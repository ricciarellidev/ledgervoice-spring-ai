package dev.ricciarelli.ledgervoice.config;

import org.springframework.ai.openai.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
@Profile("live")
@EnableConfigurationProperties(OpenAiSettings.class)
public class LiveAiConfiguration {
    @Bean
    OpenAiChatModel chatModel(OpenAiSettings settings) {
        return OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                .apiKey(settings.apiKey()).baseUrl(settings.baseUrl()).model(settings.chatModel())
                .temperature(0.1).maxCompletionTokens(500).parallelToolCalls(false)
                .timeout(Duration.ofSeconds(30)).maxRetries(0).build()).build();
    }

    @Bean
    OpenAiAudioTranscriptionModel transcriptionModel(OpenAiSettings settings) {
        return OpenAiAudioTranscriptionModel.builder().options(OpenAiAudioTranscriptionOptions.builder()
                .apiKey(settings.apiKey()).baseUrl(settings.baseUrl()).model(settings.transcriptionModel())
                .language("pt").timeout(Duration.ofSeconds(30)).maxRetries(0).build()).build();
    }

    @Bean
    OpenAiAudioSpeechModel speechModel(OpenAiSettings settings) {
        return OpenAiAudioSpeechModel.builder().options(OpenAiAudioSpeechOptions.builder()
                .apiKey(settings.apiKey()).baseUrl(settings.baseUrl()).model(settings.speechModel())
                .voice("alloy").responseFormat("mp3").timeout(Duration.ofSeconds(30)).maxRetries(0).build()).build();
    }
}
