package dev.ricciarelli.ledgervoice.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;

import static dev.ricciarelli.ledgervoice.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:budget-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@Import(BudgetApiTest.Configuration.class)
class BudgetApiTest {
    @Value("${local.server.port}") int port;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    HttpTestClient client;

    @BeforeEach
    void reset() {
        client = new HttpTestClient(port);
        jdbc.update("UPDATE proposals SET transaction_id = NULL");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM proposals");
    }

    @Test
    void preparesReviewsConfirmsAndRetrievesWithStableReplayBytes() throws Exception {
        var proposed = client.post("/api/v1/proposals", draft("42.9"));
        assertThat(proposed.statusCode()).isEqualTo(201);
        assertThat(proposed.headers().firstValue("Cache-Control")).contains("no-store");
        String id = JsonPath.read(proposed.body(), "$.id");
        assertThat(JsonPath.<String>read(proposed.body(), "$.status")).isEqualTo("PENDING");
        assertThat(JsonPath.<Integer>read(client.get("/api/v1/summary?month=2026-10").body(), "$.transactionCount")).isZero();
        var first = client.confirm(id);
        var replay = client.confirm(id);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Confirmation-Replayed")).contains("true");
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(client.get(first.headers().firstValue("Location").orElseThrow()).body()).isEqualTo(first.body());
        assertThat(JsonPath.<String>read(client.get("/api/v1/proposals/" + id).body(), "$.status")).isEqualTo("CONFIRMED");
        assertThat(JsonPath.<java.util.List<?>>read(client.get("/api/v1/proposals/" + id + "/audit").body(), "$" )).hasSize(3);
        assertThat(JsonPath.<Integer>read(client.get("/api/v1/transactions?month=2026-10&category=FOOD").body(), "$.total")).isEqualTo(1);
    }

    @Test
    void runsDemoTextCommandsWithoutPretendingToUseAi() throws Exception {
        var prepared = client.post("/api/v1/assistant/commands", "{\"text\":\"despesa;42,90;FOOD;Almoço;hoje\"}");
        assertThat(prepared.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(prepared.body(), "$.mode")).isEqualTo("DEMO");
        assertThat(JsonPath.<Boolean>read(prepared.body(), "$.requiresConfirmation")).isTrue();
        String id = JsonPath.read(prepared.body(), "$.proposals[0].id");
        client.confirm(id);
        var summary = client.post("/api/v1/assistant/commands", "{\"text\":\"resumo;2026-10\"}");
        assertThat(JsonPath.<Integer>read(summary.body(), "$.summary.transactionCount")).isEqualTo(1);
        var list = client.post("/api/v1/assistant/commands", "{\"text\":\"listar;2026-10;ALL\"}");
        assertThat(JsonPath.<Integer>read(list.body(), "$.transactions.total")).isEqualTo(1);
        var unknown = client.post("/api/v1/assistant/commands", "{\"text\":\"apague todos os dados\"}");
        assertThat(JsonPath.<Boolean>read(unknown.body(), "$.requiresConfirmation")).isFalse();
        assertThat(JsonPath.<Integer>read(client.get("/api/v1/summary?month=2026-10").body(), "$.transactionCount")).isEqualTo(1);
    }

