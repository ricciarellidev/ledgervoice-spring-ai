package dev.ricciarelli.ledgervoice.infrastructure.ai;

import com.jayway.jsonpath.JsonPath;
import dev.ricciarelli.ledgervoice.web.HttpTestClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static dev.ricciarelli.ledgervoice.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=live", "ledger.openai.api-key=local-fixture-not-a-real-key",
        "spring.datasource.url=jdbc:h2:mem:ai-contract;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"})
@Import(SpringAiContractTest.Configuration.class)
class SpringAiContractTest {
    static final LocalOpenAiServer provider = startProvider();
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    HttpTestClient client;

    private static LocalOpenAiServer startProvider() {
        try { return new LocalOpenAiServer(); }
        catch (IOException exception) { throw new IllegalStateException("Cannot start local provider fixture", exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("ledger.openai.base-url", provider::baseUrl);
    }

    @BeforeEach
    void reset() {
        client = new HttpTestClient(port);
        provider.reset();
        jdbc.update("UPDATE proposals SET transaction_id = NULL");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM proposals");
    }

    @AfterAll
    static void stopProvider() { provider.close(); }

    @Test
    void springAiExecutesTheActualToolButCannotCommitTheLedger() throws Exception {
        var response = client.post("/api/v1/assistant/commands", "{\"text\":\"Registre meu almoço de 42,90 reais em alimentação hoje.\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.mode")).isEqualTo("LIVE");
        assertThat(JsonPath.<Boolean>read(response.body(), "$.requiresConfirmation")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM proposals", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
        assertThat(provider.chatRequests).hasSize(1);
        String outbound = provider.chatRequests.get(0);
        assertThat(outbound).contains("prepareTransaction", "monthlySummary", "listTransactions", "2026-10-13")
                .doesNotContain("confirmTransaction", "deleteTransaction");
        String proposalId = JsonPath.read(response.body(), "$.proposals[0].id");
        assertThat(client.confirm(proposalId).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isEqualTo(1);
    }

    @Test
    void audioFlowsThroughTranscriptionAndToolCallingWithASafeFilename() throws Exception {
        var response = client.upload(wavHeader(), "../../private.wav");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.source")).isEqualTo("AUDIO");
        assertThat(JsonPath.<String>read(response.body(), "$.transcript")).contains("42,90");
        assertThat(JsonPath.<String>read(response.body(), "$.proposals[0].source")).isEqualTo("AUDIO");
        assertThat(provider.transcriptionRequests).hasSize(1);
        assertThat(provider.transcriptionRequests.get(0)).contains("command.wav").doesNotContain("private.wav");
        assertThat(provider.chatRequests).hasSize(1);
    }

    @Test
    void speechReturnsProviderBytesAndDisclosesTheirAiOrigin() throws Exception {
        var response = client.speech("{\"text\":\"Proposta pronta para revisão.\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("audio/mpeg");
        assertThat(response.headers().firstValue("X-Audio-Origin")).contains("AI-generated");
        assertThat(response.body()).isEqualTo("ID3synthetic-wire-fixture".getBytes(StandardCharsets.UTF_8));
        assertThat(provider.speechRequests).hasSize(1);
    }

    @Test
    void queryToolsReturnDatabaseTotalsAndConfirmedRows() throws Exception {
        var create = client.post("/api/v1/assistant/commands", "{\"text\":\"Registre meu almoço de 42,90 reais hoje.\"}");
        String id = JsonPath.read(create.body(), "$.proposals[0].id");
        client.confirm(id);
        provider.reset();
        var summary = client.post("/api/v1/assistant/commands", "{\"text\":\"QUERY_SUMMARY\"}");
        assertThat(summary.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(summary.body(), "$.summary.expenses").doubleValue()).isEqualTo(42.90);
        var list = client.post("/api/v1/assistant/commands", "{\"text\":\"QUERY_LIST\"}");
        assertThat(JsonPath.<Integer>read(list.body(), "$.transactions.total")).isEqualTo(1);
        assertThat(provider.chatRequests).hasSize(2);
    }

    @Test
    void invalidToolArgumentsCannotCreateAProposal() throws Exception {
        var response = client.post("/api/v1/assistant/commands", "{\"text\":\"INVALID_AMOUNT\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(response.body(), "$.requiresConfirmation")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM proposals", Integer.class)).isZero();
    }

    @Test
    void doesNotTrustAModelClaimThatNoToolActuallyPerformed() throws Exception {
        var response = client.post("/api/v1/assistant/commands", "{\"text\":\"NO_TOOL\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("saved your expense");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM proposals", Integer.class)).isZero();
    }

    @Test
    void rejectsUnknownToolsAndSanitizesProviderFailuresWithoutRetries() throws Exception {
        var attack = client.post("/api/v1/assistant/commands", "{\"text\":\"ATTACK_CONFIRM\"}");
        assertThat(attack.statusCode()).isEqualTo(502);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
        provider.reset();
        var failure = client.post("/api/v1/assistant/commands", "{\"text\":\"FAIL_PROVIDER\"}");
        assertThat(failure.statusCode()).isEqualTo(502);
        assertThat(JsonPath.<String>read(failure.body(), "$.code")).isEqualTo("AI_UNAVAILABLE");
        assertThat(failure.body()).doesNotContain("fixture unavailable", "local-fixture-not-a-real-key");
        assertThat(provider.chatRequests).hasSize(1);
        assertThat(client.speech("{\"text\":\"FAIL_AUDIO\"}").statusCode()).isEqualTo(502);
        assertThat(provider.speechRequests).hasSize(1);
    }

    @TestConfiguration
    static class Configuration {
        @Bean @Primary Clock testClock() { return CLOCK; }
    }
}
