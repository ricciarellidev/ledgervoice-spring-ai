package dev.ricciarelli.ledgervoice.infrastructure.ai;

import dev.ricciarelli.ledgervoice.application.AssistantGateway;
import dev.ricciarelli.ledgervoice.application.AssistantResult;
import dev.ricciarelli.ledgervoice.application.LedgerService;
import dev.ricciarelli.ledgervoice.domain.BudgetException;
import dev.ricciarelli.ledgervoice.domain.CommandSource;
import dev.ricciarelli.ledgervoice.domain.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@Component
@Profile("live")
public class SpringAiAssistant implements AssistantGateway {
    private final ChatClient client;
    private final LedgerService ledger;
    private final Clock clock;
    private final String systemPrompt;

    public SpringAiAssistant(ChatModel model, LedgerService ledger, Clock clock,
                             @Value("classpath:prompts/assistant-system.txt") Resource prompt) throws IOException {
        this.client = ChatClient.builder(model).build();
        this.ledger = ledger;
        this.clock = clock;
        this.systemPrompt = prompt.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public AssistantResult ask(String message, CommandSource source) {
        var tools = new BudgetTools(ledger, source);
        try {
            client.prompt().system(systemPrompt.replace("{{TODAY}}", LocalDate.now(clock).toString()))
                    .messages(new UserMessage(message)).tools(tools).call().content();
            // The public response comes from actual use-case results, never from a model's claim of saving data.
            return tools.result();
        } catch (RuntimeException exception) {
            throw new BudgetException(ErrorCode.AI_UNAVAILABLE, "The AI provider could not complete this command. Try again later.");
        }
    }

    @Override
    public String mode() {
        return "LIVE";
    }
}
