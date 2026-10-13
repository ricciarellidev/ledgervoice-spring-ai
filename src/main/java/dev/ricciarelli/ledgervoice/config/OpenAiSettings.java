package dev.ricciarelli.ledgervoice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ledger.openai")
public record OpenAiSettings(@NotBlank String apiKey, @NotBlank String baseUrl,
                             @NotBlank String chatModel, @NotBlank String transcriptionModel, @NotBlank String speechModel) {
    @Override
    public String toString() {
        return "OpenAiSettings[apiKey=REDACTED, chatModel=" + chatModel + "]";
    }
}
