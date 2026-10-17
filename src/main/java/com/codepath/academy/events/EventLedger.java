package com.codepath.academy.events;

import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class EventLedger {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    @org.springframework.beans.factory.annotation.Value("${app.events.enabled:false}") private boolean enabled;
    public EventLedger(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String type, long userId, String aggregate, Map<String, String> payload) {
        if (!enabled) return;
        String id = UUID.randomUUID().toString();
        String trace = MDC.get("trace_id");
        BusinessEvent event = new BusinessEvent(id, type, 1, Instant.now(), aggregate,
                trace == null || trace.isBlank() ? id : trace, userId, payload, EventTrace.capture());
        event.validate();
        db.update("INSERT INTO business_outbox(id,event_type,user_id,payload) VALUES (?,?,?,?)",
                id, type, userId, json.writeValueAsString(event));
    }

    // Called after JPA flush while the caller holds the student's lock.
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkCompletion(long userId, long courseId) {
        var counts = db.queryForMap("""
            SELECT COUNT(*) AS total,COALESCE(SUM(p.id IS NULL OR p.completed=0),0) AS remaining
            FROM lessons l JOIN course_sections s ON s.id=l.section_id
            LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.user_id=?
            WHERE s.course_id=?
            """, userId, courseId);
        if (((Number)counts.get("total")).longValue()==0 || ((Number)counts.get("remaining")).longValue()!=0) return;
        int inserted = db.update("INSERT IGNORE INTO course_completions(user_id,course_id,completed_at) VALUES (?,?,UTC_TIMESTAMP(6))", userId, courseId);
        if (inserted == 1) append("CourseCompleted", userId, userId + ":" + courseId,
                Map.of("courseId", Long.toString(courseId)));
    }
}
