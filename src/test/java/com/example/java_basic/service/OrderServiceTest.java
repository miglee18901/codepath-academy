package com.example.java_basic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.java_basic.entity.*;
import com.example.java_basic.enums.Role;
import com.example.java_basic.repository.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    CourseOrderRepository orders;

    @Mock
    OrderItemRepository items;

    @Mock
    EnrollmentRepository enrollments;

    @Mock
    PaymentRepository payments;

    @Mock
    UserRepository users;

    @Mock
    CourseService courses;

    @Mock
    PayOsGateway gateway;

    @Mock
    ApplicationEventPublisher events;

    @InjectMocks
    OrderService service;

    CourseOrder order;
    User student;

    @BeforeEach
    void setup() {
        student = User.builder()
                .id(7L)
                .username("student")
                .email("s@example.com")
                .fullName("Student")
                .role(Role.STUDENT)
                .build();
        order = new CourseOrder();
        order.setId(42L);
        order.setUserId(7L);
        order.setTotal(new BigDecimal("100000"));
        order.setStatus("PENDING");
    }

    @Test
    void wrongAmountNeverGrantsAccess() {
        assertThrows(IllegalArgumentException.class, () ->
                service.settle(order, new BigDecimal("1"), "ref")
        );
        verifyNoInteractions(payments, enrollments, events);
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    void duplicateWebhookDoesNotCreateAnotherPayment() {
        order.setStatus("PAID");
        service.settle(order, new BigDecimal("100000"), "ref");
        verifyNoInteractions(payments, enrollments, events, users);
    }

    @Test
    void confirmedPaymentGrantsAccessAndEmitsEmailEvent() {
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        OrderItem item = new OrderItem();
        item.setCourseId(3L);
        when(items.findByOrderId(42L)).thenReturn(List.of(item));
        service.settle(order, new BigDecimal("100000"), "ref");
        ArgumentCaptor<Enrollment> enrollment = ArgumentCaptor.forClass(
                Enrollment.class
        );
        verify(enrollments).save(enrollment.capture());
        assertEquals(7L, enrollment.getValue().getUserId());
        assertEquals(3L, enrollment.getValue().getCourseId());
        verify(payments).save(any(Payment.class));
        verify(events).publishEvent(any(OrderService.PurchaseConfirmed.class));
        assertEquals("PAID", order.getStatus());
        assertNotNull(order.getPaidAt());
    }

    @Test
    void existingEnrollmentIsNotDuplicated() {
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        OrderItem item = new OrderItem();
        item.setCourseId(3L);
        when(items.findByOrderId(42L)).thenReturn(List.of(item));
        when(enrollments.existsByUserIdAndCourseId(7L, 3L)).thenReturn(true);
        service.settle(order, new BigDecimal("100000"), "ref");
        verify(enrollments, never()).save(any());
    }

    @Test
    void anotherStudentCannotCheckoutOrder() {
        User stranger = User.builder().id(8L).build();
        when(orders.lockById(42L)).thenReturn(Optional.of(order));
        assertThrows(AccessDeniedException.class, () ->
                service.checkout(42L, stranger)
        );
        verifyNoInteractions(gateway);
    }

    @Test
    void failedSignatureDoesNotLoadOrMutateOrders() {
        when(gateway.verify(null)).thenThrow(
                new IllegalArgumentException("bad signature")
        );
        assertThrows(IllegalArgumentException.class, () -> service.webhook(null));
        verifyNoInteractions(orders, payments, enrollments);
    }

    @Test
    void createUsesCatalogPriceSnapshot() {
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        Course course = new Course();
        course.setId(3L);
        course.setTitle("Java");
        course.setPublished(true);
        course.setPrice(new BigDecimal("120000"));
        when(courses.requireCourse(3L)).thenReturn(course);
        when(users.findById(7L)).thenReturn(Optional.of(student));
        when(orders.save(any())).thenAnswer(a -> {
            CourseOrder o = a.getArgument(0);
            o.setId(1L);
            return o;
        });
        service.create(student, 3L);
        ArgumentCaptor<OrderItem> saved = ArgumentCaptor.forClass(OrderItem.class);
        verify(items).save(saved.capture());
        assertEquals(new BigDecimal("120000"), saved.getValue().getPrice());
        assertEquals("Java", saved.getValue().getCourseTitle());
    }
}
