package com.stream.sse.resilient_sse_stream;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final Map<String, Queue<Message>> clientMessages = new ConcurrentHashMap<>();
    private final AtomicInteger eventIdGenerator = new AtomicInteger(0);

    @Data
    private static class Message {
        private int eventId;
        private String content;
        private long timestamp;

        public Message(int eventId, String content, long timestamp) {
            this.eventId = eventId;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String query,
                                @RequestHeader(value = "Last-Event-Id", required = false) String lastEventId,
                                HttpSession session) {
        String clientId = session.getId();
        SseEmitter emitter = new SseEmitter(-1L);

        clientMessages.putIfAbsent(clientId, new ConcurrentLinkedQueue<>());

        // Process new query
        executeLLMResponse(emitter, query, clientId);

        // Cleanup on completion
        emitter.onCompletion(() -> {
            // keep messages for some time for potential reconnection
            scheduleCleanup(clientId);
        });

        return emitter;
    }

    private void executeLLMResponse(SseEmitter emitter, String query, String clientId) {
        CompletableFuture.runAsync(() -> {
            try {
                String response = generateMockResponse(query);
                String[] words = response.split(" ");
                for (String word : words) {
                    int eventId = eventIdGenerator.incrementAndGet();
                    String content = word + " ";

                    Message message = new Message(eventId, content, System.currentTimeMillis());
                    clientMessages.get(clientId).add(message);

                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(eventId))
                            .data(content).build()
                    );
                    Thread.sleep(500);
                }

                // Send completion message
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(eventIdGenerator.incrementAndGet()))
                        .data("[DONE]")
                        .build());

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
    }

    private String generateMockResponse(String query) {
        if (query.toLowerCase().contains("hello")) {
            return "Hello! How can I assist you today?";
        } else if (query.toLowerCase().contains("weather")) {
            return "I notice you're asking about weather.";
        } else {
            return "Thank you for your question about '" + query + "'.";
        }
    }

    private void scheduleCleanup(String clientId) {

    }
}
