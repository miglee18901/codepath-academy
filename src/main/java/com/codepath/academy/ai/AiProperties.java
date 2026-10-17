package com.codepath.academy.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai")
public record AiProperties(boolean searchEnabled, boolean assistantEnabled, boolean indexingEnabled,
        String ollamaUrl, String qdrantUrl, String qdrantApiKey, String collection,
        String embeddingModel, String chatModel, int dimensions, double minScore, int numThreads) {}
