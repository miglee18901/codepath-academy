package com.codepath.academy.ai;
import java.util.*;
import com.codepath.academy.ai.AiModels.Hit;
public interface VectorStore {
    void ensureCollection();
    void upsert(List<Map<String, Object>> points);
    List<Hit> search(List<Double> vector, Map<String, Object> filter, int limit, double minScore);
    void deleteOld(long courseId, long revision);
}
