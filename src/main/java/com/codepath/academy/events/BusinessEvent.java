package com.codepath.academy.events;

import java.time.Instant;
import java.util.Map;

public record BusinessEvent(String eventId, String eventType, int schemaVersion,
                            Instant occurredAt, String aggregateId, String correlationId,
                            long userId, Map<String, String> payload, Map<String,String> traceContext) {
    public void validate() {
        if (eventId == null || !eventId.matches("[a-f0-9-]{36}") || schemaVersion != 1
                || occurredAt == null || userId < 1 || aggregateId == null || correlationId == null
                || payload == null || !java.util.Set.of("OrderPaid", "EnrollmentGranted", "CourseCompleted").contains(eventType))
            throw new IllegalArgumentException("INVALID_EVENT");
        long course = Long.parseLong(payload.getOrDefault("courseId", "0"));
        if (course < 1) throw new IllegalArgumentException("INVALID_COURSE");
        if ("OrderPaid".equals(eventType)) {
            if (new java.math.BigDecimal(payload.get("amount")).signum() < 0
                    || !"VND".equals(payload.get("currency"))) throw new IllegalArgumentException("INVALID_AMOUNT");
        }
    }
}
