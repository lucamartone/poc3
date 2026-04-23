package com.poc.connectivity.model;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;

public record ConnectionKey(
        String ip,
        int port,
        ConnectivitySubSystem subsystem,
        ConnectivityProtocol protocol
) {
    public static ConnectionKey from(ConnectionRequest request) {
        return new ConnectionKey(
                request.hostname(),
                request.port(),
                request.subsystem(),
                request.protocol()
        );
    }
}
