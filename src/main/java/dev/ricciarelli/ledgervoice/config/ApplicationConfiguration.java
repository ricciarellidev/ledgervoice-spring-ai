package dev.ricciarelli.ledgervoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ApplicationConfiguration {
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
