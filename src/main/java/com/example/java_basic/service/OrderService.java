package com.example.java_basic.service;

import static com.example.java_basic.service.CourseService.*;

import com.example.java_basic.entity.*;
import com.example.java_basic.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.Webhook;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private final CourseOrderRepository orders;
  private final OrderItemRepository items;
  private final EnrollmentRepository enrollments;
  private final PaymentRepository payments;
  private final UserRepository users;
  private final CourseService courses;
  private final PayOsGateway gateway;
  private final ApplicationEventPublisher events;

  public record PurchaseConfirmed(
    String email,
    String fullName,
    Long orderId
  ) {}

  @Transactional
  public Map<String, Object> create(User user, Long courseId) {
    users.lockById(user.getId()).orElseThrow(CourseService::missing);
    Course course = courses.requireCourse(courseId);
    if (!course.isPublished()) throw missing();
    if (
      enrollments.existsByUserIdAndCourseId(user.getId(), courseId)
    ) throw new IllegalStateException("Bạn đã sở hữu khóa học này");
    // Reuse the existing pending order so retries cannot create multiple purchase links.
    for (CourseOrder old : orders.findByUserIdOrderByIdDesc(user.getId())) {
      if (
        "PENDING".equals(old.getStatus()) &&
        items
          .findByOrderId(old.getId())
          .stream()
          .anyMatch(i -> i.getCourseId().equals(courseId))
      ) return orderView(old);
    }
    CourseOrder order = new CourseOrder();
    order.setUserId(user.getId());
    order.setTotal(course.getPrice());
    order.setStatus("PENDING");
    order.setCreatedAt(LocalDateTime.now());
    orders.save(order);
    OrderItem item = new OrderItem();
    item.setOrderId(order.getId());
    item.setCourseId(courseId);
    item.setCourseTitle(course.getTitle());
    item.setPrice(course.getPrice());
    items.save(item);
    if (order.getTotal().signum() == 0) settle(
      order,
      BigDecimal.ZERO,
      "FREE-" + order.getId()
    );
    return orderView(order);
  }

  public List<Map<String, Object>> mine(User user) {
    return orders
      .findByUserIdOrderByIdDesc(user.getId())
      .stream()
      .map(this::orderView)
      .toList();
  }

  public Map<String, Object> get(Long id, User user) {
    return orderView(owned(id, user));
  }

  private CourseOrder owned(Long id, User user) {
    CourseOrder o = orders.findById(id).orElseThrow(CourseService::missing);
    if (
      !o.getUserId().equals(user.getId()) && !courses.admin(user)
    ) throw new AccessDeniedException("Đơn hàng không thuộc tài khoản");
    return o;
  }

  @Transactional
  public Map<String, Object> checkout(Long id, User user) {
    CourseOrder o = orders.lockById(id).orElseThrow(CourseService::missing);
    if (!o.getUserId().equals(user.getId())) throw new AccessDeniedException(
      "Đơn hàng không thuộc tài khoản"
    );
    if (!"PENDING".equals(o.getStatus())) throw new IllegalStateException(
      "Đơn hàng đã thanh toán"
    );
    if (o.getCheckoutUrl() == null) o.setCheckoutUrl(
      gateway.checkout(o, items.findByOrderId(id))
    );
    return view("checkoutUrl", o.getCheckoutUrl());
  }

  @Transactional
  public void webhook(Webhook webhook) {
    var data = gateway.verify(webhook);
    if (!"00".equals(data.getCode())) return;
    CourseOrder order = orders
      .lockById(data.getOrderCode())
      .orElseThrow(CourseService::missing);
    settle(
      order,
      BigDecimal.valueOf(data.getAmount()),
      "PAYOS-" + data.getOrderCode()
    );
  }

  // Caller holds the order lock (or owns a newly created order).
  void settle(CourseOrder order, BigDecimal received, String reference) {
    if (
      order.getTotal().compareTo(received) != 0
    ) throw new IllegalArgumentException(
      "Số tiền thanh toán không khớp đơn hàng"
    );
    if ("PAID".equals(order.getStatus())) return;
    if (!"PENDING".equals(order.getStatus())) throw new IllegalStateException(
      "Trạng thái đơn hàng không hợp lệ"
    );
    User user = users
      .lockById(order.getUserId())
      .orElseThrow(CourseService::missing);
    LocalDateTime now = LocalDateTime.now();
    Payment p = new Payment();
    p.setOrderId(order.getId());
    p.setExternalRef(reference);
    p.setAmount(received);
    p.setPaidAt(now);
    payments.save(p);
    for (OrderItem i : items.findByOrderId(order.getId())) {
      if (
        !enrollments.existsByUserIdAndCourseId(user.getId(), i.getCourseId())
      ) {
        Enrollment e = new Enrollment();
        e.setUserId(user.getId());
        e.setCourseId(i.getCourseId());
        e.setOrderId(order.getId());
        e.setEnrolledAt(now);
        enrollments.save(e);
      }
    }
    order.setStatus("PAID");
    order.setPaidAt(now);
    events.publishEvent(
      new PurchaseConfirmed(user.getEmail(), user.getFullName(), order.getId())
    );
  }

  public Map<String, Object> orderView(CourseOrder o) {
    return view(
      "id",
      o.getId(),
      "userId",
      o.getUserId(),
      "username",
      users.findById(o.getUserId()).map(User::getUsername).orElse(""),
      "total",
      o.getTotal(),
      "status",
      o.getStatus(),
      "createdAt",
      o.getCreatedAt(),
      "paidAt",
      o.getPaidAt(),
      "checkoutUrl",
      o.getCheckoutUrl(),
      "items",
      items.findByOrderId(o.getId())
    );
  }

  public List<Map<String, Object>> all() {
    return orders
      .findAll(org.springframework.data.domain.Sort.by("id").descending())
      .stream()
      .map(this::orderView)
      .toList();
  }

  public Map<String, Object> stats() {
    List<CourseOrder> all = orders.findAll();
    return view(
      "orders",
      all.size(),
      "paidOrders",
      all
        .stream()
        .filter(o -> "PAID".equals(o.getStatus()))
        .count(),
      "revenue",
      all
        .stream()
        .filter(o -> "PAID".equals(o.getStatus()))
        .map(CourseOrder::getTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add),
      "students",
      users
        .findAll()
        .stream()
        .filter(u -> u.getRole() == com.example.java_basic.enums.Role.STUDENT)
        .count(),
      "enrollments",
      enrollments.count()
    );
  }

  public List<Map<String, Object>> students() {
    return users
      .findAll()
      .stream()
      .map(u ->
        view(
          "id",
          u.getId(),
          "username",
          u.getUsername(),
          "fullName",
          u.getFullName(),
          "email",
          u.getEmail(),
          "role",
          u.getRole(),
          "courses",
          enrollments.findByUserIdOrderByIdDesc(u.getId()).size()
        )
      )
      .toList();
  }
}
