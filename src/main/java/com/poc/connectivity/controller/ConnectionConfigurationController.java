package com.poc.connectivity.controller;

import com.poc.connectivity.model.ConnectionConfiguration;
import com.poc.connectivity.service.ConnectionConfigurationOperations;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * WebFlux controller for user-facing connection configuration queries.
 *
 * <p>This endpoint intentionally exposes only promoted configurations so
 * operators can decide which baseband/satellite links are available
 * for runtime connect actions.
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/configurations")
public class ConnectionConfigurationController {

    private final ConnectionConfigurationOperations configurationService;

    /**
     * Returns only configurations marked as promoted.
     *
     * <p>The persistence layer is blocking (JPA), so the lookup is executed
     * on {@code boundedElastic}.
     *
     * @return reactive stream of promoted connection configurations
     */
    @GetMapping("promoted")
    public Flux<ConnectionConfiguration> getPromotedConfigurations() {
        return configurationService.getPromotedConfigurations();
    }
}
