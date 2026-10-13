package dev.ricciarelli.ledgervoice.web;

import dev.ricciarelli.ledgervoice.domain.BudgetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static dev.ricciarelli.ledgervoice.TestFixtures.wavHeader;
import static org.assertj.core.api.Assertions.*;

class AudioUploadValidatorTest {
    private final AudioUploadValidator validator = new AudioUploadValidator();

    @Test
    void acceptsAllowlistedHeadersAndUsesServerControlledFilenames() {
        assertThat(validator.validate(wavHeader(), "../../secret.WAV")).isEqualTo("command.wav");
        assertThat(validator.validate("ID3fixture".getBytes(StandardCharsets.US_ASCII), "audio.mp3")).isEqualTo("command.mp3");
        assertThat(validator.validate(new byte[]{(byte) 0xff, (byte) 0xfb, 0}, "audio.mp3")).isEqualTo("command.mp3");
        assertThat(validator.validate(new byte[]{0,0,0,24,'f','t','y','p'}, "audio.m4a")).isEqualTo("command.m4a");
        assertThat(validator.validate(new byte[]{0x1a,0x45,(byte) 0xdf,(byte) 0xa3}, "audio.webm")).isEqualTo("command.webm");
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsSpoofedEmptyAndUnsupportedFiles(Input input) {
        assertThatThrownBy(() -> validator.validate(input.bytes(), input.filename())).isInstanceOf(BudgetException.class);
    }

    static Stream<Input> invalidInputs() {
        return Stream.of(new Input(new byte[0], "audio.wav"), new Input(wavHeader(), "audio.exe"),
                new Input("not audio".getBytes(StandardCharsets.UTF_8), "audio.wav"), new Input(wavHeader(), null),
                new Input(new byte[]{0x1a}, "audio.webm"), new Input(new byte[]{(byte) 0xff}, "audio.mp3"));
    }

    @Test
    void rejectsOversizedInputBeforeProviderInvocation() {
        assertThatThrownBy(() -> validator.validate(new byte[AudioUploadValidator.MAX_BYTES + 1], "audio.wav"))
                .isInstanceOf(BudgetException.class).hasMessageContaining("5 MiB");
    }

    record Input(byte[] bytes, String filename) { }
}
