package com.codepath.academy.controller;

import com.codepath.academy.entity.User;
import com.codepath.academy.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MuxVideoController {
    private final CourseService courses;
    private final MuxVideoService videos;

    @PostMapping("/api/admin/lessons/{id}/mux/upload")
    public Object upload(@PathVariable Long id) {
        return videos.start(id);
    }

    @PostMapping("/api/admin/lessons/{id}/mux/refresh")
    public Object refresh(@PathVariable Long id) {
        return videos.refresh(id);
    }

    @DeleteMapping("/api/admin/lessons/{id}/mux")
    public Object remove(@PathVariable Long id) {
        return videos.remove(id);
    }

    @GetMapping("/api/lessons/{id}/playback")
    public ResponseEntity<?> playback(@PathVariable Long id, @AuthenticationPrincipal User user) {
        courses.lesson(id, user);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(videos.playback(id));
    }
}
