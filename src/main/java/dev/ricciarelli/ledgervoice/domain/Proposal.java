package dev.ricciarelli.ledgervoice.domain;

import java.time.Instant;
import java.util.UUID;

public record Proposal(UUID id, TransactionDraft draft, CommandSource source,
                       Instant createdAt, Instant expiresAt, UUID transactionId) {
    public String statusAt(Instant now) {
        if (transactionId != null) return "CONFIRMED";
        return now.isBefore(expiresAt) ? "PENDING" : "EXPIRED";
    }
}
