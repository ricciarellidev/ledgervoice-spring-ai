package dev.ricciarelli.ledgervoice.application;

import dev.ricciarelli.ledgervoice.domain.BudgetException;
import dev.ricciarelli.ledgervoice.domain.CommandSource;
import dev.ricciarelli.ledgervoice.domain.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
    private final AssistantGateway assistant;
    private final AudioGateway audio;

    public AssistantService(AssistantGateway assistant, AudioGateway audio) {
        this.assistant = assistant;
        this.audio = audio;
    }

    public Reply text(String text) {
        validateText(text, 2000);
        return new Reply(assistant.mode(), CommandSource.TEXT, null, assistant.ask(text, CommandSource.TEXT));
    }

    public Reply voice(byte[] bytes, String filename) {
        var transcript = audio.transcribe(bytes, filename);
        validateText(transcript, 2000);
        return new Reply(assistant.mode(), CommandSource.AUDIO, transcript, assistant.ask(transcript, CommandSource.AUDIO));
    }

    public byte[] speech(String text) {
        validateText(text, 1000);
        return audio.speak(text);
    }

    private static void validateText(String text, int limit) {
        if (text == null || text.isBlank() || text.length() > limit) {
            throw new BudgetException(ErrorCode.INVALID_COMMAND, "Text is empty or exceeds the supported length.");
        }
    }

    public record Reply(String mode, CommandSource source, String transcript, AssistantResult result) { }
}
