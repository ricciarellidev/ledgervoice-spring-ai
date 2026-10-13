package dev.ricciarelli.ledgervoice.web.dto;

import dev.ricciarelli.ledgervoice.domain.CommandSource;
import dev.ricciarelli.ledgervoice.domain.Proposal;
import dev.ricciarelli.ledgervoice.domain.TransactionDraft;

import java.time.Instant;
import java.util.UUID;

public record ProposalResponse(UUID id, TransactionDraft draft, String currency, CommandSource source,
                               String status, Instant createdAt, Instant expiresAt, UUID transactionId) {
    public static ProposalResponse from(Proposal proposal, Instant now) {
        return new ProposalResponse(proposal.id(), proposal.draft(), "BRL", proposal.source(), proposal.statusAt(now),
                proposal.createdAt(), proposal.expiresAt(), proposal.transactionId());
    }
}
