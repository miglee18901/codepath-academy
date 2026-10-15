package com.codepath.academy.controller;

import com.codepath.academy.dto.AcademyRequests.*;
import com.codepath.academy.entity.User;
import com.codepath.academy.service.*;
import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CourseService courses;
    private final OrderService orders;

    @GetMapping("/courses")
    public Object catalog(@RequestParam(required = false) Integer page,
                          @RequestParam(defaultValue = "12") int size) {
        if (page != null) return courses.catalogPage("", null, true, page, size);
        return courses.catalog("", null, true);
    }

    @PostMapping("/courses")
    public Object create(
            @RequestBody @Valid CourseInput in,
            @AuthenticationPrincipal User user
    ) {
        return courses.saveCourse(null, in, user);
    }

    @PutMapping("/courses/{id}")
    public Object update(
            @PathVariable Long id,
            @RequestBody @Valid CourseInput in,
            @AuthenticationPrincipal User user
    ) {
        return courses.saveCourse(id, in, user);
    }

    @PostMapping("/categories")
    public Object category(@RequestBody @Valid CategoryInput in) {
        return courses.saveCategory(null, in);
    }

    @PutMapping("/categories/{id}")
    public Object category(
            @PathVariable Long id,
            @RequestBody @Valid CategoryInput in
    ) {
        return courses.saveCategory(id, in);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        courses.deleteCategory(id);
    }

    @PostMapping("/courses/{courseId}/sections")
    public Object section(
            @PathVariable Long courseId,
            @RequestBody @Valid SectionInput in
    ) {
        return courses.saveSection(courseId, null, in);
    }

    @PutMapping("/courses/{courseId}/sections/{id}")
    public Object section(
            @PathVariable Long courseId,
            @PathVariable Long id,
            @RequestBody @Valid SectionInput in
    ) {
        return courses.saveSection(courseId, id, in);
    }

    @DeleteMapping("/sections/{id}")
    public void deleteSection(@PathVariable Long id) {
        courses.deleteSection(id);
    }

    @PostMapping("/sections/{sectionId}/lessons")
    public Object lesson(
            @PathVariable Long sectionId,
            @RequestBody @Valid LessonInput in
    ) {
        return courses.saveLesson(sectionId, null, in);
    }

    @PutMapping("/sections/{sectionId}/lessons/{id}")
    public Object lesson(
            @PathVariable Long sectionId,
            @PathVariable Long id,
            @RequestBody @Valid LessonInput in
    ) {
        return courses.saveLesson(sectionId, id, in);
    }

    @DeleteMapping("/lessons/{id}")
    public void deleteLesson(@PathVariable Long id) {
        courses.deleteLesson(id);
    }

    @GetMapping("/orders")
    public Object allOrders() {
        return orders.all();
    }

    @GetMapping("/students")
    public Object students() {
        return orders.students();
    }

    @GetMapping("/stats")
    public Object stats() {
        return orders.stats();
    }

    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> export() throws java.io.IOException {
        try (
                XSSFWorkbook book = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            var sheet = book.createSheet("Orders");
            var header = sheet.createRow(0);
            String[] cols = {
                    "Order",
                    "Student",
                    "Total VND",
                    "Status",
                    "Created",
                    "Paid",
            };
            for (int i = 0; i < cols.length; i++)
                header
                        .createCell(i)
                        .setCellValue(cols[i]);
            int row = 1;
            for (var o : orders.all()) {
                var r = sheet.createRow(row++);
                r.createCell(0).setCellValue(o.get("id").toString());
                r.createCell(1).setCellValue(o.get("username").toString());
                r.createCell(2).setCellValue(
                        ((java.math.BigDecimal) o.get("total")).doubleValue()
                );
                r.createCell(3).setCellValue(o.get("status").toString());
                r.createCell(4).setCellValue(o.get("createdAt").toString());
                r.createCell(5).setCellValue(Objects.toString(o.get("paidAt"), ""));
            }
            for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 6500);
            book.write(out);
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=orders.xlsx"
                    )
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                    )
                    .body(out.toByteArray());
        }
    }
}
