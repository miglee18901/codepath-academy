package com.codepath.academy.ai;

import com.codepath.academy.ai.AiModels.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AiIndexRepository {
    private final JdbcTemplate jdbc;
    public AiIndexRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(long courseId) {
        jdbc.update("""
            INSERT INTO ai_index_jobs(course_id) VALUES (?) ON DUPLICATE KEY UPDATE
            revision = revision + 1, attempts = 0, last_error = NULL, available_at = NOW(6), updated_at = NOW(6)
            """, courseId);
    }
    public record Job(long courseId, long revision, int attempts) {}
    public List<Job> pending() {
        return jdbc.query("""
            SELECT course_id, revision, attempts FROM ai_index_jobs
            WHERE revision <> indexed_revision AND attempts < 5
              AND available_at <= NOW(6) AND (lease_until IS NULL OR lease_until < NOW(6))
            ORDER BY updated_at LIMIT 1
            """, (r, i) -> new Job(r.getLong(1), r.getLong(2), r.getInt(3)));
    }
    public boolean claim(Job job, String owner) {
        return jdbc.update("""
            UPDATE ai_index_jobs SET lease_until = DATE_ADD(NOW(6), INTERVAL 180 SECOND), lease_owner = ?
            WHERE course_id = ? AND revision = ? AND attempts < 5
              AND (lease_until IS NULL OR lease_until < NOW(6)) AND available_at <= NOW(6)
            """, owner, job.courseId(), job.revision()) == 1;
    }
    public void heartbeat(Job job, String owner) {
        if (jdbc.update("UPDATE ai_index_jobs SET lease_until = DATE_ADD(NOW(6), INTERVAL 180 SECOND) WHERE course_id = ? AND lease_owner = ? AND revision = ?",
                job.courseId(), owner, job.revision()) != 1) throw new StaleRevision();
    }
    public static class StaleRevision extends RuntimeException {}
    public long revision(long courseId) {
        var values = jdbc.queryForList("SELECT revision FROM ai_index_jobs WHERE course_id = ?", Long.class, courseId);
        return values.isEmpty() ? -1 : values.get(0);
    }
    public void succeeded(Job job, String owner) {
        jdbc.update("""
            UPDATE ai_index_jobs SET indexed_revision = ?, attempts = 0, last_error = NULL,
            last_success_at = NOW(6), lease_until = NULL, lease_owner = NULL
            WHERE course_id = ? AND revision = ? AND lease_owner = ?
            """, job.revision(), job.courseId(), job.revision(), owner);
        jdbc.update("UPDATE ai_index_jobs SET lease_until = NULL, lease_owner = NULL WHERE course_id = ? AND lease_owner = ? AND revision <> ?",
                job.courseId(), owner, job.revision());
    }
    public void failed(Job job, String owner, String safeCode, boolean countAttempt) {
        int delay = countAttempt ? Math.min(900, 15 * (1 << job.attempts())) : 5;
        jdbc.update("""
            UPDATE ai_index_jobs SET attempts = attempts + ?, last_error = ?,
            available_at = TIMESTAMPADD(SECOND, ?, NOW(6)), lease_until = NULL, lease_owner = NULL
            WHERE course_id = ? AND revision = ? AND lease_owner = ?
            """, countAttempt ? 1 : 0, safeCode, delay, job.courseId(), job.revision(), owner);
        // A content change keeps the lease until the old worker has stopped; then release it.
        jdbc.update("UPDATE ai_index_jobs SET lease_until = NULL, lease_owner = NULL WHERE course_id = ? AND lease_owner = ? AND revision <> ?",
                job.courseId(), owner, job.revision());
    }
    public List<Document> documents(long courseId) {
        var result = new ArrayList<>(jdbc.query("""
            SELECT id, title, description, published, category_id FROM courses WHERE id = ?
            """, (r, i) -> new Document(r.getLong("id"), "catalog", r.getLong("id"), r.getString("title"),
                r.getString("title") + "\n\n" + r.getString("description"), r.getBoolean("published"), true,
                r.getLong("category_id")), courseId));
        result.addAll(jdbc.query("""
            SELECT l.id, l.title, l.content, l.preview, c.title AS course_title, s.title AS section_title,
                c.published, c.category_id FROM lessons l JOIN course_sections s ON l.section_id = s.id
                JOIN courses c ON s.course_id = c.id WHERE c.id = ? ORDER BY s.position, s.id, l.position, l.id
            """, (r, i) -> new Document(courseId, "lesson", r.getLong("id"), r.getString("title"),
                r.getString("course_title") + "\n" + r.getString("section_title") + "\n" + r.getString("title")
                    + "\n\n" + r.getString("content"), r.getBoolean("published"), r.getBoolean("preview"), r.getLong("category_id")), courseId));
        return result;
    }
    public Optional<Document> document(long courseId, String type, long sourceId) {
        return documents(courseId).stream().filter(d -> d.type().equals(type) && d.sourceId() == sourceId).findFirst();
    }
    @Transactional
    public void reindex(Long courseId) {
        jdbc.update("INSERT IGNORE INTO ai_index_jobs(course_id) SELECT id FROM courses");
        if (courseId == null) jdbc.update("UPDATE ai_index_jobs SET revision = revision + 1, attempts = 0, last_error = NULL, available_at = NOW(6), updated_at = NOW(6)");
        else if (jdbc.update("UPDATE ai_index_jobs SET revision = revision + 1, attempts = 0, last_error = NULL, available_at = NOW(6), updated_at = NOW(6) WHERE course_id = ?", courseId) == 0)
            throw new IllegalArgumentException("Không tìm thấy khóa học");
    }
    public List<Map<String, Object>> status() {
        return jdbc.queryForList("SELECT course_id, revision, indexed_revision, attempts, last_error, last_success_at, updated_at FROM ai_index_jobs ORDER BY course_id LIMIT 200");
    }
    public long backlog() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM ai_index_jobs WHERE revision <> indexed_revision", Long.class));
    }
    public long failures() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM ai_index_jobs WHERE revision <> indexed_revision AND attempts >= 5", Long.class));
    }
    public double oldestAge() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(MAX(TIMESTAMPDIFF(SECOND, updated_at, NOW())), 0) FROM ai_index_jobs WHERE revision <> indexed_revision", Double.class));
    }
    public double lastSuccess() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(UNIX_TIMESTAMP(MAX(last_success_at)), 0) FROM ai_index_jobs", Double.class));
    }
}
