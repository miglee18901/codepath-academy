package com.example.java_basic.service;

import com.example.java_basic.entity.Lesson;
import com.example.java_basic.repository.LessonRepository;

import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MuxVideoServiceTest {
    @Mock
    LessonRepository lessons;
    @Mock
    MuxGateway mux;
    @InjectMocks
    MuxVideoService service;
    Lesson lesson;

    @BeforeEach
    void setup() {
        lesson = new Lesson();
        lesson.setId(3L);
    }

    @Test
    void preventsDuplicateUploadsWithoutAllocatingAnotherVideo() {
        lesson.setMuxUploadId("existing");
        when(lessons.lockById(3L)).thenReturn(Optional.of(lesson));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.start(3L)).getStatusCode().value());
        verifyNoInteractions(mux);
    }

    @Test
    void refreshResolvesAssetUsesOnlySignedPlaybackAndUpdatesDuration() {
        lesson.setMuxUploadId("upload-1");
        when(lessons.lockById(3L)).thenReturn(Optional.of(lesson));
        when(mux.upload("upload-1")).thenReturn(Map.of("status", "asset_created", "asset_id", "asset-1"));
        when(mux.asset("asset-1")).thenReturn(Map.of("status", "ready", "duration", 121.2,
                "playback_ids", List.of(Map.of("id", "public-id", "policy", "public"), Map.of("id", "private-id", "policy", "signed"))));
        assertEquals("ready", service.refresh(3L).get("status"));
        assertEquals("private-id", lesson.getMuxPlaybackId());
        assertEquals(3, lesson.getDurationMinutes());
    }

    @Test
    void pendingVideoCannotIssuePlaybackTokens() {
        lesson.setMuxStatus("preparing");
        when(lessons.findById(3L)).thenReturn(Optional.of(lesson));
        assertThrows(ResponseStatusException.class, () -> service.playback(3L));
        verifyNoInteractions(mux);
    }

    @Test
    void removingCompletedUploadDeletesRemoteAssetBeforeDetaching() {
        lesson.setMuxUploadId("upload-1");
        when(lessons.lockById(3L)).thenReturn(Optional.of(lesson));
        when(mux.upload("upload-1")).thenReturn(Map.of("status", "asset_created", "asset_id", "asset-1"));
        service.remove(3L);
        verify(mux).deleteAsset("asset-1");
        assertNull(lesson.getMuxUploadId());
        assertNull(lesson.getMuxPlaybackId());
    }

    @Test
    void failedRemoteDeletionPreservesReferenceForRetry() {
        lesson.setMuxUploadId("upload-1");
        when(lessons.lockById(3L)).thenReturn(Optional.of(lesson));
        when(mux.upload("upload-1")).thenReturn(Map.of("asset_id", "asset-1"));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY)).when(mux).deleteAsset("asset-1");
        assertThrows(ResponseStatusException.class, () -> service.remove(3L));
        assertEquals("upload-1", lesson.getMuxUploadId());
        verify(lessons, never()).save(any());
    }
}
