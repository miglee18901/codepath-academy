package com.codepath.academy.events;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@ConditionalOnProperty(name="app.role",havingValue="worker")
public class ConsumerLagMetrics implements DisposableBean {
    private final Admin admin;
    private final String prefix;
    private final Map<String,AtomicLong> lag=new HashMap<>();
    private final AtomicLong available=new AtomicLong();
    public ConsumerLagMetrics(@Value("${app.events.bootstrap:localhost:9092}") String bootstrap,
                              @Value("${app.events.prefix:codepath.local}") String prefix,
                              MeterRegistry metrics,KafkaListenerEndpointRegistry registry) {
        this.prefix=prefix;
        admin=Admin.create(Map.of("bootstrap.servers",bootstrap,"default.api.timeout.ms",5000,"request.timeout.ms",3000));
        metrics.gauge("codepath.events.broker.available",available);
        metrics.gauge("codepath.events.consumers.running",registry,r -> r.getListenerContainers().stream().filter(c -> c.isRunning()).count());
        for(String group:List.of("notifications","reports")) {
            var value=new AtomicLong(-1); lag.put(group,value);
            metrics.gauge("codepath.events.consumer.lag",List.of(io.micrometer.core.instrument.Tag.of("consumer",group)),value);
        }
    }
    @Scheduled(fixedDelay=15000,initialDelay=25000)
    public void sample() {
        try {
            for(String group:lag.keySet()) {
                var topics=List.of(prefix+".commerce.v1",prefix+".learning.v1",prefix+".retry."+group);
                var descriptions=admin.describeTopics(topics).allTopicNames().get(6,TimeUnit.SECONDS);
                Map<TopicPartition,OffsetSpec> requests=new HashMap<>();
                descriptions.forEach((name,description) -> description.partitions().forEach(p -> requests.put(new TopicPartition(name,p.partition()),OffsetSpec.latest())));
                var ends=admin.listOffsets(requests).all().get(6,TimeUnit.SECONDS);
                var offsets=admin.listConsumerGroupOffsets(prefix+"."+group).partitionsToOffsetAndMetadata().get(6,TimeUnit.SECONDS);
                long count=0;
                for(var entry:ends.entrySet()) count+=Math.max(0,entry.getValue().offset()-(offsets.containsKey(entry.getKey())?offsets.get(entry.getKey()).offset():0));
                lag.get(group).set(count);
            }
            available.set(1);
        } catch(Exception ex) {available.set(0);lag.values().forEach(v -> v.set(-1));}
    }
    public void destroy() {admin.close(java.time.Duration.ofSeconds(2));}
}
