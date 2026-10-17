package com.codepath.academy.events;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.kafka.support.ProducerListener;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

class ProducerPrivacyTest {
    @Test @SuppressWarnings("unchecked") void failedSendDoesNotLogPayloadOrProviderException() {
        var config=new KafkaWorkerConfig();ReflectionTestUtils.setField(config,"bootstrap","localhost:9092");
        var template=config.businessKafka();
        var listener=(ProducerListener<String,String>)ReflectionTestUtils.getField(template,"producerListener");
        var logger=(Logger)LoggerFactory.getLogger(KafkaWorkerConfig.class);
        var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        try {
            listener.onError(new ProducerRecord<>("codepath.local.commerce.v1","2","SYNTHETIC_PAYLOAD_SECRET"),null,new RuntimeException("SYNTHETIC_PROVIDER_SECRET"));
            assertEquals(1,logs.list.size());var message=logs.list.get(0).getFormattedMessage();
            assertTrue(message.contains("KAFKA_PUBLISH_FAILED"));assertFalse(message.contains("SYNTHETIC"));assertNull(logs.list.get(0).getThrowableProxy());
        } finally {logger.detachAppender(logs);logs.stop();template.destroy();}
    }
}
