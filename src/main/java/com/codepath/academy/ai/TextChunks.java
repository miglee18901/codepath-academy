package com.codepath.academy.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.codepath.academy.ai.AiModels.Chunk;

public final class TextChunks {
    public static final String VERSION = "paragraph-v1-1600-200";
    private TextChunks() {}
    public static List<Chunk> split(String text) {
        return split(text, 1600, 200);
    }
    static List<Chunk> split(String input, int size, int overlap) {
        String text = Objects.toString(input, "").replace("\r\n", "\n");
        List<Chunk> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            if (end < text.length()) {
                int boundary = text.lastIndexOf("\n\n", end);
                if (boundary > start + size / 2) end = boundary;
                // Keep surrogate pairs intact.
                if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            }
            String part = text.substring(start, end);
            if (!part.isBlank()) result.add(new Chunk(result.size(), part));
            if (end == text.length()) break;
            int next = Math.max(start + 1, end - overlap);
            if (Character.isLowSurrogate(text.charAt(next))) next++;
            start = next;
        }
        return result;
    }
    public static String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
