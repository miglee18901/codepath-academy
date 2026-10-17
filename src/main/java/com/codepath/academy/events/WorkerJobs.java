package com.codepath.academy.events;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@ConditionalOnProperty(name="app.role",havingValue="worker")
public class WorkerJobs {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String,String> kafka;
    private final JavaMailSender mail;
    private final MeterRegistry metrics;
    private final tools.jackson.databind.ObjectMapper json;
    @Value("${app.events.prefix:codepath.local}") String prefix;
    @Value("${app.mail-from:no-reply@codepath.local}") String from;
    @Value("${app.frontend-url:http://localhost:5173}") String frontend;
    public WorkerJobs(JdbcTemplate db, org.springframework.transaction.PlatformTransactionManager manager,
                      KafkaTemplate<String,String> kafka, JavaMailSender mail, MeterRegistry metrics, tools.jackson.databind.ObjectMapper json) {
        this.db=db; this.tx=new TransactionTemplate(manager); this.kafka=kafka; this.mail=mail; this.metrics=metrics; this.json=json;
        gauge("outbox.pending", "SELECT COUNT(*) FROM business_outbox WHERE published_at IS NULL");
        gauge("outbox.oldest.seconds", "SELECT COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),UTC_TIMESTAMP()),0) FROM business_outbox WHERE published_at IS NULL");
        gauge("failures", "SELECT COUNT(*) FROM business_failures WHERE status='FAILED'");
        gauge("email.failed", "SELECT COUNT(*) FROM business_email_jobs WHERE status='FAILED'");
        gauge("heartbeat", "SELECT UNIX_TIMESTAMP(MAX(updated_at)) FROM business_backfill");
    }
    private void gauge(String name,String sql) {
        metrics.gauge("codepath.events."+name, sql, q -> { try { Number n=db.queryForObject(q,Number.class); return n==null?0:n.doubleValue(); } catch(Exception ex) { return Double.NaN; } });
    }
    private Map<String,Object> claim(String table,String condition,String order) {
        return tx.execute(s -> {
            var rows=db.queryForList("SELECT * FROM "+table+" WHERE "+condition+" AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) ORDER BY "+order+" LIMIT 1 FOR UPDATE SKIP LOCKED");
            if(rows.isEmpty()) return null;
            var row=rows.get(0); String owner=UUID.randomUUID().toString();
            db.update("UPDATE "+table+" SET lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 60 SECOND) WHERE id=?",owner,row.get("id"));
            row.put("owner",owner); return row;
        });
    }
    @Scheduled(fixedDelay=1000,initialDelay=15000)
    public void publish() {
        for(int i=0;i<20;i++) {
            var r=claim("business_outbox","published_at IS NULL AND available_at<=UTC_TIMESTAMP(6)","created_at,id");
            if(r==null) return;
            try {
                String type=r.get("event_type").toString();
                BusinessEvent event=json.readValue(r.get("payload").toString(),BusinessEvent.class);
                try(var scope=EventTrace.restore(event.traceContext())) {
                    kafka.send(prefix+("CourseCompleted".equals(type)?".learning.v1":".commerce.v1"),r.get("user_id").toString(),r.get("payload").toString()).get(15,TimeUnit.SECONDS);
                }
                db.update("UPDATE business_outbox SET published_at=UTC_TIMESTAMP(6),lease_owner=NULL,lease_until=NULL,last_error=NULL WHERE id=? AND lease_owner=?",r.get("id"),r.get("owner"));
                metrics.counter("codepath.events.published","outcome","success").increment();
            } catch(Exception ex) {
                db.update("UPDATE business_outbox SET attempts=attempts+1,last_error='KAFKA_UNAVAILABLE',available_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 SECOND),lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",r.get("id"),r.get("owner"));
                metrics.counter("codepath.events.published","outcome","failure").increment(); return;
            }
        }
    }
    @Scheduled(fixedDelay=5000,initialDelay=20000)
    public void replay() {
        var r=claim("business_failures","status='RETRY_PENDING'","id"); if(r==null) return;
        try {
            kafka.send(prefix+".retry."+r.get("consumer"),r.get("event_id").toString(),r.get("payload").toString()).get(15,TimeUnit.SECONDS);
            db.update("UPDATE business_failures SET status='RETRYING',lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=? AND status='RETRY_PENDING'",r.get("id"),r.get("owner"));
        } catch(Exception ex) {
            // Lease expiry schedules a later retry; never discard the request.
            metrics.counter("codepath.events.replay.failures").increment();
        }
    }
    @Scheduled(fixedDelay=3000,initialDelay=20000)
    public void email() {
        for(int i=0;i<10;i++) {
            var r=claim("business_email_jobs","status='PENDING' AND available_at<=UTC_TIMESTAMP(6)","created_at,id"); if(r==null)return;
            try {
                var users=db.queryForList("SELECT email FROM users WHERE id=?",r.get("user_id"));
                if(users.isEmpty()) throw new IllegalArgumentException("USER_REMOVED");
                var message=new SimpleMailMessage(); message.setFrom(from); message.setTo(users.get(0).get("email").toString());
                message.setSubject(r.get("title").toString());
                message.setText(r.get("message")+"\n\n"+frontend.replaceAll("/$","")+r.get("target")); mail.send(message);
                db.update("UPDATE business_email_jobs SET status='SENT',sent_at=UTC_TIMESTAMP(6),lease_owner=NULL,lease_until=NULL,last_error=NULL WHERE id=? AND lease_owner=?",r.get("id"),r.get("owner"));
                metrics.counter("codepath.email.deliveries","outcome","success").increment();
            } catch(Exception ex) {
                int attempts=((Number)r.get("attempts")).intValue()+1;
                db.update("UPDATE business_email_jobs SET attempts=?,status=?,last_error='SMTP_DELIVERY_FAILED',available_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ? SECOND),lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",attempts,attempts>=5?"FAILED":"PENDING",Math.min(300,15*(1<<attempts)),r.get("id"),r.get("owner"));
                metrics.counter("codepath.email.deliveries","outcome","failure").increment();
            }
        }
    }
}