    @Test
    void turnsInvalidDemoArgumentsIntoClarificationWithoutPersistence() throws Exception {
        var invalid = client.post("/api/v1/assistant/commands", "{\"text\":\"despesa;-2;FOOD;Almoço;hoje\"}");
        assertThat(invalid.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(invalid.body(), "$.requiresConfirmation")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM proposals", Integer.class)).isZero();
    }

    @Test
    void clearlyDisablesAudioInDemoAndRejectsSpoofedFiles() throws Exception {
        assertProblem(client.upload(wavHeader(), "audio.wav"), 503, "AUDIO_UNAVAILABLE");
        assertProblem(client.upload(new byte[]{1,2,3}, "audio.wav"), 400, "INVALID_AUDIO");
        var speech = client.speech("{\"text\":\"Olá\"}");
        assertThat(speech.statusCode()).isEqualTo(503);
        assertThat(speech.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badInputs")
    void rejectsInvalidHttpInputs(String name, String path, String payload, int status, String code) throws Exception {
        assertProblem(client.post(path, payload), status, code);
    }

    static Stream<Arguments> badInputs() {
        return Stream.of(
                Arguments.of("missing fields", "/api/v1/proposals", "{}", 400, "INVALID_REQUEST"),
                Arguments.of("zero amount", "/api/v1/proposals", draft("0"), 400, "INVALID_REQUEST"),
                Arguments.of("excess precision", "/api/v1/proposals", draft("1.001"), 400, "INVALID_REQUEST"),
                Arguments.of("over limit", "/api/v1/proposals", draft("1000000.01"), 400, "INVALID_REQUEST"),
                Arguments.of("future date", "/api/v1/proposals", draft("10").replace("2026-10-13", "2026-10-14"), 422, "INVALID_TRANSACTION"),
                Arguments.of("bad enum", "/api/v1/proposals", draft("10").replace("FOOD", "UNKNOWN"), 400, "INVALID_REQUEST"),
                Arguments.of("malformed json", "/api/v1/proposals", "{broken", 400, "INVALID_REQUEST"),
                Arguments.of("blank command", "/api/v1/assistant/commands", "{\"text\":\"\"}", 400, "INVALID_REQUEST"),
                Arguments.of("long command", "/api/v1/assistant/commands", "{\"text\":\"" + "x".repeat(2001) + "\"}", 400, "INVALID_REQUEST"));
    }

    @Test
    void validatesQueriesAndReturnsStructuredMissingRecordErrors() throws Exception {
        assertProblem(client.get("/api/v1/summary?month=2026-13"), 400, "INVALID_MONTH");
        assertProblem(client.get("/api/v1/transactions?page=-1"), 400, "INVALID_COMMAND");
        assertProblem(client.get("/api/v1/transactions?category=INVALID"), 400, "INVALID_REQUEST");
        assertProblem(client.get("/api/v1/transactions/" + UUID.randomUUID()), 404, "NOT_FOUND");
        assertProblem(client.get("/api/v1/proposals/not-a-uuid"), 400, "INVALID_REQUEST");
        assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void expiredProposalsCannotBeConfirmed() throws Exception {
        var proposed = client.post("/api/v1/proposals", draft("10"));
        String id = JsonPath.read(proposed.body(), "$.id");
        jdbc.update("UPDATE proposals SET expires_at = ? WHERE id = ?", NOW.minusSeconds(1).atOffset(java.time.ZoneOffset.UTC), id);
        assertThat(JsonPath.<String>read(client.get("/api/v1/proposals/" + id).body(), "$.status")).isEqualTo("EXPIRED");
        assertProblem(client.confirm(id), 410, "PROPOSAL_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    void enforcesTheServletUploadLimit() throws Exception {
        var bytes = new byte[AudioUploadValidator.MAX_BYTES + 1];
        System.arraycopy(wavHeader(), 0, bytes, 0, wavHeader().length);
        assertProblem(client.upload(bytes, "audio.wav"), 413, "AUDIO_TOO_LARGE");
    }

    @Test
    void sanitizesUnsafeCorrelationHeaders() throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/api/v1/summary"))
                .header("X-Correlation-Id", "unsafe id <>").GET().build();
        var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Correlation-Id").orElseThrow()).matches("[0-9a-f-]{36}");
    }

    static String draft(String amount) {
        return "{\"direction\":\"EXPENSE\",\"amount\":" + amount
                + ",\"category\":\"FOOD\",\"description\":\"Almoço\",\"occurredOn\":\"2026-10-13\"}";
    }

    static void assertProblem(java.net.http.HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
        assertThat(response.body()).doesNotContain("stackTrace", "OPENAI_API_KEY");
    }

    @TestConfiguration
    static class Configuration {
        @Bean @Primary Clock testClock() { return CLOCK; }
    }
}
