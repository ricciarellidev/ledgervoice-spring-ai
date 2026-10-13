package dev.ricciarelli.ledgervoice.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(UUID id, UUID proposalId, String action, Instant occurredAt) { }
