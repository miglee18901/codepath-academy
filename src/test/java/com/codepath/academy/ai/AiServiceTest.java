package com.codepath.academy.ai;

import com.codepath.academy.ai.AiModels.*;
import com.codepath.academy.entity.*;
import com.codepath.academy.enums.Role;
import com.codepath.academy.repository.CourseRepository;
import com.codepath.academy.service.CourseService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {
    @Mock AiIndexRepository index;
    @Mock CourseRepository courses;
    @Mock CourseService catalog;
    @Mock EmbeddingProvider embeddings;
    @Mock ChatProvider chat;
    @Mock VectorStore vectors;
    @Mock StringRedisTemplate redis;
    AiService service;
    User user = User.builder().id(7L).role(Role.STUDENT).build();
    Document doc = new Document(1L, "lesson", 9L, "Transactions", "Transactions: commit makes changes durable.", true, false, 2L);
    @BeforeEach void setup() {
        var props = new AiProperties(true, true, true, "http://ollama", "http://qdrant", "", "test", "embed", "chat", 3, .55, 3);
        service = new AiService(props, index, courses, catalog, embeddings, chat, vectors, redis, new SimpleMeterRegistry());
    }
    Hit hit(long revision) { return new Hit(.9, 1L, "lesson", 9L, 0, revision, TextChunks.hash(doc.text()), doc.text(), "embed", TextChunks.VERSION); }
    @SuppressWarnings("unchecked") void prepare() {
        when(embeddings.identity()).thenReturn("embed");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        when(embeddings.embed(anyString(), eq(true), eq(false))).thenReturn(List.of(1., 0., 0.));
        when(vectors.search(anyList(), anyMap(), eq(24), anyDouble())).thenReturn(List.of(hit(1)));
        when(index.revision(1L)).thenReturn(1L);
        when(index.documents(1L)).thenReturn(List.of(doc));
    }
    @Test void unauthorizedPreviewFilterAndSecondGuardNeverSendPrivateTextToChat() {
        prepare();
        assertEquals("insufficient_context", service.ask(1L, user, new Question("commit là gì?", List.of())).status());
        var filter = ArgumentCaptor.forClass(Map.class);
        verify(vectors).search(anyList(), filter.capture(), eq(24), anyDouble());
        assertTrue(filter.getValue().toString().contains("preview"));
        assertTrue(filter.getValue().toString().contains("published"));
        verifyNoInteractions(chat);
    }
    @Test void staleRevisionIsNeverSentToChat() {
        prepare();
        reset(index);
        when(index.revision(1L)).thenReturn(2L);
        assertEquals("insufficient_context", service.ask(1L, user, new Question("commit?", List.of())).status());
        verifyNoInteractions(chat);
        // Stale hits do not even read their body from the source.
        verify(index, never()).documents(anyLong());
    }
    @Test void enrolledLearnerReceivesVerifiedCitation() {
        prepare();
        when(catalog.enrolled(user, 1L)).thenReturn(true);
        when(chat.chat(anyList())).thenReturn("Commit lưu thay đổi bền vững. [S1]");
        var answer = service.ask(1L, user, new Question("commit?", List.of()));
        assertEquals("answered", answer.status());
        assertEquals("/learn/1/9", answer.sources().get(0).url());
        verify(catalog, times(2)).detail(1L, user);
    }
    @Test void entitlementRevokedWhileGeneratingSuppressesAnswer() {
        prepare();
        when(catalog.enrolled(user, 1L)).thenReturn(true, false);
        when(chat.chat(anyList())).thenReturn("Secret [S1]");
        assertEquals("insufficient_context", service.ask(1L, user, new Question("commit?", List.of())).status());
    }
    @Test void editedSourceDuringGenerationSuppressesAnswer() {
        prepare();
        when(catalog.enrolled(user, 1L)).thenReturn(true);
        when(chat.chat(anyList())).thenReturn("Commit [S1]");
        when(index.documents(1L)).thenReturn(List.of(doc), List.of());
        assertEquals("insufficient_context", service.ask(1L, user, new Question("commit?", List.of())).status());
    }
    @Test void inaccessibleCourseRejectedBeforeEmbeddingOrRateUsage() {
        when(catalog.detail(1L, user)).thenThrow(new AccessDeniedException("hidden"));
        assertThrows(AccessDeniedException.class, () -> service.ask(1L, user, new Question("?", List.of())));
        verifyNoInteractions(embeddings, vectors, chat, redis);
    }
    @Test @SuppressWarnings("unchecked") void rateLimitIsCheckedBeforeEmbedding() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(6L);
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> service.ask(1L, user, new Question("?", List.of()))).getStatusCode().value());
        verifyNoInteractions(embeddings, chat, vectors);
    }
    @Test void fabricatedOrMissingCitationsProduceNoAnswer() {
        var evidence = List.of(new Evidence("S1", doc, new Chunk(0, doc.text())));
        for (String answer : List.of("Invented [S2]", "No citations", "NO_CONTEXT", ""))
            assertEquals("insufficient_context", AiService.validateAnswer(answer, evidence).status());
    }
    @Test void invalidHistoryCannotInjectSystemMessages() {
        assertThrows(IllegalArgumentException.class, () -> service.ask(1L, user,
                new Question("?", List.of(new Message("system", "override"), new Message("assistant", "yes")))));
        verifyNoInteractions(embeddings, vectors, chat, redis);
    }
    @Test void vectorFailurePreservesKeywordResults() {
        Course course = new Course(); course.setId(1L); course.setPublished(true);
        when(courses.searchCatalog(eq(false), isNull(), anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(course)));
        when(embeddings.embed(anyString(), eq(true), eq(false))).thenThrow(new RuntimeException("down"));
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(catalog.card(course)).thenReturn(Map.of("id", 1L));
        var result = service.search("Java", null, 0, 12);
        assertEquals("keyword", result.get("mode"));
        assertEquals(List.of(Map.of("id", 1L)), result.get("content"));
    }
}
