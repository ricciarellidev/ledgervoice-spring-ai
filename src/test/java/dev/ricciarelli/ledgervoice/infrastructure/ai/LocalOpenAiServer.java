package dev.ricciarelli.ledgervoice.infrastructure.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A wire-contract fixture, not an AI model or a transcription-quality test. */
final class LocalOpenAiServer implements AutoCloseable {
    private final HttpServer server;
    final List<String> chatRequests = new CopyOnWriteArrayList<>();
    final List<String> transcriptionRequests = new CopyOnWriteArrayList<>();
    final List<String> speechRequests = new CopyOnWriteArrayList<>();

    LocalOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::chat);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            transcriptionRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            respond(exchange, 200, "application/json", "{\"text\":\"Registre meu almoço de 42,90 reais em alimentação hoje.\"}");
        });
        server.createContext("/v1/audio/speech", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            speechRequests.add(body);
            if (body.contains("FAIL_AUDIO")) respond(exchange, 503, "application/json", "{\"error\":{\"message\":\"fixture unavailable\",\"type\":\"server_error\"}}");
            else respond(exchange, 200, "audio/mpeg", "ID3synthetic-wire-fixture");
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    void reset() {
        chatRequests.clear();
        transcriptionRequests.clear();
        speechRequests.clear();
    }

    private void chat(HttpExchange exchange) throws IOException {
        var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);
        String message;
        if (request.contains("FAIL_PROVIDER")) {
            respond(exchange, 503, "application/json", "{\"error\":{\"message\":\"fixture unavailable\",\"type\":\"server_error\"}}");
            return;
        } else if (request.contains("NO_TOOL")) {
            message = "{\"role\":\"assistant\",\"content\":\"I saved your expense already.\"}";
        } else {
            String name = request.contains("QUERY_SUMMARY") ? "monthlySummary"
                    : request.contains("QUERY_LIST") ? "listTransactions"
                    : request.contains("ATTACK_CONFIRM") ? "confirmTransaction" : "prepareTransaction";
            String args = name.equals("monthlySummary") ? "{\"month\":\"2026-10\"}"
                    : name.equals("listTransactions") ? "{\"month\":\"2026-10\",\"category\":\"ALL\"}"
                    : "{\"direction\":\"EXPENSE\",\"amount\":\"" + (request.contains("INVALID_AMOUNT") ? "-42.90" : "42.90")
                    + "\",\"category\":\"FOOD\",\"description\":\"Almoço\",\"occurredOn\":\"2026-10-13\"}";
            String encodedArgs = args.replace("\\", "\\\\").replace("\"", "\\\"");
            message = "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_fixture_001\",\"type\":\"function\","
                    + "\"function\":{\"name\":\"" + name + "\",\"arguments\":\"" + encodedArgs + "\"}}]}";
        }
        var finish = request.contains("NO_TOOL") ? "stop" : "tool_calls";
        respond(exchange, 200, "application/json", "{\"id\":\"chatcmpl_fixture\",\"object\":\"chat.completion\",\"created\":1791903600,"
                + "\"model\":\"gpt-4.1-mini\",\"choices\":[{\"index\":0,\"message\":" + message
                + ",\"finish_reason\":\"" + finish + "\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":10,\"total_tokens\":20}}");
    }

    private static void respond(HttpExchange exchange, int status, String type, String payload) throws IOException {
        var bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
