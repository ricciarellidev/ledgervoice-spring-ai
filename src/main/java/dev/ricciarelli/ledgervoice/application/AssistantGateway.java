package dev.ricciarelli.ledgervoice.application;

import dev.ricciarelli.ledgervoice.domain.CommandSource;

public interface AssistantGateway {
    AssistantResult ask(String message, CommandSource source);
    String mode();
}
