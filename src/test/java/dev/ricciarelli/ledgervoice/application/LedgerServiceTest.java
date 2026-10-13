package dev.ricciarelli.ledgervoice.application;

import dev.ricciarelli.ledgervoice.domain.*;
import dev.ricciarelli.ledgervoice.infrastructure.persistence.JdbcLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;

import static dev.ricciarelli.ledgervoice.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ledger-service;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@Import(LedgerServiceTest.Configuration.class)
class LedgerServiceTest {
    @Autowired LedgerService ledger;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired FailingAuditRepository repository;

    @BeforeEach
    void reset() {
        clock.instant = NOW;
        repository.setFailAudit(false);
        jdbc.update("UPDATE proposals SET transaction_id = NULL");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM proposals");
    }

    @Test
    void recordsOnlyAfterConfirmationAndReplaysTheSameReceipt() {
        var proposal = ledger.prepare(expense(), CommandSource.MANUAL);
        assertThat(ledger.summary("2026-10").transactionCount()).isZero();
        var first = ledger.confirm(proposal.id());
        var second = ledger.confirm(proposal.id());
        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transaction()).isEqualTo(first.transaction());
        assertThat(ledger.findProposal(proposal.id()).statusAt(NOW)).isEqualTo("CONFIRMED");
        assertThat(ledger.auditFor(proposal.id())).extracting(AuditEvent::action)
                .containsExactlyInAnyOrder("PROPOSAL_CREATED", "TRANSACTION_CONFIRMED", "CONFIRMATION_REPLAYED");
        assertThat(ledger.findTransaction(first.transaction().id())).isEqualTo(first.transaction());
    }

    @Test
    void expiresAtTheBoundaryButKeepsConfirmedReceiptsReplayable() {
        var expired = ledger.prepare(expense(), CommandSource.TEXT);
        var confirmed = ledger.prepare(expense(), CommandSource.TEXT);
        ledger.confirm(confirmed.id());
        clock.instant = NOW.plus(Duration.ofMinutes(15));
        assertThat(ledger.findProposal(expired.id()).statusAt(clock.instant)).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> ledger.confirm(expired.id())).isInstanceOf(BudgetException.class);
        assertThat(ledger.confirm(confirmed.id()).replayed()).isTrue();
        assertThat(ledger.summary("2026-10").transactionCount()).isEqualTo(1);
    }

    @Test
    void rollsBackTheTransactionAndProposalIfAuditFails() {
        var proposal = ledger.prepare(expense(), CommandSource.MANUAL);
        repository.setFailAudit(true);
        assertThatThrownBy(() -> ledger.confirm(proposal.id())).isInstanceOf(IllegalStateException.class);
        repository.setFailAudit(false);
        assertThat(ledger.findProposal(proposal.id()).transactionId()).isNull();
        assertThat(ledger.summary("2026-10").transactionCount()).isZero();
        assertThat(ledger.auditFor(proposal.id())).hasSize(1);
        assertThat(ledger.confirm(proposal.id()).replayed()).isFalse();
    }

    @Test
    void calculatesExactTotalsAndFiltersOnlyConfirmedRows() {
        var income = new TransactionDraft(Direction.INCOME, new BigDecimal("100.10"), Category.SALARY, "Salário", expense().occurredOn());
        ledger.confirm(ledger.prepare(income, CommandSource.MANUAL).id());
        ledger.confirm(ledger.prepare(expense(), CommandSource.MANUAL).id());
        ledger.prepare(expense(), CommandSource.MANUAL);
        var summary = ledger.summary("2026-10");
        assertThat(summary.income()).isEqualByComparingTo("100.10");
        assertThat(summary.expenses()).isEqualByComparingTo("42.90");
        assertThat(summary.net()).isEqualByComparingTo("57.20");
        assertThat(ledger.list("2026-10", Category.FOOD, 0, 20).items()).hasSize(1);
        assertThat(ledger.list("2026-10", null, 0, 1).total()).isEqualTo(2);
        assertThat(ledger.list("2026-10", null, 1, 1).items()).hasSize(1);
        assertThat(ledger.summary("2026-09").transactionCount()).isZero();
        assertThat(ledger.summary(null).month()).isEqualTo("2026-10");
    }

    @Test
    void rejectsInvalidPeriodsPaginationAndMissingRecords() {
        for (var value : new String[]{"2026-13", "1999-12", "2101-01", "2026-10' OR 1=1"}) {
            assertThatThrownBy(() -> ledger.summary(value)).isInstanceOf(BudgetException.class);
        }
        assertThatThrownBy(() -> ledger.list("2026-10", null, -1, 20)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> ledger.list("2026-10", null, 10001, 20)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> ledger.list("2026-10", null, 0, 0)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> ledger.list("2026-10", null, 0, 101)).isInstanceOf(BudgetException.class);
        var id = java.util.UUID.randomUUID();
        assertThatThrownBy(() -> ledger.findProposal(id)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> ledger.findTransaction(id)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> ledger.confirm(id)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> ledger.auditFor(id)).isInstanceOf(BudgetException.class);
    }

    @Test
    void concurrentConfirmationsCreateOneTransaction() throws Exception {
        var proposal = ledger.prepare(expense(), CommandSource.AUDIO);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        var futures = new ArrayList<Future<LedgerService.Confirmation>>();
        try {
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timeout");
                return ledger.confirm(proposal.id());
            }));
            start.countDown();
            int created = 0;
            java.util.UUID transactionId = null;
            for (var future : futures) {
                var result = future.get(15, TimeUnit.SECONDS);
                if (!result.replayed()) created++;
                if (transactionId == null) transactionId = result.transaction().id();
                assertThat(result.transaction().id()).isEqualTo(transactionId);
            }
            assertThat(created).isEqualTo(1);
            assertThat(ledger.summary("2026-10").transactionCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void toolSessionsValidateArgumentsAndAllowOnlyOneOperation() {
        var invalid = new dev.ricciarelli.ledgervoice.infrastructure.ai.BudgetTools(ledger, CommandSource.TEXT);
        assertThat(invalid.prepareTransaction(null, null, null, null, null).ok()).isFalse();
        assertThat(invalid.result().proposals()).isEmpty();

        var valid = new dev.ricciarelli.ledgervoice.infrastructure.ai.BudgetTools(ledger, CommandSource.TEXT);
        assertThat(valid.prepareTransaction("EXPENSE", "42.90", "FOOD", "Almoço", "2026-10-13").ok()).isTrue();
        assertThat(valid.monthlySummary("2026-10").code()).isEqualTo("TOOL_LIMIT_REACHED");
        assertThat(valid.result().proposals()).hasSize(1);
        assertThat(valid.result().summary()).isNull();
        assertThat(ledger.summary("2026-10").transactionCount()).isZero();
    }

    @TestConfiguration
    static class Configuration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary FailingAuditRepository failingRepository(JdbcTemplate jdbc) { return new FailingAuditRepository(jdbc); }
    }

    static class MutableClock extends Clock {
        volatile Instant instant = NOW;
        @Override public ZoneId getZone() { return ZoneId.of("America/Sao_Paulo"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    static class FailingAuditRepository extends JdbcLedgerRepository {
        private volatile boolean failAudit;
        FailingAuditRepository(JdbcTemplate jdbc) { super(jdbc); }
        public void setFailAudit(boolean value) { failAudit = value; }
        @Override public void appendAudit(AuditEvent event) {
            if (failAudit && event.action().equals("TRANSACTION_CONFIRMED")) throw new IllegalStateException("Injected audit failure");
            super.appendAudit(event);
        }
    }
}
