package com.codepath.academy.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codepath.academy.entity.*;
import com.codepath.academy.enums.Role;
import com.codepath.academy.repository.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

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

    private void remotePayment(PaymentLinkStatus status, long amount, long paid) {
        order.setCheckoutUrl("https://pay.payos.vn/test-link");
        when(orders.lockById(42L)).thenReturn(Optional.of(order));
        PaymentLink payment = new PaymentLink();
        payment.setOrderCode(42L);
        payment.setAmount(amount);
        payment.setAmountPaid(paid);
        payment.setAmountRemaining(amount - paid);
        payment.setStatus(status);
        when(gateway.payment(42L)).thenReturn(payment);
    }

    @Test
    void reconciliationRecoversMissingWebhookExactlyOnce() {
        remotePayment(PaymentLinkStatus.PAID, 100000, 100000);
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        OrderItem item = new OrderItem();
        item.setCourseId(3L);
        when(items.findByOrderId(42L)).thenReturn(List.of(item));
        assertEquals("PAID", service.reconcile(42L, student).get("status"));
        assertEquals("PAID", service.reconcile(42L, student).get("status"));
        verify(gateway, times(1)).payment(42L);
        verify(payments, times(1)).save(any());
        verify(enrollments, times(1)).save(any());
        verify(events, times(1)).publishEvent(any(OrderService.PurchaseConfirmed.class));
    }

    @Test
    void underpaidOrderDoesNotGrantAccess() {
        remotePayment(PaymentLinkStatus.UNDERPAID, 100000, 50000);
        assertEquals("PENDING", service.reconcile(42L, student).get("status"));
        verifyNoInteractions(payments, enrollments, events);
    }

    @Test
    void remoteAmountMismatchDoesNotGrantAccess() {
        remotePayment(PaymentLinkStatus.PAID, 10000, 10000);
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(42L, student));
        verifyNoInteractions(payments, enrollments, events);
    }

    @Test
    void paidStatusWithInsufficientAmountDoesNotGrantAccess() {
        remotePayment(PaymentLinkStatus.PAID, 100000, 50000);
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(42L, student));
        verifyNoInteractions(payments, enrollments, events);
    }

    @Test
    void cancelledRemoteLinkIsNotReusedForCheckout() {
        remotePayment(PaymentLinkStatus.CANCELLED, 100000, 0);
        assertEquals("CANCELLED", service.checkout(42L, student).get("status"));
        verify(gateway, never()).checkout(any(), any());
        verifyNoInteractions(payments, enrollments, events);
    }

    @Test
    void paidRemoteLinkIsSettledInsteadOfRedirectingToPayAgain() {
        remotePayment(PaymentLinkStatus.PAID, 100000, 100000);
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        assertEquals("PAID", service.checkout(42L, student).get("status"));
        verify(gateway, never()).checkout(any(), any());
        verify(payments).save(any());
    }

    @Test
    void anotherStudentCannotReconcileOrder() {
        when(orders.lockById(42L)).thenReturn(Optional.of(order));
        assertThrows(AccessDeniedException.class,
                () -> service.reconcile(42L, User.builder().id(8L).build()));
        verifyNoInteractions(gateway, payments, enrollments);
    }

    @Test
    void enrolledStudentCannotCreateAnotherOrder() {
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        Course course = new Course();
        course.setPublished(true);
        when(courses.requireCourse(3L)).thenReturn(course);
        when(enrollments.existsByUserIdAndCourseId(7L, 3L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.create(student, 3L));
        verifyNoInteractions(orders, items, gateway);
    }

    @Test
    void pendingOrderIsReusedInsteadOfCreatingAnotherOne() {
        when(users.lockById(7L)).thenReturn(Optional.of(student));
        Course course = new Course();
        course.setPublished(true);
        when(courses.requireCourse(3L)).thenReturn(course);
        when(orders.findByUserIdOrderByIdDesc(7L)).thenReturn(List.of(order));
        OrderItem item = new OrderItem();
        item.setCourseId(3L);
        when(items.findByOrderId(42L)).thenReturn(List.of(item));
        assertEquals(42L, service.create(student, 3L).get("id"));
        verify(orders, never()).save(any());
        verify(items, never()).save(any());
    }
}
