package com.codepath.academy.ai;
import java.util.List;
import java.util.Map;
public interface ChatProvider {
    String chat(List<Map<String, String>> messages);
}
