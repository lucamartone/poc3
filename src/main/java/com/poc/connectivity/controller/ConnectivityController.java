package com.poc.connectivity.controller;

import com.poc.connectivity.model.ConnectionResponse;
import com.poc.connectivity.service.ConnectionConfigurationOperations;
import com.poc.connectivity.service.ConnectivityOperations;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/connectivity")
public class ConnectivityController {

    private final ConnectivityOperations connectivityService;
    private final ConnectionConfigurationOperations configurationService;

    @PostMapping("connect/{id}")
    public Mono<ResponseEntity<ConnectionResponse>> connect(@PathVariable("id") UUID configurationId) {
        return configurationService.connect(configurationId).map(ResponseEntity::ok);
    }

    @PostMapping("disconnect/{id}")
    public Mono<ResponseEntity<ConnectionResponse>> disconnect(@PathVariable("id") UUID configurationId) {
        return configurationService.disconnect(configurationId).map(ResponseEntity::ok);
    }

    @PostMapping("reconnect/{id}")
    public Mono<ResponseEntity<ConnectionResponse>> reconnect(@PathVariable("id") UUID configurationId) {
        return configurationService.reconnect(configurationId).map(ResponseEntity::ok);
    }

    @GetMapping("{connectionId}")
    public Mono<ResponseEntity<ConnectionResponse>> getStatus(@PathVariable("connectionId") UUID connectionId) {
        return connectivityService.getStatus(connectionId).map(ResponseEntity::ok);
    }

    @GetMapping(value = "{connectionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ConnectionResponse>> statusEvents(@PathVariable("connectionId") UUID connectionId) {
        return connectivityService.statusEvents(connectionId)
                .map(event -> ServerSentEvent.<ConnectionResponse>builder()
                        .id(event.connectionId().toString())
                        .event("connection-status")
                        .data(event)
                        .build());
    }
}
