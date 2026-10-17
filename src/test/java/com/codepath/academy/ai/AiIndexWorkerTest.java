package com.codepath.academy.ai;
import com.codepath.academy.ai.AiModels.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class AiIndexWorkerTest {
    @Test void providerFailureRetainsDurableJobAndDoesNotDeletePreviousGeneration() {
        var repo = mock(AiIndexRepository.class);
        var embed = mock(EmbeddingProvider.class);
        var vector = mock(VectorStore.class);
        var props = new AiProperties(true, true, true, "", "", "", "test", "embed", "chat", 3, .5, 3);
        var worker = new AiIndexWorker(props, repo, embed, vector, new SimpleMeterRegistry());
        var job = new AiIndexRepository.Job(1, 2, 0);
        when(repo.pending()).thenReturn(List.of(job));
        when(repo.claim(eq(job), anyString())).thenReturn(true);
        when(repo.documents(1)).thenReturn(List.of(new Document(1,"catalog",1,"title","text",true,true,1)));
        when(embed.embed(anyString(), eq(false), eq(true))).thenThrow(new RuntimeException("secret provider body"));
        worker.poll();
        verify(repo).failed(eq(job), anyString(), eq("INDEX_PROVIDER_OR_STORAGE_ERROR"), eq(true));
        verify(vector, never()).deleteOld(anyLong(), anyLong());
        verify(repo, never()).succeeded(any(), anyString());
    }
    @Test void changedRevisionAbortsIndexingWithoutCountingProviderFailure() {
        var repo = mock(AiIndexRepository.class); var vector = mock(VectorStore.class);
        var props = new AiProperties(true, true, true, "", "", "", "test", "embed", "chat", 3, .5, 3);
        var worker = new AiIndexWorker(props, repo, mock(EmbeddingProvider.class), vector, new SimpleMeterRegistry());
        var job = new AiIndexRepository.Job(1,2,0);
        when(repo.pending()).thenReturn(List.of(job)); when(repo.claim(eq(job), anyString())).thenReturn(true);
        doThrow(new AiIndexRepository.StaleRevision()).when(repo).heartbeat(eq(job), anyString());
        worker.poll();
        verify(repo).failed(eq(job), anyString(), isNull(), eq(false));
        verify(vector, never()).deleteOld(anyLong(), anyLong());
    }
}
