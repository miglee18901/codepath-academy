package com.codepath.academy.ai;

import com.codepath.academy.ai.AiModels.Hit;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class QdrantClient implements VectorStore {
    private final RestClient client;
    private final AiProperties properties;
    public QdrantClient(AiProperties properties) {
        this.properties = properties;
        if (!properties.collection().matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid AI collection");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(properties.qdrantUrl()).requestFactory(factory)
                .defaultHeader("api-key", properties.qdrantApiKey()).build();
    }
    private String path() { return "/collections/" + properties.collection(); }
    @SuppressWarnings("unchecked")
    public synchronized void ensureCollection() {
        Map<String, Object> response;
        try { response = client.get().uri(path()).retrieve().body(Map.class); }
        catch (HttpClientErrorException.NotFound e) {
            try { client.put().uri(path()).body(Map.of("vectors", Map.of("size", properties.dimensions(), "distance", "Cosine")))
                    .retrieve().toBodilessEntity(); }
            catch (HttpClientErrorException.Conflict ignored) { /* Another worker created it. */ }
            response = client.get().uri(path()).retrieve().body(Map.class);
        }
        var result = (Map<String, Object>) Objects.requireNonNull(response).get("result");
        var config = (Map<String, Object>) result.get("config");
        var params = (Map<String, Object>) config.get("params");
        var vectors = (Map<String, Object>) params.get("vectors");
        if (((Number) vectors.get("size")).intValue() != properties.dimensions()
                || !"Cosine".equals(vectors.get("distance"))) throw new IllegalStateException("Reindex into a new compatible collection");
        for (var field : Map.of("courseId", "integer", "type", "keyword", "revision", "integer",
                "published", "bool", "preview", "bool", "categoryId", "integer").entrySet()) {
            client.put().uri(path() + "/index?wait=true").body(Map.of("field_name", field.getKey(), "field_schema", field.getValue()))
                    .retrieve().toBodilessEntity();
        }
    }
    public void upsert(List<Map<String, Object>> points) {
        if (!points.isEmpty()) client.put().uri(path() + "/points?wait=true").body(Map.of("points", points)).retrieve().toBodilessEntity();
    }
    @SuppressWarnings("unchecked")
    public List<Hit> search(List<Double> vector, Map<String, Object> filter, int limit, double minScore) {
        var body = client.post().uri(path() + "/points/query").body(Map.of("query", vector,
                "filter", filter, "limit", limit, "with_payload", true, "score_threshold", minScore)).retrieve().body(Map.class);
        var result = (Map<String, Object>) Objects.requireNonNull(body).get("result");
        return ((List<Map<String, Object>>) result.get("points")).stream().map(p -> {
            var v = (Map<String, Object>) p.get("payload");
            return new Hit(((Number) p.get("score")).doubleValue(), number(v, "courseId"), (String) v.get("type"),
                    number(v, "sourceId"), (int) number(v, "chunk"), number(v, "revision"), (String) v.get("hash"),
                    (String) v.get("text"), (String) v.get("model"), (String) v.get("chunker"));
        }).toList();
    }
    private long number(Map<String, Object> value, String key) { return ((Number) value.get(key)).longValue(); }
    public static Map<String, Object> match(String key, Object value) { return Map.of("key", key, "match", Map.of("value", value)); }
    public void deleteOld(long courseId, long revision) {
        client.post().uri(path() + "/points/delete?wait=true").body(Map.of("filter", Map.of(
                "must", List.of(match("courseId", courseId)),
                "must_not", List.of(match("revision", revision)))))
                .retrieve().toBodilessEntity();
    }
}
