package com.poc.connectivity.repository;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import com.poc.connectivity.model.ConnectionConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConnectionConfigurationRepository extends JpaRepository<ConnectionConfiguration, UUID> {

    List<ConnectionConfiguration> findAllByPromoted(boolean promoted);

    List<ConnectionConfiguration> findAllByPromotedTrue();

    boolean existsByHostnameIgnoreCaseAndPortAndSubsystemAndProtocol(
            String hostname,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol
    );

    boolean existsByHostnameIgnoreCaseAndPortAndSubsystemAndProtocolAndIdNot(
            String hostname,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol,
            UUID id
    );
}
