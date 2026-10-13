package dev.ricciarelli.ledgervoice.application;

import dev.ricciarelli.ledgervoice.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {
    private final LedgerRepository repository;
    private final Clock clock;

    public LedgerService(LedgerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Proposal prepare(TransactionDraft draft, CommandSource source) {
        draft.validate(clock);
        var normalized = new TransactionDraft(draft.direction(), draft.amount().setScale(2), draft.category(),
                draft.description(), draft.occurredOn());
        var now = now();
        var proposal = new Proposal(UUID.randomUUID(), normalized, source, now, now.plus(Duration.ofMinutes(15)), null);
        repository.saveProposal(proposal);
        audit(proposal.id(), "PROPOSAL_CREATED");
        return proposal;
    }

    @Transactional
    public Confirmation confirm(UUID proposalId) {
        // The row lock and unique proposal_id constraint protect the commit, not an in-memory cache.
        var proposal = repository.lockProposal(proposalId).orElseThrow(LedgerService::notFound);
        if (proposal.transactionId() != null) {
            var transaction = repository.findTransaction(proposal.transactionId()).orElseThrow(LedgerService::notFound);
            audit(proposalId, "CONFIRMATION_REPLAYED");
            return new Confirmation(transaction, true);
        }
        if (!now().isBefore(proposal.expiresAt())) {
            throw new BudgetException(ErrorCode.PROPOSAL_EXPIRED, "The proposal has expired. Prepare a new one.");
        }
        var transaction = new Transaction(UUID.randomUUID(), proposalId, proposal.draft(), "BRL",
                proposal.source(), now());
        repository.saveTransaction(transaction);
        repository.attachTransaction(proposalId, transaction.id());
        audit(proposalId, "TRANSACTION_CONFIRMED");
        return new Confirmation(transaction, false);
    }

    @Transactional(readOnly = true)
    public Proposal findProposal(UUID id) {
        return repository.findProposal(id).orElseThrow(LedgerService::notFound);
    }

    @Transactional(readOnly = true)
    public Transaction findTransaction(UUID id) {
        return repository.findTransaction(id).orElseThrow(LedgerService::notFound);
    }

    @Transactional(readOnly = true)
    public TransactionPage list(String month, Category category, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new BudgetException(ErrorCode.INVALID_COMMAND, "Page must be 0-10000; size must be 1-100.");
        }
        return repository.list(parseMonth(month), category, page, size);
    }

    @Transactional(readOnly = true)
    public MonthlySummary summary(String month) {
        return repository.summary(parseMonth(month));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> auditFor(UUID proposalId) {
        findProposal(proposalId);
        return repository.audit(proposalId);
    }

    public YearMonth parseMonth(String value) {
        if (value == null) return YearMonth.now(clock);
        try {
            var month = YearMonth.parse(value);
            if (month.getYear() < 2000 || month.getYear() > 2100) throw new DateTimeParseException("Out of range", value, 0);
            return month;
        } catch (DateTimeParseException exception) {
            throw new BudgetException(ErrorCode.INVALID_MONTH, "Month must use yyyy-MM, between 2000-01 and 2100-12.");
        }
    }

    private void audit(UUID id, String action) {
        repository.appendAudit(new AuditEvent(UUID.randomUUID(), id, action, now()));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static BudgetException notFound() {
        return new BudgetException(ErrorCode.NOT_FOUND, "The requested record was not found.");
    }

    public record Confirmation(Transaction transaction, boolean replayed) { }
}
