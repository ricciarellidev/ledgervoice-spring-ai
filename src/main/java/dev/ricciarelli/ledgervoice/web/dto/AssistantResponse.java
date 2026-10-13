package dev.ricciarelli.ledgervoice.web.dto;

import dev.ricciarelli.ledgervoice.application.AssistantService;
import dev.ricciarelli.ledgervoice.domain.CommandSource;
import dev.ricciarelli.ledgervoice.domain.MonthlySummary;
import dev.ricciarelli.ledgervoice.domain.TransactionPage;

import java.time.Instant;
import java.util.List;

public record AssistantResponse(String mode, CommandSource source, String transcript, String answer,
                                boolean requiresConfirmation, List<ProposalResponse> proposals,
                                MonthlySummary summary, TransactionPage transactions) {
    public static AssistantResponse from(AssistantService.Reply reply, Instant now) {
        var result = reply.result();
        return new AssistantResponse(reply.mode(), reply.source(), reply.transcript(), result.answer(),
                !result.proposals().isEmpty(), result.proposals().stream().map(p -> ProposalResponse.from(p, now)).toList(),
                result.summary(), result.transactions());
    }
}
