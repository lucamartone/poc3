package com.poc.connectivity.service;

import com.poc.connectivity.exception.ConnectivityException;
import com.poc.connectivity.model.ConnectionConfiguration;
import com.poc.connectivity.model.ConnectionResponse;
import com.poc.connectivity.repository.ConnectionConfigurationStore;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;


/**
 * Service dedicated to connection configuration access and validation.
 *
 * <p>This component centralizes configuration-oriented policies used by
 * runtime connectivity flows, such as promoted-only checks for user-triggered
 * connect commands.
 *
 * <p>Because persistence is JPA-based and blocking, repository calls are
 * executed on {@code boundedElastic}.
 */
@Service
@AllArgsConstructor
public class ConnectionConfigurationService implements ConnectionConfigurationOperations {

    private final ConnectionConfigurationStore configurationStore;
    private final ConnectivityOperations connectivityService;

    /**
     * Retrieves only configurations flagged as promoted.
     *
     * @return reactive stream of promoted configurations
     */
    @Override
    public Flux<ConnectionConfiguration> getPromotedConfigurations() {
        return Flux.defer(() -> Flux.fromIterable(configurationStore.findPromoted()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Retrieves a configuration by id.
     *
     * @param configurationId configuration identifier
     * @return configuration if found, otherwise an error
     */
    @Override
    public Mono<ConnectionConfiguration> getById(UUID configurationId) {
        return Mono.fromCallable(() -> configurationStore.findById(configurationId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(configurationOpt -> configurationOpt
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ConnectivityException("Connection configuration not found"))));
    }

    /**
     * Retrieves a configuration by id and ensures it is promoted.
     *
     * @param configurationId configuration identifier
     * @return promoted configuration if available, otherwise an error
     */
    @Override
    public Mono<ConnectionConfiguration> getPromotedById(UUID configurationId) {
        return getById(configurationId)
                .flatMap(configuration -> configuration.isPromoted()
                        ? Mono.just(configuration)
                        : Mono.error(new ConnectivityException("Configuration is not available for user connection")));
    }

    /**
     * Connects using a promoted configuration id.
     *
     * @param configurationId configuration identifier
     * @return connectivity response for the connect command
     */
    @Override
    public Mono<ConnectionResponse> connect(UUID configurationId) {
        return getPromotedById(configurationId)
                .flatMap(configuration -> connectivityService.connect(configuration.toConnectionRequest()));
    }

    /**
     * Disconnects using a configuration id.
     *
     * @param configurationId configuration identifier
     * @return connectivity response for the disconnect command
     */
    @Override
    public Mono<ConnectionResponse> disconnect(UUID configurationId) {
        return getById(configurationId)
                .flatMap(configuration -> connectivityService.disconnect(configuration.toConnectionRequest()));
    }

    /**
     * Reconnects using a configuration id.
     *
     * @param configurationId configuration identifier
     * @return connectivity response for the reconnect command
     */
    @Override
    public Mono<ConnectionResponse> reconnect(UUID configurationId) {
        return getById(configurationId)
                .flatMap(configuration -> connectivityService.reconnect(configuration.toConnectionRequest()));
    }
}
