package com.poc.connectivity.model;

import com.poc.connectivity.domain.ConnectivityProtocol;
import com.poc.connectivity.domain.ConnectivitySubSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectionRequest(
        @Valid
        String hostname,
        @NotNull
        @Min(1)
        @Max(65535)
        Integer port,
        @NotNull
        ConnectivitySubSystem subsystem,
        @NotNull
        ConnectivityProtocol protocol
) {
}
