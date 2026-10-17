package com.codepath.academy.events;

import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableKafka
@ConditionalOnProperty(name="app.role", havingValue="worker")
public class KafkaWorkerConfig {
    @Value("${app.events.bootstrap:localhost:9092}") String bootstrap;
    @Value("${app.events.prefix:codepath.local}") String prefix;
    @Bean KafkaAdmin businessKafkaAdmin() { return new KafkaAdmin(Map.of("bootstrap.servers", bootstrap)); }
    @Bean KafkaAdmin.NewTopics businessTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String name : List.of("commerce.v1", "learning.v1", "retry.notifications", "retry.reports"))
            topics.add(new NewTopic(prefix + "." + name, 3, (short)1).configs(Map.of("retention.ms", "604800000")));
        for (String consumer : List.of("notifications", "reports"))
            topics.add(new NewTopic(prefix + "." + consumer + ".dlt", 3, (short)1).configs(Map.of("retention.ms", "2592000000")));
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
    @Bean KafkaTemplate<String,String> businessKafka() {
        Map<String,Object> p = new HashMap<>();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        var template = new KafkaTemplate<String,String>(new DefaultKafkaProducerFactory<>(p));
        template.setObservationEnabled(true);
        // Spring's default listener includes record payload in error logs.
        template.setProducerListener(new org.springframework.kafka.support.ProducerListener<String,String>() {
            @Override public void onError(org.apache.kafka.clients.producer.ProducerRecord<String,String> record,
                                          org.apache.kafka.clients.producer.RecordMetadata metadata, Exception exception) {
                org.slf4j.LoggerFactory.getLogger(KafkaWorkerConfig.class)
                        .warn("Business event publish failed topic={} code=KAFKA_PUBLISH_FAILED",record.topic());
            }
        });
        return template;
    }
    private ConcurrentKafkaListenerContainerFactory<String,String> factory(String group, KafkaTemplate<String,String> kafka) {
        var cf = new DefaultKafkaConsumerFactory<String,String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 20));
        var f = new ConcurrentKafkaListenerContainerFactory<String,String>();
        f.setConsumerFactory(cf);
        f.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        f.getContainerProperties().setObservationEnabled(true);
        var delays = new org.springframework.util.backoff.BackOff() {
            public org.springframework.util.backoff.BackOffExecution start() {
                return new org.springframework.util.backoff.BackOffExecution() {
                    int index;
                    public long nextBackOff() { return index < 3 ? new long[]{1000,5000,15000}[index++] : STOP; }
                };
            }
        };
        var errors = new DefaultErrorHandler((record, error) -> {
            try { kafka.send(prefix + "." + group + ".dlt", record.key() == null ? "invalid" : record.key().toString(),
                    record.value() == null ? "null" : record.value().toString()).get(15, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception failed) { throw new IllegalStateException("DLT_PUBLISH_FAILED"); }
        }, delays);
        errors.addNotRetryableExceptions(IllegalArgumentException.class);
        errors.setLogLevel(org.springframework.kafka.KafkaException.Level.DEBUG);
        f.setCommonErrorHandler(errors);
        return f;
    }
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> notificationFactory(KafkaTemplate<String,String> businessKafka) { return factory("notifications", businessKafka); }
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> reportFactory(KafkaTemplate<String,String> businessKafka) { return factory("reports", businessKafka); }
    // A DLT write must never be skipped on DB errors.
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> dltFactory(KafkaTemplate<String,String> businessKafka) {
        var f = factory("unused", businessKafka);
        var errors = new DefaultErrorHandler(new org.springframework.util.backoff.FixedBackOff(5000, Long.MAX_VALUE));
        errors.setLogLevel(org.springframework.kafka.KafkaException.Level.DEBUG);
        f.setCommonErrorHandler(errors);
        return f;
    }
}
