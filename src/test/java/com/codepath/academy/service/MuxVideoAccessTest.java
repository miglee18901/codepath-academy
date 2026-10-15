package com.codepath.academy.service;

import com.codepath.academy.controller.MuxVideoController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MuxVideoAccessTest {
    @Test
    void deniedLessonNeverIssuesVideoTokens() {
        var courses = mock(CourseService.class);
        var videos = mock(MuxVideoService.class);
        var controller = new MuxVideoController(courses, videos);
        when(courses.lesson(3L, null)).thenThrow(new AccessDeniedException("private"));
        assertThrows(AccessDeniedException.class, () -> controller.playback(3L, null));
        verifyNoInteractions(videos);
    }
}
