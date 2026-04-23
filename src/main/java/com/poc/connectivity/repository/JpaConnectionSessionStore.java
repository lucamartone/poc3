package com.poc.connectivity.repository;

import com.poc.connectivity.model.ConnectionKey;
import com.poc.connectivity.model.ConnectionSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaConnectionSessionStore implements ConnectionSessionStore {

    private final ConnectionSessionRepository repository;

    @Override
    public Optional<ConnectionSession> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<ConnectionSession> findByKey(ConnectionKey key) {
        return repository.findByKey(key);
    }

    @Override
    public ConnectionSession save(ConnectionSession session) {
        return repository.save(session);
    }
}
