package dev.ricciarelli.ledgervoice.infrastructure.persistence;

import dev.ricciarelli.ledgervoice.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {
    private final JdbcTemplate jdbc;

    public JdbcLedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveProposal(Proposal proposal) {
        var draft = proposal.draft();
        jdbc.update("""
                INSERT INTO proposals (id, direction, amount, category, description, occurred_on, source, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, proposal.id().toString(), draft.direction().name(), draft.amount(), draft.category().name(),
                draft.description(), draft.occurredOn(), proposal.source().name(),
                proposal.createdAt().atOffset(ZoneOffset.UTC), proposal.expiresAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<Proposal> findProposal(UUID id) {
        return jdbc.query("SELECT * FROM proposals WHERE id = ?", JdbcLedgerRepository::proposal, id.toString())
                .stream().findFirst();
    }

    @Override
    public Optional<Proposal> lockProposal(UUID id) {
        return jdbc.query("SELECT * FROM proposals WHERE id = ? FOR UPDATE", JdbcLedgerRepository::proposal, id.toString())
                .stream().findFirst();
    }

    @Override
    public void attachTransaction(UUID proposalId, UUID transactionId) {
        jdbc.update("UPDATE proposals SET transaction_id = ? WHERE id = ?", transactionId.toString(), proposalId.toString());
    }

    @Override
    public void saveTransaction(Transaction transaction) {
        var draft = transaction.details();
        jdbc.update("""
                INSERT INTO transactions (id, proposal_id, direction, amount, category, description, occurred_on, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, transaction.id().toString(), transaction.proposalId().toString(), draft.direction().name(), draft.amount(),
                draft.category().name(), draft.description(), draft.occurredOn(), transaction.source().name(),
                transaction.createdAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<Transaction> findTransaction(UUID id) {
        return jdbc.query("SELECT * FROM transactions WHERE id = ?", JdbcLedgerRepository::transaction, id.toString())
                .stream().findFirst();
    }

    @Override
    public TransactionPage list(YearMonth month, Category category, int page, int size) {
        var predicate = " WHERE occurred_on >= ? AND occurred_on < ?" + (category == null ? "" : " AND category = ?");
        var args = new ArrayList<Object>(List.of(month.atDay(1), month.plusMonths(1).atDay(1)));
        if (category != null) args.add(category.name());
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM transactions" + predicate, Long.class, args.toArray());
        args.add(size);
        args.add(page * size);
        var items = jdbc.query("SELECT * FROM transactions" + predicate
                + " ORDER BY occurred_on DESC, created_at DESC, id DESC LIMIT ? OFFSET ?",
                JdbcLedgerRepository::transaction, args.toArray());
        return new TransactionPage(items, total == null ? 0 : total, page, size);
    }

    @Override
    public MonthlySummary summary(YearMonth month) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN direction = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
                       COALESCE(SUM(CASE WHEN direction = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses,
                       COUNT(*) AS total FROM transactions WHERE occurred_on >= ? AND occurred_on < ?
                """, (rs, row) -> {
                    var income = rs.getBigDecimal("income");
                    var expenses = rs.getBigDecimal("expenses");
                    return new MonthlySummary(month.toString(), "BRL", income, expenses, income.subtract(expenses), rs.getLong("total"));
                }, month.atDay(1), month.plusMonths(1).atDay(1));
    }

    @Override
    public void appendAudit(AuditEvent event) {
        jdbc.update("INSERT INTO audit_events (id, proposal_id, action, occurred_at) VALUES (?, ?, ?, ?)",
                event.id().toString(), event.proposalId().toString(), event.action(), event.occurredAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public List<AuditEvent> audit(UUID proposalId) {
        return jdbc.query("SELECT * FROM audit_events WHERE proposal_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 100",
                (rs, row) -> new AuditEvent(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("proposal_id")),
                        rs.getString("action"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant()), proposalId.toString());
    }

    private static TransactionDraft draft(ResultSet rs) throws SQLException {
        return new TransactionDraft(Direction.valueOf(rs.getString("direction")), rs.getBigDecimal("amount"),
                Category.valueOf(rs.getString("category")), rs.getString("description"), rs.getDate("occurred_on").toLocalDate());
    }

    private static Proposal proposal(ResultSet rs, int row) throws SQLException {
        var transactionId = rs.getString("transaction_id");
        return new Proposal(UUID.fromString(rs.getString("id")), draft(rs), CommandSource.valueOf(rs.getString("source")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                transactionId == null ? null : UUID.fromString(transactionId));
    }

    private static Transaction transaction(ResultSet rs, int row) throws SQLException {
        return new Transaction(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("proposal_id")), draft(rs), "BRL",
                CommandSource.valueOf(rs.getString("source")), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
