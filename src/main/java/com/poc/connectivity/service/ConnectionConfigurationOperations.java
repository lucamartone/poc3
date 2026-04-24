package com.poc.connectivity.service;

import com.poc.connectivity.model.ConnectionConfiguration;
import com.poc.connectivity.model.ConnectionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConnectionConfigurationOperations {

    Flux<ConnectionConfiguration> getPromotedConfigurations();

    Mono<ConnectionConfiguration> getById(UUID configurationId);

    Mono<ConnectionConfiguration> getPromotedById(UUID configurationId);

    Mono<ConnectionResponse> connect(UUID configurationId);

    Mono<ConnectionResponse> disconnect(UUID configurationId);

    Mono<ConnectionResponse> reconnect(UUID configurationId);
}
