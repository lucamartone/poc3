package com.poc.connectivity.repository;

import com.poc.connectivity.model.ConnectionKey;
import com.poc.connectivity.model.ConnectionSession;
import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionSessionRepository extends JpaRepository<ConnectionSession, UUID> {

    Optional<ConnectionSession> findByIpAndPortAndSubsystemAndProtocol(
            String ip,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol
    );

    default Optional<ConnectionSession> findByKey(ConnectionKey key) {
        return findByIpAndPortAndSubsystemAndProtocol(
                key.ip(),
                key.port(),
                key.subsystem(),
                key.protocol()
        );
    }
}
