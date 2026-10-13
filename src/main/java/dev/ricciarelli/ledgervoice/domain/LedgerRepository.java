package dev.ricciarelli.ledgervoice.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository {
    void saveProposal(Proposal proposal);
    Optional<Proposal> findProposal(UUID id);
    Optional<Proposal> lockProposal(UUID id);
    void attachTransaction(UUID proposalId, UUID transactionId);
    void saveTransaction(Transaction transaction);
    Optional<Transaction> findTransaction(UUID id);
    TransactionPage list(YearMonth month, Category category, int page, int size);
    MonthlySummary summary(YearMonth month);
    void appendAudit(AuditEvent event);
    List<AuditEvent> audit(UUID proposalId);
}
