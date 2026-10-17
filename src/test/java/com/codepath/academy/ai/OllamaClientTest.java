package com.codepath.academy.ai;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {
    HttpServer server;
    AtomicReference<String> response = new AtomicReference<>();
    AtomicReference<String> request = new AtomicReference<>();
    OllamaClient client;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new OllamaClient(new AiProperties(true,true,true,"http://127.0.0.1:"+server.getAddress().getPort(),
                "http://qdrant","","test","embed:300m","chat",3,.35,3), new AiCapacity());
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void embeddingProtocolUsesUntruncatedInputAndRejectsWrongDimensions() {
        response.set("{\"embeddings\":[[1,0,0]]}");
        assertEquals(List.of(1.,0.,0.), client.embed("rollback", true, false));
        assertTrue(request.get().contains("task: search result | query: rollback"));
        assertTrue(request.get().contains("\"truncate\":false"));
        assertTrue(request.get().contains("\"num_thread\":3"));
        response.set("{\"embeddings\":[[1,0]]}");
        assertThrows(IllegalStateException.class, () -> client.embed("text", false, false));
    }
    @Test void modelFingerprintIncludesDigestAndDoesNotAcceptMissingModel() {
        response.set("{\"models\":[{\"name\":\"embed:300m\",\"digest\":\"abc123\"}]}");
        assertEquals("embed:300m@abc123", client.identity());
        response.set("{\"models\":[]}");
        assertEquals("embed:300m@abc123", client.identity(), "Briefly cache model metadata");
    }
    @Test void truncatedResponsesNeverReachAnswerValidator() {
        response.set("{\"message\":{\"content\":\"Incomplete [S1]\"},\"done_reason\":\"length\"}");
        assertThrows(IllegalStateException.class, () -> client.chat(List.of(Map.of("role","user","content","question"))));
        response.set("{\"message\":{\"content\":\"Answer [S1]\"},\"done_reason\":\"stop\"}");
        assertEquals("Answer [S1]", client.chat(List.of(Map.of("role","user","content","question"))));
        assertTrue(request.get().contains("\"think\":false"));
    }
}
