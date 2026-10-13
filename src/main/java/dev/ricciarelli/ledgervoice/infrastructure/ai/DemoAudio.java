package dev.ricciarelli.ledgervoice.infrastructure.ai;

import dev.ricciarelli.ledgervoice.application.AudioGateway;
import dev.ricciarelli.ledgervoice.domain.BudgetException;
import dev.ricciarelli.ledgervoice.domain.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!live")
public class DemoAudio implements AudioGateway {
    @Override
    public String transcribe(byte[] audio, String filename) {
        throw unavailable();
    }

    @Override
    public byte[] speak(String text) {
        throw unavailable();
    }

    private static BudgetException unavailable() {
        return new BudgetException(ErrorCode.AUDIO_UNAVAILABLE,
                "Audio requires the live profile and a server-side OpenAI API key. Demo supports text only.");
    }
}
