package dev.ricciarelli.ledgervoice.infrastructure.ai;

import dev.ricciarelli.ledgervoice.application.AudioGateway;
import dev.ricciarelli.ledgervoice.domain.BudgetException;
import dev.ricciarelli.ledgervoice.domain.ErrorCode;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
@Profile("live")
public class SpringAiAudio implements AudioGateway {
    private final OpenAiAudioTranscriptionModel transcription;
    private final OpenAiAudioSpeechModel speech;

    public SpringAiAudio(OpenAiAudioTranscriptionModel transcription, OpenAiAudioSpeechModel speech) {
        this.transcription = transcription;
        this.speech = speech;
    }

    @Override
    public String transcribe(byte[] audio, String filename) {
        try {
            var resource = new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            return transcription.call(new AudioTranscriptionPrompt(resource)).getResult().getOutput();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public byte[] speak(String text) {
        try {
            return speech.call(text);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static BudgetException unavailable() {
        return new BudgetException(ErrorCode.AI_UNAVAILABLE, "The audio provider could not complete this request. Try again later.");
    }
}
