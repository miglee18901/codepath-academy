package com.codepath.academy.controller;

import com.codepath.academy.dto.AcademyRequests.*;
import com.codepath.academy.entity.User;
import com.codepath.academy.service.*;
import jakarta.validation.Valid;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.payos.model.webhooks.Webhook;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AcademyController {

    private final CourseService courses;
    private final OrderService orders;

    @GetMapping("/categories")
    public Object categories() {
        return courses.categories();
    }

    @GetMapping("/courses")
    public Object catalog(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long categoryId
    ) {
        return courses.catalog(q, categoryId, false);
    }

    @GetMapping("/courses/{id}")
    public Object course(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return courses.detail(id, user);
    }

    @GetMapping("/lessons/{id}")
    public Object lesson(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return courses.lesson(id, user);
    }

    @GetMapping("/me")
    public Object me(@AuthenticationPrincipal User user) {
        return CourseService.view(
                "id",
                user.getId(),
                "username",
                user.getUsername(),
                "fullName",
                user.getFullName(),
                "email",
                user.getEmail(),
                "role",
                user.getRole()
        );
    }

    @GetMapping("/me/courses")
    public Object library(@AuthenticationPrincipal User user) {
        return courses.library(user);
    }

    @PutMapping("/me/progress/{id}")
    public Object progress(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProgressInput in
    ) {
        return courses.updateProgress(id, user, in);
    }

    @PostMapping("/orders")
    public Object createOrder(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody OrderInput in
    ) {
        return orders.create(user, in.courseId());
    }

    @GetMapping("/orders")
    public Object orders(@AuthenticationPrincipal User user) {
        return orders.mine(user);
    }

    @GetMapping("/orders/{id}")
    public Object order(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return orders.get(id, user);
    }

    @PostMapping("/orders/{id}/checkout")
    public Object checkout(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return orders.checkout(id, user);
    }

    @PostMapping("/payments/webhook")
    public Object webhook(@RequestBody Webhook webhook) {
        orders.webhook(webhook);
        return Map.of("message", "success");
    }
}
