package com.codepath.academy.events;

import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="app.role",havingValue="worker")
public class ReportBackfill {
    static java.time.Instant instant(Object value) {
        if(value instanceof Timestamp timestamp) return timestamp.toInstant();
        if(value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        throw new IllegalArgumentException("INVALID_LEGACY_TIMESTAMP");
    }
    private final JdbcTemplate db;
    private final EventProcessing processing;
    public ReportBackfill(JdbcTemplate db, EventProcessing processing) { this.db=db;this.processing=processing; }
    @Scheduled(fixedDelay=5000,initialDelay=15000)
    @Transactional
    public void batch() {
        var jobs=db.queryForList("SELECT * FROM business_backfill WHERE done=0 ORDER BY FIELD(job,'orders','enrollments','completions','completion_facts') LIMIT 1 FOR UPDATE SKIP LOCKED");
        if(jobs.isEmpty()) { db.update("UPDATE business_backfill SET updated_at=UTC_TIMESTAMP(6) WHERE job='orders'"); return; }
        var job=jobs.get(0); String name=job.get("job").toString(); long cursor=((Number)job.get("cursor_id")).longValue();
        String sql="orders".equals(name)?"SELECT * FROM orders WHERE id>? ORDER BY id LIMIT 50":"SELECT * FROM enrollments WHERE id>? ORDER BY id LIMIT 50";
        var rows=db.queryForList(sql,cursor);
        for(var r:rows) {
            cursor=((Number)r.get("id")).longValue();
            if("orders".equals(name)) {
                if(!"PAID".equals(r.get("status")))continue;
                for(var item:db.queryForList("SELECT * FROM order_items WHERE order_id=?",cursor))
                    processing.fact("paid",cursor+":"+item.get("course_id"),((Number)item.get("course_id")).longValue(),
                            instant(r.get("paid_at")),item.get("price").toString(),true);
            } else {
                long user=((Number)r.get("user_id")).longValue(),course=((Number)r.get("course_id")).longValue();
                if("enrollments".equals(name)) processing.fact("enrollment",user+":"+course,course,instant(r.get("enrolled_at")),"0",true);
                if("completions".equals(name)) {
                    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",user);
                    db.update("""
                        INSERT IGNORE INTO course_completions(user_id,course_id,completed_at,historical)
                        SELECT ?,?,UTC_TIMESTAMP(6),1 WHERE EXISTS(SELECT 1 FROM lessons l JOIN course_sections s ON s.id=l.section_id WHERE s.course_id=?)
                        AND NOT EXISTS(SELECT 1 FROM lessons l JOIN course_sections s ON s.id=l.section_id
                        LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.user_id=? WHERE s.course_id=? AND (p.id IS NULL OR p.completed=0))
                        """,user,course,course,user,course);
                }
                if("completion_facts".equals(name)) for(var completion:db.queryForList("SELECT * FROM course_completions WHERE user_id=? AND course_id=?",user,course))
                    processing.fact("completion",user+":"+course,course,instant(completion.get("completed_at")),"0",Boolean.TRUE.equals(completion.get("historical")));
            }
        }
        db.update("UPDATE business_backfill SET cursor_id=?,done=?,updated_at=UTC_TIMESTAMP(6) WHERE job=?",cursor,rows.size()<50,name);
    }
}
