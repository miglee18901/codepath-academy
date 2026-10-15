package com.example.java_basic.service;

import com.example.java_basic.entity.Lesson;
import com.example.java_basic.repository.LessonRepository;

import java.time.Instant;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MuxVideoService {
    private final LessonRepository lessons;
    private final MuxGateway mux;

    @Transactional
    public Map<String, Object> start(Long lessonId) {
        Lesson lesson = locked(lessonId);
        if (lesson.getMuxUploadId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Bài học đã có video Mux. Xóa video hiện tại trước khi tải video mới.");
        var upload = mux.createUpload(lessonId);
        if (!(upload.get("id") instanceof String) || !(upload.get("url") instanceof String))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mux không trả về URL upload hợp lệ.");
        lesson.setMuxUploadId((String) upload.get("id"));
        lesson.setMuxStatus("waiting");
        lessons.save(lesson);
        return Map.of("uploadUrl", upload.get("url"), "status", "waiting");
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> refresh(Long lessonId) {
        Lesson lesson = locked(lessonId);
        if (lesson.getMuxUploadId() == null) return status(lesson);
        var upload = mux.upload(lesson.getMuxUploadId());
        lesson.setMuxStatus(Objects.toString(upload.get("status"), "waiting"));
        if (upload.get("asset_id") instanceof String assetId) lesson.setMuxAssetId(assetId);
        if (lesson.getMuxAssetId() != null) {
            var asset = mux.asset(lesson.getMuxAssetId());
            lesson.setMuxStatus(Objects.toString(asset.get("status"), "preparing"));
            lesson.setMuxPlaybackId(null);
            if ("ready".equals(lesson.getMuxStatus())) {
                var ids = (List<Map<String, Object>>) asset.getOrDefault("playback_ids", List.of());
                lesson.setMuxPlaybackId(ids.stream().filter(p -> "signed".equals(p.get("policy")))
                        .map(p -> (String) p.get("id")).findFirst().orElse(null));
                if (lesson.getMuxPlaybackId() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Video Mux thiếu signed playback ID.");
                if (asset.get("duration") instanceof Number seconds)
                    lesson.setDurationMinutes((int) Math.ceil(seconds.doubleValue() / 60));
            }
        }
        lessons.save(lesson);
        return status(lesson);
    }

    @Transactional
    public Map<String, Object> remove(Long lessonId) {
        Lesson lesson = locked(lessonId);
        if (lesson.getMuxUploadId() != null) {
            var upload = mux.upload(lesson.getMuxUploadId());
            String assetId = Objects.toString(upload.get("asset_id"), lesson.getMuxAssetId());
            if (assetId != null) mux.deleteAsset(assetId);
            else if ("waiting".equals(upload.get("status"))) mux.cancelUpload(lesson.getMuxUploadId());
        }
        lesson.setMuxUploadId(null);
        lesson.setMuxAssetId(null);
        lesson.setMuxPlaybackId(null);
        lesson.setMuxStatus(null);
        lessons.save(lesson);
        return status(lesson);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> playback(Long lessonId) {
        // Authorization is performed by CourseService.lesson in the controller before this call.
        Lesson lesson = lessons.findById(lessonId).orElseThrow(CourseService::missing);
        if (!"ready".equals(lesson.getMuxStatus()) || lesson.getMuxPlaybackId() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video chưa sẵn sàng. Vui lòng thử lại sau.");
        Instant expires = Instant.now().plusSeconds(Math.max(3600L, lesson.getDurationMinutes() * 60L + 900));
        return Map.of("playbackId", lesson.getMuxPlaybackId(), "tokens", mux.tokens(lesson.getMuxPlaybackId(), expires),
                "expiresAt", expires.toString());
    }

    private Lesson locked(Long id) {
        return lessons.lockById(id).orElseThrow(CourseService::missing);
    }

    private Map<String, Object> status(Lesson lesson) {
        return Map.of("configured", mux.configured(), "status", Objects.toString(lesson.getMuxStatus(), "none"),
                "durationMinutes", lesson.getDurationMinutes());
    }
}
