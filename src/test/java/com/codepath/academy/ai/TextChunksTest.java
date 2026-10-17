package com.codepath.academy.ai;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TextChunksTest {
    @Test void largeVietnameseAndCodeContentHasBoundedChunksWithoutLostTail() {
        String text = ("Giao dịch cơ sở dữ liệu 🔐\n\n```java\ncommit();\n```\n\n").repeat(200) + "LAST_SENTINEL";
        var chunks = TextChunks.split(text);
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(c -> c.text().length() <= 1600 && text.contains(c.text())));
        assertTrue(chunks.get(chunks.size() - 1).text().endsWith("LAST_SENTINEL"));
        assertTrue(chunks.stream().noneMatch(c -> Character.isLowSurrogate(c.text().charAt(0))
                || Character.isHighSurrogate(c.text().charAt(c.text().length() - 1))));
    }
    @Test void hashesChangeWithContentAndBlankDocumentsAreSkipped() {
        assertNotEquals(TextChunks.hash("a"), TextChunks.hash("b"));
        assertTrue(TextChunks.split("  \n").isEmpty());
    }
}
