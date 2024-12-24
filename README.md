# Resilient SSE Stream

A Spring Boot application demonstrating Server-Sent Events (SSE) with automatic reconnection handling.

## Run Application
```
mvn clean install
mvn spring-boot:run
```

## Test with cURL
```
# Basic request
curl -N -H "Accept:text/event-stream" "http://localhost:8080/api/chat/stream?query=hello"

# Test reconnection with Last-Event-ID
curl -N -H "Accept:text/event-stream" -H "Last-Event-ID: 2" "http://localhost:8080/api/chat/stream?query=hello"
```

## Expected Output
```
data: First word 
data: Second word 
data: Third word 
data: [DONE]
```