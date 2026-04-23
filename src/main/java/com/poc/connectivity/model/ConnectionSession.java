package com.poc.connectivity.model;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import com.poc.connectivity.domain.ConnectionStatus;
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
        name = "connection_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_connection_session_key",
                columnNames = {"ip", "port", "subsystem", "protocol"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConnectionSession {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String ip;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectivitySubSystem subsystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectivityProtocol protocol;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private String lastError;

    public static ConnectionSession of(UUID id, ConnectionKey key, Instant createdAt) {
        Instant now = Instant.now();
        return new ConnectionSession(
                id,
                key.ip(),
                key.port(),
                key.subsystem(),
                key.protocol(),
                createdAt,
                ConnectionStatus.CONNECTING,
                now,
                null
        );
    }

    public UUID id() {
        return id;
    }

    public ConnectionKey key() {
        return new ConnectionKey(ip, port, subsystem, protocol);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void markOpen() {
        this.status = ConnectionStatus.OPEN;
        this.updatedAt = Instant.now();
        this.lastError = null;
    }

    public void markConnecting() {
        this.status = ConnectionStatus.CONNECTING;
        this.updatedAt = Instant.now();
        this.lastError = null;
    }

    public void markClosing() {
        this.status = ConnectionStatus.CLOSING;
        this.updatedAt = Instant.now();
    }

    public void markClosed() {
        this.status = ConnectionStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    public void markError(String error) {
        this.status = ConnectionStatus.ERROR;
        this.updatedAt = Instant.now();
        this.lastError = error;
    }
}
