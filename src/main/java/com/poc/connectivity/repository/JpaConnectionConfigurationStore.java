package com.poc.connectivity.repository;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import com.poc.connectivity.model.ConnectionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaConnectionConfigurationStore implements ConnectionConfigurationStore {

    private final ConnectionConfigurationRepository repository;

    @Override
    public ConnectionConfiguration create(ConnectionConfiguration configuration) {
        return repository.save(configuration);
    }

    @Override
    public ConnectionConfiguration update(ConnectionConfiguration configuration) {
        return repository.save(configuration);
    }

    @Override
    public Optional<ConnectionConfiguration> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<ConnectionConfiguration> findAll() {
        return repository.findAll();
    }

    @Override
    public List<ConnectionConfiguration> findByPromoted(boolean promoted) {
        return repository.findAllByPromoted(promoted);
    }

    @Override
    public List<ConnectionConfiguration> findPromoted() {
        return repository.findAllByPromotedTrue();
    }

    @Override
    public boolean existsByConnectionKey(String hostname, int port, ConnectivitySubSystem subsystem, ConnectivityProtocol protocol) {
        return repository.existsByHostnameIgnoreCaseAndPortAndSubsystemAndProtocol(hostname, port, subsystem, protocol);
    }

    @Override
    public boolean existsByConnectionKeyExcludingId(
            String hostname,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol,
            UUID id
    ) {
        return repository.existsByHostnameIgnoreCaseAndPortAndSubsystemAndProtocolAndIdNot(
                hostname,
                port,
                subsystem,
                protocol,
                id
        );
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
