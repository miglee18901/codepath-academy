package com.codepath.academy.events;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.role",havingValue="api",matchIfMissing=true)
public class EventApiMetrics {
    public EventApiMetrics(MeterRegistry registry,@Value("${app.events.enabled:false}") boolean enabled) {
        registry.gauge("codepath.events.enabled",enabled?1:0);
    }
}
