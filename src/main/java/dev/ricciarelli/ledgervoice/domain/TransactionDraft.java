package dev.ricciarelli.ledgervoice.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

public record TransactionDraft(
        Direction direction, BigDecimal amount, Category category, String description, LocalDate occurredOn
) {
    public TransactionDraft {
        description = description == null ? null : description.strip();
    }

    public void validate(Clock clock) {
        if (direction == null || category == null || amount == null || occurredOn == null) {
            reject("Direction, amount, category and date are required.");
        }
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000.00")) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            reject("Amount must be positive, at most 1000000.00, with two decimal places.");
        }
        if (description == null || description.isBlank() || description.length() > 120
                || description.codePoints().anyMatch(Character::isISOControl)) {
            reject("Description must contain 1 to 120 characters without control characters.");
        }
        if (occurredOn.isBefore(LocalDate.of(2000, 1, 1)) || occurredOn.isAfter(LocalDate.now(clock))) {
            reject("Date must be between 2000-01-01 and today.");
        }
    }

    private static void reject(String message) {
        throw new BudgetException(ErrorCode.INVALID_TRANSACTION, message);
    }
}
