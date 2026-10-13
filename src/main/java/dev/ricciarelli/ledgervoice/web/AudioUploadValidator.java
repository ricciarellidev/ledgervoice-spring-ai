package dev.ricciarelli.ledgervoice.web;

import dev.ricciarelli.ledgervoice.domain.BudgetException;
import dev.ricciarelli.ledgervoice.domain.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class AudioUploadValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public String validate(byte[] bytes, String originalFilename) {
        if (bytes.length == 0) throw invalid();
        if (bytes.length > MAX_BYTES) throw new BudgetException(ErrorCode.AUDIO_TOO_LARGE, "Audio exceeds the 5 MiB limit.");
        var name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".wav") && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE")) return "command.wav";
        if (name.endsWith(".mp3") && (ascii(bytes, 0, "ID3")
                || bytes.length > 1 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0)) return "command.mp3";
        if (name.endsWith(".m4a") && ascii(bytes, 4, "ftyp")) return "command.m4a";
        if (name.endsWith(".webm") && bytes.length > 3 && (bytes[0] & 0xff) == 0x1a
                && (bytes[1] & 0xff) == 0x45 && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3) return "command.webm";
        throw invalid();
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        return bytes.length >= offset + expected.length()
                && new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII).equals(expected);
    }

    private static BudgetException invalid() {
        return new BudgetException(ErrorCode.INVALID_AUDIO, "Provide a non-empty WAV, MP3, M4A or WebM file with a matching header.");
    }
}
