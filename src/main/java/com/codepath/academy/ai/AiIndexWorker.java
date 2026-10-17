package com.codepath.academy.ai;

import com.codepath.academy.ai.AiModels.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class AiIndexWorker {
    private final AiProperties properties;
    private final AiIndexRepository repository;
    private final EmbeddingProvider embeddings;
    private final VectorStore vectors;
    private final MeterRegistry metrics;
    private final String owner = UUID.randomUUID().toString();
    public AiIndexWorker(AiProperties properties, AiIndexRepository repository, EmbeddingProvider embeddings,
                         VectorStore vectors, MeterRegistry metrics) {
        this.properties = properties; this.repository = repository; this.embeddings = embeddings;
        this.vectors = vectors; this.metrics = metrics;
        metrics.gauge("codepath.ai.index.enabled", properties, p -> p.indexingEnabled() ? 1.0 : 0.0);
        metrics.gauge("codepath.ai.index.backlog", repository, AiIndexRepository::backlog);
        metrics.gauge("codepath.ai.index.failed", repository, AiIndexRepository::failures);
        metrics.gauge("codepath.ai.index.oldest.seconds", repository, AiIndexRepository::oldestAge);
        metrics.gauge("codepath.ai.index.last.success", repository, AiIndexRepository::lastSuccess);
    }
    @Scheduled(fixedDelayString = "${app.ai.poll-ms:5000}", initialDelay = 15000)
    public void poll() {
        if (!properties.indexingEnabled()) return;
        for (var job : repository.pending()) {
            if (!repository.claim(job, owner)) continue;
            var sample = io.micrometer.core.instrument.Timer.start(metrics);
            String outcome = "success";
            try {
                vectors.ensureCollection();
                for (var document : repository.documents(job.courseId())) {
                    for (var chunk : TextChunks.split(document.text())) {
                        index(job, document, chunk.text(), chunk.index(), 0);
                    }
                }
                repository.heartbeat(job, owner);
                vectors.deleteOld(job.courseId(), job.revision());
                repository.succeeded(job, owner);
            } catch (AiCapacity.BackgroundBusy | AiIndexRepository.StaleRevision e) {
                outcome = "deferred"; repository.failed(job, owner, null, false);
            } catch (RuntimeException e) {
                outcome = "failure";
                // No provider response bodies or source content in errors/logs.
                repository.failed(job, owner, "INDEX_PROVIDER_OR_STORAGE_ERROR", true);
            } finally {
                metrics.counter("codepath.ai.index.runs", "outcome", outcome).increment();
                sample.stop(metrics.timer("codepath.ai.index.duration", "outcome", outcome));
            }
        }
    }
    private void index(AiIndexRepository.Job job, Document document, String text, int chunk, int depth) {
        repository.heartbeat(job, owner);
        List<Double> vector;
        try { vector = embeddings.embed(text, false, true); }
        catch (HttpClientErrorException.BadRequest e) {
            if (text.length() <= 100 || depth >= 6) throw e;
            int middle = text.length() / 2;
            if (Character.isLowSurrogate(text.charAt(middle))) middle++;
            index(job, document, text.substring(0, middle), chunk, depth + 1);
            index(job, document, text.substring(middle), chunk, depth + 1);
            return;
        }
        repository.heartbeat(job, owner);
        String id = UUID.nameUUIDFromBytes((job.courseId() + ":" + job.revision() + ":" + document.type()
                + ":" + document.sourceId() + ":" + chunk + ":" + TextChunks.hash(text)).getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", document.courseId()); payload.put("sourceId", document.sourceId());
        payload.put("type", document.type()); payload.put("chunk", chunk); payload.put("revision", job.revision());
        payload.put("hash", TextChunks.hash(document.text())); payload.put("text", text);
        payload.put("published", document.published()); payload.put("preview", document.preview());
        payload.put("categoryId", document.categoryId()); payload.put("model", embeddings.identity());
        payload.put("chunker", TextChunks.VERSION);
        vectors.upsert(List.of(Map.of("id", id, "vector", vector, "payload", payload)));
    }
}
