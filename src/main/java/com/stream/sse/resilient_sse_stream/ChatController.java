package com.stream.sse.resilient_sse_stream;

import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.Getter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
    @Getter
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
                                @RequestParam String clientToken,
                                @RequestHeader(value = "Last-Event-Id", required = false) String lastEventId,
                                HttpSession session) {
        SseEmitter emitter = new SseEmitter(-1L);

        clientMessages.putIfAbsent(clientToken, new ConcurrentLinkedQueue<>());

        if (lastEventId != null) {
            // Client wants to resume
            handleReconnection(emitter, clientToken, Integer.parseInt(lastEventId));
        } else {
            // Process new query
            executeLLMResponse(emitter, query, clientToken);
        }

        // Cleanup on completion
        emitter.onCompletion(() -> {
            // keep messages for some time for potential reconnection
            scheduleCleanup(clientToken);
        });

        return emitter;
    }

    private void handleReconnection(SseEmitter emitter, String clientId, int lastReceivedEventId) {
        Queue<Message> messages = clientMessages.get(clientId);
        if (messages != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    messages.stream()
                            // Filter all messages received when client was offline
                            .filter(msg -> msg.getEventId() > lastReceivedEventId)
                            .forEach(msg -> {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .id(String.valueOf(msg.getEventId()))
                                            .data(msg.getContent())
                                            .build());
                                } catch (IOException ex) {
                                    emitter.completeWithError(ex);
                                }
                            });
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            });
        }

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
