package com.codepath.academy.events;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name="app.role", havingValue="worker")
public class KafkaConsumers {
    private final EventProcessing processing;
    private final ObjectMapper json;
    private final JdbcTemplate db;
    public KafkaConsumers(EventProcessing processing, ObjectMapper json, JdbcTemplate db) { this.processing=processing; this.json=json; this.db=db; }
    @KafkaListener(topics={"${app.events.prefix:codepath.local}.commerce.v1", "${app.events.prefix:codepath.local}.learning.v1", "${app.events.prefix:codepath.local}.retry.notifications"}, groupId="${app.events.prefix:codepath.local}.notifications", containerFactory="notificationFactory")
    public void notifications(String raw) { consume("notifications",raw); }
    @KafkaListener(topics={"${app.events.prefix:codepath.local}.commerce.v1", "${app.events.prefix:codepath.local}.learning.v1", "${app.events.prefix:codepath.local}.retry.reports"}, groupId="${app.events.prefix:codepath.local}.reports", containerFactory="reportFactory")
    public void reports(String raw) { consume("reports",raw); }
    private void consume(String consumer, String raw) {
        BusinessEvent event;
        try { event = json.readValue(raw, BusinessEvent.class); event.validate(); }
        catch (Exception ex) { throw new IllegalArgumentException("INVALID_EVENT"); }
        processing.process(consumer,event);
        db.update("UPDATE business_failures SET status='RESOLVED',lease_owner=NULL,lease_until=NULL WHERE consumer=? AND event_id=?",consumer,event.eventId());
    }
    @KafkaListener(topics="${app.events.prefix:codepath.local}.notifications.dlt",groupId="${app.events.prefix:codepath.local}.notifications-dlt",containerFactory="dltFactory")
    public void notificationDlt(ConsumerRecord<String,String> record) { failure("notifications",record); }
    @KafkaListener(topics="${app.events.prefix:codepath.local}.reports.dlt",groupId="${app.events.prefix:codepath.local}.reports-dlt",containerFactory="dltFactory")
    public void reportDlt(ConsumerRecord<String,String> record) { failure("reports",record); }
    private void failure(String consumer, ConsumerRecord<String,String> r) {
        String raw = r.value() == null ? "null" : r.value();
        String id = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
        try {
            // Unsupported versions are exactly the records that reach DLT: keep their identity for replay.
            String candidate = json.readTree(raw).path("eventId").asText();
            if(candidate.matches("[a-f0-9-]{36}")) id=candidate;
        } catch (Exception ignored) { }
        // If a delayed duplicate DLT arrives after successful replay, leave it resolved.
        Integer done = db.queryForObject("SELECT COUNT(*) FROM business_inbox WHERE consumer=? AND event_id=?",Integer.class,consumer,id);
        db.update("""
            INSERT INTO business_failures(consumer,event_id,source_topic,payload,error_code,status) VALUES (?,?,?,?,'CONSUMER_PROCESSING_FAILED',?)
            ON DUPLICATE KEY UPDATE status=IF(status='RESOLVED','RESOLVED',VALUES(status)), lease_owner=NULL,lease_until=NULL
            """,consumer,id,r.topic(),raw,done != null && done>0 ? "RESOLVED" : "FAILED");
    }
}
