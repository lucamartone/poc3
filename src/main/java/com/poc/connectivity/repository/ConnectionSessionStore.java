package com.poc.connectivity.repository;

import com.poc.connectivity.model.ConnectionKey;
import com.poc.connectivity.model.ConnectionSession;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for connection session lifecycle state.
 *
 * <p>Service layer depends on this abstraction so storage can be swapped
 * (JPA - Redis) without touching orchestration logic.
 */
public interface ConnectionSessionStore {

    Optional<ConnectionSession> findById(UUID id);

    Optional<ConnectionSession> findByKey(ConnectionKey key);

    ConnectionSession save(ConnectionSession session);
}
