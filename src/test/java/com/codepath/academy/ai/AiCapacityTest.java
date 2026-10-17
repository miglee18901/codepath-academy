package com.codepath.academy.ai;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCapacityTest {
    @Test void backgroundNeverOverlapsInferenceAndForegroundResumesAfterRelease() throws Exception {
        AiCapacity capacity = new AiCapacity();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var background = executor.submit(() -> capacity.run(true, () -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return "background";
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(AiCapacity.BackgroundBusy.class, () -> capacity.run(true, () -> "must not run"));
            var foreground = executor.submit(() -> capacity.run(false, () -> "foreground"));
            release.countDown();
            assertEquals("background", background.get(5, TimeUnit.SECONDS));
            assertEquals("foreground", foreground.get(5, TimeUnit.SECONDS));
            assertEquals("next", capacity.run(true, () -> "next"));
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
