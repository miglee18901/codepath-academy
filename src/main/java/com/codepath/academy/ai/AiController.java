package com.codepath.academy.ai;

import com.codepath.academy.entity.User;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AiController {
    private final AiService service;
    private final AiIndexRepository index;
    public AiController(AiService service, AiIndexRepository index) { this.service = service; this.index = index; }
    @GetMapping("/api/ai/capabilities")
    public Object capabilities() { return service.capabilities(); }
    @GetMapping("/api/search/courses")
    public Object search(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "12") int limit) {
        return service.search(q, categoryId, offset, limit);
    }
    @PostMapping("/api/courses/{courseId}/assistant")
    public Object ask(@PathVariable long courseId, @AuthenticationPrincipal User user, @Valid @RequestBody AiModels.Question question) {
        return service.ask(courseId, user, question);
    }
    @GetMapping("/api/admin/ai/index")
    public Object status() { return Map.of("jobs", index.status(), "backlog", index.backlog(), "failed", index.failures()); }
    @PostMapping("/api/admin/ai/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Object reindex(@RequestParam(required = false) Long courseId) {
        index.reindex(courseId);
        return Map.of("message", "Đã yêu cầu lập lại chỉ mục.");
    }
}
