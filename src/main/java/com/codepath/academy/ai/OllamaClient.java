package com.codepath.academy.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OllamaClient implements EmbeddingProvider, ChatProvider {
    private final RestClient client;
    private final AiProperties properties;
    private final AiCapacity capacity;
    private volatile String identity;
    private volatile long identityExpires;
    public OllamaClient(AiProperties properties, AiCapacity capacity) {
        this.properties = properties;
        this.capacity = capacity;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(120));
        client = RestClient.builder().baseUrl(properties.ollamaUrl()).requestFactory(factory).build();
    }
    @Override
    @SuppressWarnings("unchecked")
    public synchronized String identity() {
        if (identity != null && System.nanoTime() < identityExpires) return identity;
        var body = client.get().uri("/api/tags").retrieve().body(Map.class);
        var models = (List<Map<String, Object>>) Objects.requireNonNull(body).get("models");
        var model = models.stream().filter(m -> properties.embeddingModel().equals(m.get("name")))
                .findFirst().orElseThrow(() -> new IllegalStateException("Embedding model not installed"));
        identity = properties.embeddingModel() + "@" + Objects.requireNonNull(model.get("digest"));
        identityExpires = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        return identity;
    }
    @Override
    @SuppressWarnings("unchecked")
    public List<Double> embed(String text, boolean query, boolean background) {
        return capacity.run(background, () -> {
            String prefix = query ? "task: search result | query: " : "title: document | text: ";
            var body = client.post().uri("/api/embed").body(Map.of("model", properties.embeddingModel(),
                    "input", prefix + text, "truncate", false, "keep_alive", "5m",
                    "options", Map.of("num_thread", properties.numThreads())))
                    .retrieve().body(Map.class);
            var embeddings = (List<List<Number>>) Objects.requireNonNull(body).get("embeddings");
            var vector = embeddings.get(0).stream().map(Number::doubleValue).toList();
            if (vector.size() != properties.dimensions() || vector.stream().anyMatch(v -> !Double.isFinite(v)))
                throw new IllegalStateException("Embedding dimension mismatch");
            return vector;
        });
    }
    @Override
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages) {
        return capacity.run(false, () -> {
            var body = client.post().uri("/api/chat").body(Map.of("model", properties.chatModel(),
                    "messages", messages, "stream", false, "think", false, "keep_alive", 0,
                    "options", Map.of("num_ctx", 4096, "num_predict", 512, "temperature", 0.1,
                            "num_thread", properties.numThreads())))
                    .retrieve().body(Map.class);
            if ("length".equals(Objects.requireNonNull(body).get("done_reason")))
                throw new IllegalStateException("Incomplete model response");
            return Objects.toString(((Map<String, Object>) body.get("message")).get("content"), "");
        });
    }
}
