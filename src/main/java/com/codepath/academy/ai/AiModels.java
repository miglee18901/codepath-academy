package com.codepath.academy.ai;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class AiModels {
    private AiModels() {}
    public record Message(@Pattern(regexp = "user|assistant") String role,
                          @NotBlank @Size(max = 4000) String content) {}
    public record Question(@NotBlank @Size(max = 2000) String question,
                           @NotNull @Size(max = 6) List<@NotNull @Valid Message> history) {}
    public record Citation(String id, String title, String url, String excerpt) {}
    public record Answer(String status, String answer, List<Citation> sources) {}
    public record Document(long courseId, String type, long sourceId, String title, String text,
                           boolean published, boolean preview, long categoryId) {}
    public record Chunk(int index, String text) {}
    public record Hit(double score, long courseId, String type, long sourceId, int chunk,
                      long revision, String hash, String text, String model, String chunker) {}
    public record Evidence(String id, Document document, Chunk chunk) {}
}
