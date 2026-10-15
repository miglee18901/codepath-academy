package com.codepath.academy.exception;

import java.util.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                Map.of("message", "Thông tin không hợp lệ", "fields", errors)
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(
                Map.of("message", Objects.toString(ex.getReason(), "Yêu cầu thất bại"))
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(
                Map.of("message", "Bạn chưa có quyền truy cập nội dung này")
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<?> credentials() {
        return ResponseEntity.status(401).body(
                Map.of("message", "Sai tên đăng nhập hoặc mật khẩu")
        );
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
    })
    ResponseEntity<?> invalid(RuntimeException ex) {
        return ResponseEntity.badRequest().body(
                Map.of(
                        "message",
                        Objects.toString(ex.getMessage(), "Yêu cầu không hợp lệ")
                )
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(
                Map.of("message", "Dữ liệu trùng hoặc đang được sử dụng")
        );
    }
}
