package com.poc.connectivity.service;

import com.poc.connectivity.model.ConnectionRequest;
import com.poc.connectivity.model.ConnectionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConnectivityOperations {
    Mono<ConnectionResponse> connect(ConnectionRequest request);

    Mono<ConnectionResponse> disconnect(ConnectionRequest request);

    Mono<ConnectionResponse> reconnect(ConnectionRequest request);

    Mono<ConnectionResponse> getStatus(UUID connectionId);

    Flux<ConnectionResponse> statusEvents(UUID connectionId);
}
