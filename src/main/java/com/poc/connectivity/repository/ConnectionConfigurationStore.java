package com.poc.connectivity.repository;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import com.poc.connectivity.model.ConnectionConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectionConfigurationStore {

    ConnectionConfiguration create(ConnectionConfiguration configuration);

    ConnectionConfiguration update(ConnectionConfiguration configuration);

    Optional<ConnectionConfiguration> findById(UUID id);

    List<ConnectionConfiguration> findAll();

    List<ConnectionConfiguration> findByPromoted(boolean promoted);

    List<ConnectionConfiguration> findPromoted();

    boolean existsByConnectionKey(String hostname, int port, ConnectivitySubSystem subsystem, ConnectivityProtocol protocol);

    boolean existsByConnectionKeyExcludingId(
            String hostname,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol,
            UUID id
    );

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
