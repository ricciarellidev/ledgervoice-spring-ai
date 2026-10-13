package dev.ricciarelli.ledgervoice.application;

import dev.ricciarelli.ledgervoice.domain.MonthlySummary;
import dev.ricciarelli.ledgervoice.domain.Proposal;
import dev.ricciarelli.ledgervoice.domain.TransactionPage;

import java.util.List;

public record AssistantResult(String answer, List<Proposal> proposals,
                              MonthlySummary summary, TransactionPage transactions) {
    public AssistantResult {
        proposals = List.copyOf(proposals);
    }
}
