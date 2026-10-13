package dev.ricciarelli.ledgervoice.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpTestClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;

    public HttpTestClient(int port) {
        base = "http://127.0.0.1:" + port;
    }

    public HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("X-Correlation-Id", "http_test_001")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> confirm(String id) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/proposals/" + id + "/confirm"))
                .timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> upload(byte[] audio, String filename) throws Exception {
        return http.send(uploadRequest(audio, filename), HttpResponse.BodyHandlers.ofString());
    }

    public HttpRequest uploadRequest(byte[] audio, String filename) {
        var boundary = "ledgervoice_fixture_boundary";
        var header = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var footer = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return HttpRequest.newBuilder(URI.create(base + "/api/v1/assistant/audio")).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(header),
                        HttpRequest.BodyPublishers.ofByteArray(audio), HttpRequest.BodyPublishers.ofByteArray(footer))).build();
    }

    public HttpResponse<byte[]> speech(String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/assistant/speech")).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
