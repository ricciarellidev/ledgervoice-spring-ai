package dev.ricciarelli.ledgervoice.infrastructure.ai;

import dev.ricciarelli.ledgervoice.application.AssistantGateway;
import dev.ricciarelli.ledgervoice.application.AssistantResult;
import dev.ricciarelli.ledgervoice.application.LedgerService;
import dev.ricciarelli.ledgervoice.domain.CommandSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@Component
@Profile("!live")
public class DemoAssistant implements AssistantGateway {
    private final LedgerService ledger;
    private final Clock clock;

    public DemoAssistant(LedgerService ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    @Override
    public AssistantResult ask(String message, CommandSource source) {
        var tools = new BudgetTools(ledger, source);
        var parts = message.split(";", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].strip();
        var command = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length == 5 && (command.equals("despesa") || command.equals("receita"))) {
            var direction = command.equals("despesa") ? "EXPENSE" : "INCOME";
            var date = parts[4].equalsIgnoreCase("hoje") ? LocalDate.now(clock).toString() : parts[4];
            tools.prepareTransaction(direction, parts[1].replace(',', '.'), parts[2], parts[3], date);
        } else if (parts.length == 2 && command.equals("resumo")) {
            tools.monthlySummary(parts[1]);
        } else if (parts.length == 3 && command.equals("listar")) {
            tools.listTransactions(parts[1], parts[2]);
        }
        return tools.result();
    }

    @Override
    public String mode() {
        return "DEMO";
    }
}
