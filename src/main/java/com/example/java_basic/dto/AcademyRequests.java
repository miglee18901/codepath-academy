package com.example.java_basic.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class AcademyRequests {

  private AcademyRequests() {}

  public record CategoryInput(@NotBlank @Size(max = 100) String name) {}

  public record CourseInput(
    @NotNull Long categoryId,
    @NotBlank @Size(max = 160) String title,
    @NotBlank @Size(max = 100000) String description,
    @NotNull
    @DecimalMin("0")
    @DecimalMax("999999999")
    @Digits(integer = 9, fraction = 0)
    BigDecimal price,
    @Size(max = 1000) String thumbnailUrl,
    @NotBlank @Size(max = 100) String instructor,
    @NotBlank @Size(max = 30) String level,
    boolean published
  ) {}

  public record SectionInput(
    @NotBlank @Size(max = 160) String title,
    @Min(0) int position
  ) {}

  public record LessonInput(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Size(max = 1000000) String content,
    @Size(max = 1000) String videoUrl,
    @Min(0) @Max(10000) int durationMinutes,
    @Min(0) int position,
    boolean preview
  ) {}

  public record OrderInput(@NotNull @Positive Long courseId) {}

  public record ProgressInput(boolean completed) {}
}
