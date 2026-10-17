package com.codepath.academy.ai;
import java.util.List;
public interface EmbeddingProvider {
    List<Double> embed(String text, boolean query, boolean background);
    String identity();
}
