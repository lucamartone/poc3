package com.poc.connectivity.model;

import com.poc.connectivity.domain.ConnectionErrorCode;
import com.poc.connectivity.domain.ConnectionStatus;

import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
        UUID connectionId,
        ConnectionStatus status,
        ConnectionKey key,
        Instant timestamp,
        String message,
        ConnectionErrorCode errorCode
) {
}
