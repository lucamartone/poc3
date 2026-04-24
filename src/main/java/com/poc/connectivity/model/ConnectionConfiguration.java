package com.poc.connectivity.model;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "connection_configuration",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_connection_configuration_key",
                columnNames = {"hostname", "port", "subsystem", "protocol"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConnectionConfiguration {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectivitySubSystem subsystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectivityProtocol protocol;

    @Column(nullable = false)
    private boolean promoted;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static ConnectionConfiguration create(
            String hostname,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol,
            boolean promoted
    ) {
        Instant now = Instant.now();
        return new ConnectionConfiguration(
                UUID.randomUUID(),
                normalizeHostname(hostname),
                port,
                subsystem,
                protocol,
                promoted,
                now,
                now
        );
    }

    public void update(
            String hostname,
            int port,
            ConnectivitySubSystem subsystem,
            ConnectivityProtocol protocol,
            boolean promoted
    ) {
        this.hostname = normalizeHostname(hostname);
        this.port = port;
        this.subsystem = subsystem;
        this.protocol = protocol;
        this.promoted = promoted;
        this.updatedAt = Instant.now();
    }

    public ConnectionRequest toConnectionRequest() {
        return new ConnectionRequest(hostname, port, subsystem, protocol);
    }

    private static String normalizeHostname(String hostname) {
        return hostname == null ? null : hostname.trim().toLowerCase();
    }
}
