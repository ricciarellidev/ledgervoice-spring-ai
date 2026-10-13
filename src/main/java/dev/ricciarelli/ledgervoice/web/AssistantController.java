package dev.ricciarelli.ledgervoice.web;

import dev.ricciarelli.ledgervoice.application.AssistantService;
import dev.ricciarelli.ledgervoice.web.dto.AssistantResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantService assistant;
    private final AudioUploadValidator validator;
    private final Clock clock;

    public AssistantController(AssistantService assistant, AudioUploadValidator validator, Clock clock) {
        this.assistant = assistant;
        this.validator = validator;
        this.clock = clock;
    }

    @PostMapping("/commands")
    public AssistantResponse text(@Valid @RequestBody TextRequest request) {
        return AssistantResponse.from(assistant.text(request.text()), Instant.now(clock));
    }

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssistantResponse voice(@RequestPart("file") MultipartFile file) throws IOException {
        var bytes = file.getBytes();
        var filename = validator.validate(bytes, file.getOriginalFilename());
        return AssistantResponse.from(assistant.voice(bytes, filename), Instant.now(clock));
    }

    @PostMapping(value = "/speech", produces = "audio/mpeg")
    public ResponseEntity<byte[]> speech(@Valid @RequestBody SpeechRequest request) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore()).header("X-Audio-Origin", "AI-generated")
                .body(assistant.speech(request.text()));
    }

    public record TextRequest(@NotBlank @Size(max = 2000) String text) { }
    public record SpeechRequest(@NotBlank @Size(max = 1000) String text) { }
}
