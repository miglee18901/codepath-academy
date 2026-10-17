package com.codepath.academy.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class EventLedgerTest {
    JdbcTemplate db; EventLedger ledger;
    @BeforeEach void init() {db=mock(JdbcTemplate.class);ledger=new EventLedger(db,JsonMapper.builder().findAndAddModules().build());ReflectionTestUtils.setField(ledger,"enabled",true);}
    @Test void serializedEventContainsNoCredentialsAndRoundTrips() {
        ledger.append("OrderPaid",7,"42:3",Map.of("courseId","3","amount","0","currency","VND"));
        var raw=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(db).update(contains("INSERT INTO business_outbox"),anyString(),eq("OrderPaid"),eq(7L),raw.capture());
        var e=JsonMapper.builder().findAndAddModules().build().readValue(raw.getValue(),BusinessEvent.class);
        e.validate();assertEquals("0",e.payload().get("amount"));assertFalse(raw.getValue().contains("email"));assertNotNull(e.traceContext());
    }
    @Test void disabledModeDoesNotAccumulateLegacyPurchaseNotifications() {
        ReflectionTestUtils.setField(ledger,"enabled",false);ledger.append("OrderPaid",7,"42:3",Map.of());verifyNoInteractions(db);
    }
    @Test void emptyCourseNeverCompletes() {
        when(db.queryForMap(anyString(),eq(7L),eq(3L))).thenReturn(Map.of("total",0L,"remaining",0L));
        ledger.checkCompletion(7,3);verify(db,never()).update(anyString(),any(),any());
    }
    @Test void incompleteCourseNeverCompletes() {
        when(db.queryForMap(anyString(),eq(7L),eq(3L))).thenReturn(Map.of("total",2L,"remaining",1L));
        ledger.checkCompletion(7,3);verify(db,never()).update(anyString(),any(),any());
    }
    @Test void repeatedCompletionDoesNotAppendNewEvent() {
        when(db.queryForMap(anyString(),eq(7L),eq(3L))).thenReturn(Map.of("total",2L,"remaining",0L));
        ledger.checkCompletion(7,3);
        verify(db).update(contains("INSERT IGNORE INTO course_completions"),eq(7L),eq(3L));
        verify(db,never()).update(contains("INSERT INTO business_outbox"),any(),any(),any(),any());
    }
}
