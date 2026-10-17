package com.codepath.academy.events;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventProcessing {
    private final JdbcTemplate db;
    public EventProcessing(JdbcTemplate db) { this.db = db; }
    static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Transactional
    public void process(String consumer, BusinessEvent event) {
        event.validate();
        if (!Set.of("notifications", "reports").contains(consumer)) throw new IllegalArgumentException("INVALID_CONSUMER");
        if (db.update("INSERT IGNORE INTO business_inbox(consumer,event_id) VALUES (?,?)", consumer, event.eventId()) == 0) return;
        if ("reports".equals(consumer)) report(event);
        else notify(event);
    }

    private void report(BusinessEvent e) {
        String kind = switch(e.eventType()) { case "OrderPaid" -> "paid"; case "EnrollmentGranted" -> "enrollment"; default -> "completion"; };
        fact(kind, e.aggregateId(), Long.parseLong(e.payload().get("courseId")),
                e.occurredAt(), e.payload().getOrDefault("amount", "0"), false);
    }

    public void fact(String kind, String key, long courseId, Instant at, String amount, boolean historical) {
        db.update("""
            INSERT IGNORE INTO business_report_facts(kind,fact_key,course_id,occurred_at,report_date,amount,historical)
            VALUES (?,?,?,?,?,?,?)
            """, kind, key, courseId, Timestamp.from(at), at.atZone(REPORT_ZONE).toLocalDate().toString(),
                new java.math.BigDecimal(amount), historical);
    }

    private void notify(BusinessEvent e) {
        if ("EnrollmentGranted".equals(e.eventType())) return;
        long course = Long.parseLong(e.payload().get("courseId"));
        var titles = db.queryForList("SELECT title FROM courses WHERE id=?", String.class, course);
        String courseTitle = titles.isEmpty() ? "Khóa học #" + course : titles.get(0);
        boolean paid = "OrderPaid".equals(e.eventType());
        String title = paid ? "Đăng ký khóa học thành công" : "Chúc mừng bạn hoàn thành khóa học";
        String message = paid ? "Bạn đã có thể học: " + courseTitle : "Bạn đã hoàn thành: " + courseTitle;
        String target = paid ? "/orders" : "/learn/" + course;
        db.update("INSERT IGNORE INTO notifications(event_id,user_id,title,message,target,created_at) VALUES (?,?,?,?,?,?)",
                e.eventId(), e.userId(), title, message, target, Timestamp.from(e.occurredAt()));
        db.update("INSERT IGNORE INTO business_email_jobs(id,user_id,title,message,target) VALUES (?,?,?,?,?)",
                e.eventId(), e.userId(), title, message, target);
    }
}
