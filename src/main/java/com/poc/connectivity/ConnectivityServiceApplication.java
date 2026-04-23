package com.poc.connectivity;

import com.poc.connectivity.config.SocketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
@EnableConfigurationProperties(SocketProperties.class)
public class ConnectivityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectivityServiceApplication.class, args);
    }
}
