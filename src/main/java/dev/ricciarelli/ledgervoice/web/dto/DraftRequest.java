package dev.ricciarelli.ledgervoice.web.dto;

import dev.ricciarelli.ledgervoice.domain.Category;
import dev.ricciarelli.ledgervoice.domain.Direction;
import dev.ricciarelli.ledgervoice.domain.TransactionDraft;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DraftRequest(@NotNull Direction direction,
                           @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal amount,
                           @NotNull Category category, @NotBlank @Size(max = 120) String description,
                           @NotNull LocalDate occurredOn) {
    public TransactionDraft toDraft() {
        return new TransactionDraft(direction, amount, category, description, occurredOn);
    }
}
