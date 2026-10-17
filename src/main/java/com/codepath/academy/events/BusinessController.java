package com.codepath.academy.events;

import java.time.*;
import java.util.*;
import com.codepath.academy.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@ConditionalOnProperty(name="app.role",havingValue="api",matchIfMissing=true)
public class BusinessController {
    private final JdbcTemplate db;
    public BusinessController(JdbcTemplate db) { this.db=db; }
    private int size(int page,int size) { if(page<0||page>100000||size<1||size>100)throw new IllegalArgumentException("Phân trang không hợp lệ"); return size; }
    private Map<String,Object> page(List<Map<String,Object>> rows,long count,int page,int size) {
        for(var row:rows) row.replaceAll((key,value) -> value instanceof java.time.LocalDateTime local
                ? local.toInstant(ZoneOffset.UTC).toString() : value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant().toString() : value);
        return Map.of("content",rows,"totalElements",count,"page",page,"size",size,"totalPages",(count+size-1)/size);
    }
    @GetMapping("/api/me/notifications")
    public Object notifications(@AuthenticationPrincipal User user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        size(page,size);
        return page(db.queryForList("SELECT id,title,message,target,created_at,read_at FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?",user.getId(),size,page*size),
                db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?",Long.class,user.getId()),page,size);
    }
    @GetMapping("/api/me/notifications/unread-count")
    public Object unread(@AuthenticationPrincipal User user) { return Map.of("count",db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_at IS NULL",Long.class,user.getId())); }
    @PutMapping("/api/me/notifications/{id}/read")
    public void read(@AuthenticationPrincipal User user,@PathVariable long id) {
        if(db.update("UPDATE notifications SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=? AND user_id=?",id,user.getId())==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo");
    }
    @PutMapping("/api/me/notifications/read-all")
    public void readAll(@AuthenticationPrincipal User user) { db.update("UPDATE notifications SET read_at=UTC_TIMESTAMP(6) WHERE user_id=? AND read_at IS NULL",user.getId()); }

    @GetMapping({"/api/admin/reports/overview","/api/admin/reports/daily","/api/admin/reports/courses"})
    public Object report(jakarta.servlet.http.HttpServletRequest request,@RequestParam(required=false) String from,
                         @RequestParam(required=false) String to,@RequestParam(required=false) Long courseId) {
        LocalDate end,start;
        try { end=to==null?LocalDate.now(EventProcessing.REPORT_ZONE):LocalDate.parse(to); start=from==null?end.minusDays(29):LocalDate.parse(from); }
        catch(Exception ex) { throw new IllegalArgumentException("Ngày báo cáo không hợp lệ"); }
        if(start.isAfter(end)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>365||courseId!=null&&courseId<1)
            throw new IllegalArgumentException("Chọn khoảng ngày tối đa 366 ngày");
        String path=request.getRequestURI();
        String grouping=path.endsWith("/daily")?"report_date":path.endsWith("/courses")?"f.course_id":"";
        String select=grouping.isEmpty()?"":grouping+" AS bucket,";
        String sql="SELECT "+select+"""
            COALESCE(SUM(CASE WHEN kind='paid' THEN amount ELSE 0 END),0) AS revenue,
            COUNT(DISTINCT CASE WHEN kind='paid' THEN SUBSTRING_INDEX(fact_key,':',1) END) AS paid_orders,
            COALESCE(SUM(kind='enrollment'),0) AS enrollments,
            COALESCE(SUM(kind='completion' AND historical=0),0) AS completions,
            COALESCE(SUM(kind='completion' AND historical=1),0) AS historical_completions
            FROM business_report_facts f WHERE report_date BETWEEN ? AND ?
            """+(courseId==null?"":" AND f.course_id=?")+(grouping.isEmpty()?"":" GROUP BY "+grouping+" ORDER BY "+grouping);
        var args=new ArrayList<Object>(List.of(start.toString(),end.toString())); if(courseId!=null)args.add(courseId);
        var result=db.queryForList(sql,args.toArray());
        if(path.endsWith("/courses")) for(var row:result) {
            var names=db.queryForList("SELECT title FROM courses WHERE id=?",String.class,row.get("bucket"));
            row.put("title",names.isEmpty()?"Khóa học #"+row.get("bucket"):names.get(0));
        }
        Map<String,Object> response=new LinkedHashMap<>(); response.put("content",result);
        response.put("timezone",EventProcessing.REPORT_ZONE.toString()); response.put("from",start); response.put("to",end);
        response.put("updatedAt",db.queryForObject("SELECT MAX(updated_at) FROM business_report_facts",java.sql.Timestamp.class));
        response.put("backfill",db.queryForList("SELECT job,done,cursor_id FROM business_backfill ORDER BY job"));
        return response;
    }
    @GetMapping("/api/admin/events/status")
    public Object status() {
        return Map.of("outbox",db.queryForList("SELECT COUNT(*) AS pending,COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),UTC_TIMESTAMP()),0) AS oldest_seconds FROM business_outbox WHERE published_at IS NULL").get(0),
                "failures",db.queryForList("SELECT status,COUNT(*) AS count FROM business_failures GROUP BY status"),
                "email",db.queryForList("SELECT status,COUNT(*) AS count FROM business_email_jobs GROUP BY status"),
                "backfill",db.queryForList("SELECT * FROM business_backfill ORDER BY job"));
    }
    @GetMapping("/api/admin/events/failures")
    public Object failures(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        size(page,size); return page(db.queryForList("SELECT id,consumer,event_id,error_code,status,created_at,retry_at FROM business_failures ORDER BY id DESC LIMIT ? OFFSET ?",size,page*size),
                db.queryForObject("SELECT COUNT(*) FROM business_failures",Long.class),page,size);
    }
    @PostMapping("/api/admin/events/failures/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void retry(@PathVariable long id,@AuthenticationPrincipal User user) {
        if(db.update("UPDATE business_failures SET status='RETRY_PENDING',retry_at=UTC_TIMESTAMP(6),retry_by=? WHERE id=? AND status='FAILED'",user.getId(),id)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Sự kiện không ở trạng thái có thể thử lại");
        db.update("INSERT INTO business_retry_audit(failure_id,admin_id) VALUES (?,?)",id,user.getId());
    }
    @GetMapping("/api/admin/events/emails")
    public Object emails(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        size(page,size); return page(db.queryForList("SELECT id,status,attempts,last_error,created_at FROM business_email_jobs WHERE status='FAILED' ORDER BY created_at DESC LIMIT ? OFFSET ?",size,page*size),
                db.queryForObject("SELECT COUNT(*) FROM business_email_jobs WHERE status='FAILED'",Long.class),page,size);
    }
    @PostMapping("/api/admin/events/emails/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void retryEmail(@PathVariable String id,@AuthenticationPrincipal User user) {
        if(db.update("UPDATE business_email_jobs SET status='PENDING',attempts=0,available_at=UTC_TIMESTAMP(6),last_error=NULL WHERE id=? AND status='FAILED'",id)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Email không ở trạng thái có thể thử lại");
        db.update("INSERT INTO business_email_retry_audit(email_id,admin_id) VALUES (?,?)",id,user.getId());
    }
}
