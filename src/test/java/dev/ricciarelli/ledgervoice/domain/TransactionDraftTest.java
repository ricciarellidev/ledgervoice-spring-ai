package dev.ricciarelli.ledgervoice.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static dev.ricciarelli.ledgervoice.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class TransactionDraftTest {
    @Test
    void acceptsValidAmountsAndTrimsDescription() {
        var draft = new TransactionDraft(Direction.EXPENSE, new BigDecimal("1000000.00"), Category.FOOD,
                "  Almoço  ", LocalDate.of(2026, 10, 13));
        assertThatCode(() -> draft.validate(CLOCK)).doesNotThrowAnyException();
        assertThat(draft.description()).isEqualTo("Almoço");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1.001", "1000000.01"})
    void rejectsInvalidMoney(String amount) {
        var draft = new TransactionDraft(Direction.EXPENSE, new BigDecimal(amount), Category.FOOD, "Almoço", expense().occurredOn());
        assertThatThrownBy(() -> draft.validate(CLOCK)).isInstanceOf(BudgetException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "two\nlines"})
    void rejectsInvalidDescriptions(String description) {
        var draft = new TransactionDraft(Direction.EXPENSE, BigDecimal.ONE, Category.FOOD, description, expense().occurredOn());
        assertThatThrownBy(() -> draft.validate(CLOCK)).isInstanceOf(BudgetException.class);
    }

    @Test
    void rejectsMissingFieldsAndLongDescriptions() {
        assertThatThrownBy(() -> new TransactionDraft(null, null, null, null, null).validate(CLOCK)).isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> new TransactionDraft(Direction.EXPENSE, BigDecimal.ONE, Category.FOOD, null, expense().occurredOn()).validate(CLOCK))
                .isInstanceOf(BudgetException.class);
        assertThatThrownBy(() -> new TransactionDraft(Direction.EXPENSE, BigDecimal.ONE, Category.FOOD, "x".repeat(121), expense().occurredOn()).validate(CLOCK))
                .isInstanceOf(BudgetException.class);
    }

    @Test
    void rejectsFutureAndVeryOldDates() {
        for (var date : new LocalDate[]{LocalDate.of(1999, 12, 31), LocalDate.of(2026, 10, 14)}) {
            assertThatThrownBy(() -> new TransactionDraft(Direction.EXPENSE, BigDecimal.ONE, Category.FOOD, "Almoço", date).validate(CLOCK))
                    .isInstanceOf(BudgetException.class);
        }
    }
}
