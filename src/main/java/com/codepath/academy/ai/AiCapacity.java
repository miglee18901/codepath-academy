package com.codepath.academy.ai;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiCapacity {
    private final Semaphore slots = new Semaphore(4, true);
    private final Semaphore running = new Semaphore(1, true);
    private final AtomicInteger waiting = new AtomicInteger();
    public static class BackgroundBusy extends RuntimeException {}
    public <T> T run(boolean background, Supplier<T> work) {
        if (background) {
            if (waiting.get() > 0 || !slots.tryAcquire()) throw new BackgroundBusy();
            if (!running.tryAcquire()) { slots.release(); throw new BackgroundBusy(); }
            try { return work.get(); } finally { running.release(); slots.release(); }
        }
        if (!slots.tryAcquire()) throw busy();
        waiting.incrementAndGet();
        boolean acquired = false;
        try {
            acquired = running.tryAcquire(120, TimeUnit.SECONDS);
            if (!acquired) throw busy();
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw busy();
        } finally {
            waiting.decrementAndGet();
            if (acquired) running.release();
            slots.release();
        }
    }
    private ResponseStatusException busy() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI đang bận. Vui lòng thử lại sau.");
    }
}
