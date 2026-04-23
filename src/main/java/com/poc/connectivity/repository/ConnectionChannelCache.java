package com.poc.connectivity.repository;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConnectionChannelCache {

    private final ConcurrentMap<UUID, Channel> channels = new ConcurrentHashMap<>();

    public void put(UUID connectionId, Channel channel) {
        channels.put(connectionId, channel);
    }

    public Optional<Channel> get(UUID connectionId) {
        return Optional.ofNullable(channels.get(connectionId));
    }

    public void remove(UUID connectionId) {
        channels.remove(connectionId);
    }
}
