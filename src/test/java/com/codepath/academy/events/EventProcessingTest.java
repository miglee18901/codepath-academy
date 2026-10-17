package com.codepath.academy.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class EventProcessingTest {
    @Mock JdbcTemplate db;
    EventProcessing processing;
    @BeforeEach void init() {processing=new EventProcessing(db);}
    @Test void legacyDatetimeIsReadAsUtcWithEitherJdbcRepresentation() {
        assertEquals(Instant.parse("2026-10-17T18:00:00Z"), ReportBackfill.instant(java.time.LocalDateTime.parse("2026-10-17T18:00:00")));
        assertEquals(Instant.parse("2026-10-17T18:00:00Z"), ReportBackfill.instant(java.sql.Timestamp.from(Instant.parse("2026-10-17T18:00:00Z"))));
    }
    BusinessEvent event(String type) {return new BusinessEvent(UUID.randomUUID().toString(),type,1,Instant.parse("2026-10-17T18:00:00Z"),"42:3","trace",7,Map.of("courseId","3","amount","100000","currency","VND"),Map.of());}
    @Test void duplicateEventDoesNotWriteAnyEffects() {
        var e=event("OrderPaid"); processing.process("reports",e);
        verify(db).update("INSERT IGNORE INTO business_inbox(consumer,event_id) VALUES (?,?)","reports",e.eventId()); verifyNoMoreInteractions(db);
    }
    @Test void invalidConsumerNeverReservesInbox() {assertThrows(IllegalArgumentException.class,()->processing.process("unknown",event("OrderPaid")));verifyNoInteractions(db);}
    @Test void unsupportedSchemaNeverReservesInbox() {
        var e=new BusinessEvent(UUID.randomUUID().toString(),"OrderPaid",2,Instant.now(),"42:3","trace",7,Map.of("courseId","3"),Map.of());
        assertThrows(IllegalArgumentException.class,()->processing.process("reports",e));verifyNoInteractions(db);
    }
    @Test void negativePaymentNeverReservesInbox() {
        var e=new BusinessEvent(UUID.randomUUID().toString(),"OrderPaid",1,Instant.now(),"42:3","trace",7,Map.of("courseId","3","amount","-1","currency","VND"),Map.of());
        assertThrows(IllegalArgumentException.class,()->processing.process("reports",e));verifyNoInteractions(db);
    }
    @Test void reportUsesVietnamDayAcrossUtcMidnight() {
        var e=event("OrderPaid"); when(db.update("INSERT IGNORE INTO business_inbox(consumer,event_id) VALUES (?,?)","reports",e.eventId())).thenReturn(1);
        processing.process("reports",e);
        verify(db).update(contains("INSERT IGNORE INTO business_report_facts"),eq("paid"),eq("42:3"),eq(3L),any(java.sql.Timestamp.class),eq("2026-10-18"),eq(new java.math.BigDecimal("100000")),eq(false));
    }
    @Test void enrollmentDoesNotSendAnotherPurchaseEmail() {
        var e=event("EnrollmentGranted");when(db.update("INSERT IGNORE INTO business_inbox(consumer,event_id) VALUES (?,?)","notifications",e.eventId())).thenReturn(1);
        processing.process("notifications",e);
        verify(db).update("INSERT IGNORE INTO business_inbox(consumer,event_id) VALUES (?,?)","notifications",e.eventId());
        verifyNoMoreInteractions(db);
    }
    @Test void notificationUsesInternalTargetAndSingleDurableEmailJob() {
        var e=event("CourseCompleted");when(db.update("INSERT IGNORE INTO business_inbox(consumer,event_id) VALUES (?,?)","notifications",e.eventId())).thenReturn(1);
        when(db.queryForList("SELECT title FROM courses WHERE id=?",String.class,3L)).thenReturn(List.of("Java"));
        processing.process("notifications",e);
        verify(db).update(contains("INSERT IGNORE INTO notifications"),eq(e.eventId()),eq(7L),anyString(),eq("Bạn đã hoàn thành: Java"),eq("/learn/3"),any(java.sql.Timestamp.class));
        verify(db).update(contains("INSERT IGNORE INTO business_email_jobs"),eq(e.eventId()),eq(7L),anyString(),anyString(),eq("/learn/3"));
    }
}
