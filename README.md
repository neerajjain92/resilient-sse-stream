# Resilient SSE Stream

A Spring Boot application demonstrating Server-Sent Events (SSE) with automatic reconnection handling.

## Run Application
```
mvn clean install
mvn spring-boot:run
```

## Test with cURL
```
# Basic request with clientToken
curl -N -H "Accept:text/event-stream" "http://localhost:8080/api/chat/stream?query=hello&clientToken=test-client-123"

# Reconnection with Last-Event-ID and same clientToken
curl -N -H "Accept:text/event-stream" -H "Last-Event-ID: 2" "http://localhost:8080/api/chat/stream?query=hello&clientToken=test-client-123"
```

## Expected Output
```
data: First word 
data: Second word 
data: Third word 
data: [DONE]
```