package dev.ricciarelli.ledgervoice.domain;

public class BudgetException extends RuntimeException {
    private final ErrorCode code;

    public BudgetException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
