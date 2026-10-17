package com.codepath.academy.events;

import java.util.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.*;
import io.opentelemetry.context.propagation.TextMapGetter;

final class EventTrace {
    private EventTrace() { }
    static Map<String,String> capture() {
        Map<String,String> headers = new HashMap<>();
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(),headers,Map::put);
        // Persist only W3C trace context, never baggage or user attributes.
        headers.keySet().retainAll(Set.of("traceparent","tracestate"));
        return headers;
    }
    static Scope restore(Map<String,String> headers) {
        if(headers==null) return Context.current().makeCurrent();
        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator().extract(Context.root(),headers,new TextMapGetter<Map<String,String>>() {
            public Iterable<String> keys(Map<String,String> carrier) {return carrier.keySet();}
            public String get(Map<String,String> carrier,String key) {return carrier.get(key);}
        }).makeCurrent();
    }
}
