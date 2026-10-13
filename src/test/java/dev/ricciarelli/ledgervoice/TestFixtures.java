package dev.ricciarelli.ledgervoice;

import dev.ricciarelli.ledgervoice.domain.*;

import java.math.BigDecimal;
import java.time.*;

public final class TestFixtures {
    public static final Instant NOW = Instant.parse("2026-10-13T15:00:00.123456Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));

    private TestFixtures() { }

    public static TransactionDraft expense() {
        return new TransactionDraft(Direction.EXPENSE, new BigDecimal("42.90"), Category.FOOD, "Almoço", LocalDate.of(2026, 10, 13));
    }

    public static byte[] wavHeader() {
        return new byte[]{'R','I','F','F',36,0,0,0,'W','A','V','E','f','m','t',' ',16,0,0,0};
    }
}
