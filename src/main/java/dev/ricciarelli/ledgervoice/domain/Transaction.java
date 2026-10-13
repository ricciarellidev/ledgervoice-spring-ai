package dev.ricciarelli.ledgervoice.domain;

import java.time.Instant;
import java.util.UUID;

public record Transaction(UUID id, UUID proposalId, TransactionDraft details,
                          String currency, CommandSource source, Instant createdAt) { }
