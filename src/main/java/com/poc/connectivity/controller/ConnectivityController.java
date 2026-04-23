package com.poc.connectivity.controller;

import com.poc.connectivity.model.ConnectionRequest;
import com.poc.connectivity.model.ConnectionResponse;
import com.poc.connectivity.service.IConnectivityService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@AllArgsConstructor
public class ConnectivityController {

    private final IConnectivityService connectivityService;

    @PostMapping("/api/v1/connectivity/connect")
    public Mono<ResponseEntity<ConnectionResponse>> connect(@Valid @RequestBody ConnectionRequest request) {
        return connectivityService.connect(request).map(ResponseEntity::ok);
    }

    @PostMapping("/api/v1/connectivity/disconnect")
    public Mono<ResponseEntity<ConnectionResponse>> disconnect(@Valid @RequestBody ConnectionRequest request) {
        return connectivityService.disconnect(request).map(ResponseEntity::ok);
    }

    @PostMapping("/api/v1/connectivity/reconnect")
    public Mono<ResponseEntity<ConnectionResponse>> reconnect(@Valid @RequestBody ConnectionRequest request) {
        return connectivityService.reconnect(request).map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/connectivity/{connectionId}")
    public Mono<ResponseEntity<ConnectionResponse>> getStatus(@PathVariable UUID connectionId) {
        return connectivityService.getStatus(connectionId).map(ResponseEntity::ok);
    }

    @GetMapping(value = "/api/v1/connectivity/{connectionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ConnectionResponse>> statusEvents(@PathVariable UUID connectionId) {
        return connectivityService.statusEvents(connectionId)
                .map(event -> ServerSentEvent.<ConnectionResponse>builder()
                        .id(event.connectionId().toString())
                        .event("connection-status")
                        .data(event)
                        .build());
    }
}
