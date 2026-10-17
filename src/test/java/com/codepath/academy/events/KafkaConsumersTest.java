package com.codepath.academy.events;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class KafkaConsumersTest {
    @Test void unsupportedSchemaRetainsOriginalEventIdInDlt() {
        var db=mock(JdbcTemplate.class);
        var consumer=new KafkaConsumers(mock(EventProcessing.class),JsonMapper.builder().findAndAddModules().build(),db);
        String id="d5027d37-5aa3-418c-9c07-497e519c1bc7";
        String raw="{\"eventId\":\""+id+"\",\"schemaVersion\":2}";
        consumer.notificationDlt(new ConsumerRecord<>("codepath.local.notifications.dlt",0,1L,"2",raw));
        verify(db).update(contains("INSERT INTO business_failures"),eq("notifications"),eq(id),eq("codepath.local.notifications.dlt"),eq(raw),eq("FAILED"));
    }
}
