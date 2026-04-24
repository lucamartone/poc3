package com.poc.connectivity.service;

import com.poc.connectivity.config.SocketProperties;
import com.poc.connectivity.domain.ConnectionErrorCode;
import com.poc.connectivity.exception.ConnectivityException;
import com.poc.connectivity.model.ConnectionKey;
import com.poc.connectivity.model.ConnectionRequest;
import com.poc.connectivity.model.ConnectionResponse;
import com.poc.connectivity.model.ConnectionSession;
import com.poc.connectivity.domain.ConnectionStatus;
import com.poc.connectivity.repository.ConnectionChannelCache;
import com.poc.connectivity.repository.ConnectionSessionStore;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;


/**
 * Reactive service that orchestrates the full connection lifecycle.
 *
 * <p>This component coordinates:
 * <ul>
 *   <li>Connection state persistence through {@link ConnectionSessionStore}</li>
 *   <li>Runtime channel tracking through {@link ConnectionChannelCache}</li>
 *   <li>Asynchronous socket connect via Netty {@link EventLoopGroup}</li>
 *   <li>Live status broadcasting through an in-memory reactive sink</li>
 * </ul>
 *
 * <p>Database operations use {@code boundedElastic} because the persistence layer is blocking.
 */
@Service
@AllArgsConstructor
public class ConnectivityService implements ConnectivityOperations {

    private final ConnectionSessionStore sessionRepository;
    private final ConnectionChannelCache channelCache;
    private final SocketProperties socketProperties;
    private final EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final Sinks.Many<ConnectionResponse> statusSink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Mono<ConnectionResponse> connect(ConnectionRequest request) {
        ConnectionKey key = ConnectionKey.from(request);
        return Mono.fromCallable(() -> sessionRepository.findByKey(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> existing
                        .map(session -> switch (session.getStatus()) {
                            case OPEN -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.OPEN,
                                    key,
                                    "Connection already opened"
                            ));
                            case CONNECTING -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CONNECTING,
                                    key,
                                    "Connecting"
                            ));
                            case CLOSING -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CLOSING,
                                    key,
                                    "Closing Connection"
                            ));
                            default -> restartSession(session);
                        })
                        .orElseGet(() -> createPendingSessionAndConnect(key)))
                .doOnNext(this::publishEvent);
    }

    /**
     * Idempotent disconnect command.
     *
     * <p>Behavior by current state:
     * <ul>
     *   <li>{@code CLOSED}: returns already-closed response</li>
     *   <li>{@code CLOSING}: returns closing response</li>
     *   <li>other states: transitions to closing, releases resources, and marks closed</li>
     * </ul>
     *
     * @param request connection target descriptor
     * @return reactive response describing disconnection outcome
     */
    @Override
    public Mono<ConnectionResponse> disconnect(ConnectionRequest request) {
        ConnectionKey key = ConnectionKey.from(request);
        return Mono.fromCallable(() -> sessionRepository.findByKey(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(sessionOpt -> sessionOpt
                        .map(session -> switch (session.getStatus()) {
                            case CLOSED -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CLOSED,
                                    key,
                                    "Connection already closed"
                            ));
                            case CLOSING -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CLOSING,
                                    key,
                                    "Connection is closing"
                            ));
                            default -> closeSession(session);
                        })
                        .orElseGet(() -> Mono.error(new ConnectivityException("Connection not found"))))
                .doOnNext(this::publishEvent);
    }

    /**
     * Force reconnect command.
     *
     * <p>If a session exists it is first closed; a new async connect cycle is then started.
     *
     * @param request connection target descriptor
     * @return reactive response with freshly started connect state
     */
    @Override
    public Mono<ConnectionResponse> reconnect(ConnectionRequest request) {
        ConnectionKey key = ConnectionKey.from(request);
        return Mono.fromCallable(() -> sessionRepository.findByKey(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> clearIfPresent(existing).then(createPendingSessionAndConnect(key)))
                .doOnNext(this::publishEvent);
    }

    /**
     * Retrieves the persisted status for a specific connection id.
     *
     * @param connectionId unique connection identifier
     * @return current status snapshot
     */
    @Override
    public Mono<ConnectionResponse> getStatus(UUID connectionId) {
        return Mono.fromCallable(() -> sessionRepository.findById(connectionId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(sessionOpt -> sessionOpt
                        .map(session -> Mono.just(response(
                                session.id(),
                                session.getStatus(),
                                session.key(),
                                "Connection status"
                        )))
                        .orElseGet(() -> Mono.error(new ConnectivityException("Connection not found"))));
    }

    /**
     * Streams status updates for a single connection.
     *
     * <p>The stream starts with an immediate status snapshot and then continues with:
     * <ul>
     *   <li>state change events emitted by service operations</li>
     *   <li>periodic heartbeat snapshots</li>
     * </ul>
     *
     * @param connectionId unique connection identifier
     * @return live event stream scoped to the requested id
     */
    @Override
    public Flux<ConnectionResponse> statusEvents(UUID connectionId) {
        return getStatus(connectionId)
                .flux()
                .concatWith(Flux.merge(
                        statusSink.asFlux().filter(event -> connectionId.equals(event.connectionId())),
                        heartbeatEvents(connectionId)
                ));
    }

    /**
     * Persists a new session in {@code CONNECTING} state and starts async socket connect.
     *
     * @param key logical endpoint key
     * @return immediate response indicating that the connection was started
     */
    private Mono<ConnectionResponse> createPendingSessionAndConnect(ConnectionKey key) {
        UUID connectionId = UUID.randomUUID();
        ConnectionSession pendingSession = ConnectionSession.of(connectionId, key, Instant.now());

        return Mono.fromCallable(() -> sessionRepository.save(pendingSession))
                .subscribeOn(Schedulers.boundedElastic())
                .map(saved -> {
                    launchAsyncConnect(saved);
                    return response(saved.id(), ConnectionStatus.CONNECTING, key, "Connection started");
                });
    }

    /**
     * Reuses an existing session by resetting it to {@code CONNECTING} and restarting async connect.
     *
     * @param session existing persisted session
     * @return response indicating restart was triggered
     */
    private Mono<ConnectionResponse> restartSession(ConnectionSession session) {
        return Mono.fromCallable(() -> {
                    session.markConnecting();
                    return sessionRepository.save(session);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(saved -> {
                    launchAsyncConnect(saved);
                    return response(saved.id(), ConnectionStatus.CONNECTING, saved.key(), "Connection restarted");
                });
    }

    /**
     * Starts connection attempt out-of-band so HTTP response is never blocked/fails because of Netty bootstrap setup.
     */
    private void launchAsyncConnect(ConnectionSession session) {
        Mono.fromRunnable(() -> startAsyncConnect(session))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {
                        },
                        error -> {
                            ErrorDescriptor failure = failureDescriptor(error);
                            markErrorAsync(session.id(), failure.message(), failure.code())
                                    .doOnNext(this::publishEvent)
                                    .subscribe();
                        }
                );
    }

    /**
     * Starts Netty asynchronous socket connect.
     *
     * <p>Callback actions:
     * <ul>
     *   <li>on success: cache channel and mark session {@code OPEN}</li>
     *   <li>on failure: mark session {@code ERROR}</li>
     * </ul>
     *
     * @param session persisted session to update
     */
    private void startAsyncConnect(ConnectionSession session) {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, socketProperties.connectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true);

        try {
            bootstrap.connect(session.key().ip(), session.key().port()).addListener(future -> {
                if (!future.isSuccess()) {
                    ErrorDescriptor failure = failureDescriptor(future.cause());
                    markErrorAsync(session.id(), failure.message(), failure.code())
                            .doOnNext(this::publishEvent)
                            .subscribe();
                    return;
                }

                Channel channel = ((io.netty.channel.ChannelFuture) future).channel();
                channelCache.put(session.id(), channel);
                markOpenAsync(session.id())
                        .doOnNext(this::publishEvent)
                        .subscribe();
            });
        } catch (Exception e) {
            ErrorDescriptor failure = failureDescriptor(e);
            markErrorAsync(session.id(), failure.message(), failure.code())
                    .doOnNext(this::publishEvent)
                    .subscribe();
        }
    }

    /**
     * Marks a session as {@code OPEN} and emits a status response.
     *
     * @param sessionId persisted session id
     * @return emitted response if session still exists, otherwise empty
     */
    private Mono<ConnectionResponse> markOpenAsync(UUID sessionId) {
        return Mono.fromCallable(() -> sessionRepository.findById(sessionId).map(session -> {
                    session.markOpen();
                    ConnectionSession saved = sessionRepository.save(session);
                    return response(saved.id(), saved.getStatus(), saved.key(), "Connection opened");
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    /**
     * Marks a session as {@code ERROR} with a message and emits a status response.
     *
     * @param sessionId persisted session id
     * @param message failure description
     * @return emitted response if session still exists, otherwise empty
     */
    private Mono<ConnectionResponse> markErrorAsync(UUID sessionId, String message, ConnectionErrorCode errorCode) {
        return Mono.fromCallable(() -> sessionRepository.findById(sessionId).map(session -> {
                    session.markError(message);
                    ConnectionSession saved = sessionRepository.save(session);
                    return response(saved.id(), saved.getStatus(), saved.key(), message, errorCode);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    /**
     * Closes session only if present.
     *
     * @param sessionOpt optional session
     * @return completion signal
     */
    private Mono<Void> clearIfPresent(Optional<ConnectionSession> sessionOpt) {
        return sessionOpt.map(session -> closeSession(session).then()).orElseGet(Mono::empty);
    }

    /**
     * Applies graceful close transition:
     * {@code CLOSING -> CLOSED}, closes channel, removes runtime cache entry.
     *
     * @param session session to close
     * @return final {@code CLOSED} response
     */
    private Mono<ConnectionResponse> closeSession(ConnectionSession session) {
        return Mono.fromCallable(() -> {
                    session.markClosing();
                    ConnectionSession closing = sessionRepository.save(session);
                    publishEvent(response(closing.id(), ConnectionStatus.CLOSING, closing.key(), "Connection is closing"));
                    channelCache.get(session.id()).ifPresent(this::closeChannelQuietly);
                    channelCache.remove(session.id());
                    closing.markClosed();
                    ConnectionSession closed = sessionRepository.save(closing);
                    return response(closed.id(), ConnectionStatus.CLOSED, closed.key(), "Connection closed");
                })
                .subscribeOn(Schedulers.boundedElastic())
                ;
    }

    /**
     * Creates API status DTO with current timestamp.
     */
    private ConnectionResponse response(UUID id, ConnectionStatus status, ConnectionKey key, String message) {
        return new ConnectionResponse(id, status, key, Instant.now(), message, null);
    }

    /**
     * Creates API status DTO with current timestamp and explicit error code.
     */
    private ConnectionResponse response(
            UUID id,
            ConnectionStatus status,
            ConnectionKey key,
            String message,
            ConnectionErrorCode errorCode
    ) {
        return new ConnectionResponse(id, status, key, Instant.now(), message, errorCode);
    }

    /**
     * Converts any throwable into a client-facing failure descriptor.
     *
     * <p>The descriptor contains both a readable English message and a stable
     * domain error code for client-side handling.
     */
    private ErrorDescriptor failureDescriptor(Throwable throwable) {
        if (throwable == null) {
            return new ErrorDescriptor(
                    "Unable to open socket connection to remote endpoint",
                    ConnectionErrorCode.CONNECTION_FAILED
            );
        }

        Throwable root = rootCause(throwable);
        String message = root.getMessage();
        String normalizedMessage = message == null ? "" : message.toLowerCase();

        if (root instanceof UnknownHostException) {
            return new ErrorDescriptor("Host unreachable: unknown host", ConnectionErrorCode.HOST_UNREACHABLE);
        }
        if (root instanceof NoRouteToHostException) {
            return new ErrorDescriptor("Host unreachable: no route to host", ConnectionErrorCode.HOST_UNREACHABLE);
        }
        if (root instanceof ConnectException) {
            if (normalizedMessage.contains("refused")) {
                return new ErrorDescriptor(
                        "Connection refused: port is closed or not available",
                        ConnectionErrorCode.PORT_CLOSED
                );
            }
            return new ErrorDescriptor(
                    "Connection failed: remote host or port is not reachable",
                    ConnectionErrorCode.CONNECTION_FAILED
            );
        }
        if (root instanceof TimeoutException || normalizedMessage.contains("timed out") || normalizedMessage.contains("timeout")) {
            return new ErrorDescriptor(
                    "Connection timeout: remote host did not respond in time",
                    ConnectionErrorCode.CONNECTION_TIMEOUT
            );
        }
        if (normalizedMessage.contains("unreachable")) {
            return new ErrorDescriptor("Host unreachable", ConnectionErrorCode.HOST_UNREACHABLE);
        }
        if (normalizedMessage.contains("refused")) {
            return new ErrorDescriptor(
                    "Connection refused: port is closed or not available",
                    ConnectionErrorCode.PORT_CLOSED
            );
        }
        if (normalizedMessage.contains("closed")) {
            return new ErrorDescriptor("Connection closed by remote host", ConnectionErrorCode.REMOTE_CLOSED);
        }
        if (message == null || message.isBlank()) {
            return new ErrorDescriptor(
                    "Unable to open socket connection to remote endpoint",
                    ConnectionErrorCode.CONNECTION_FAILED
            );
        }
        return new ErrorDescriptor("Socket connection error: " + message, ConnectionErrorCode.CONNECTION_FAILED);
    }

    /**
     * Structured failure output used to keep message and error code aligned.
     */
    private record ErrorDescriptor(String message, ConnectionErrorCode code) {
    }

    /**
     * Resolves the deepest cause for more accurate error classification.
     */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Publishes a status update to the in-memory multicast sink.
     */
    private void publishEvent(ConnectionResponse event) {
        statusSink.tryEmitNext(event);
    }

    /**
     * Generates periodic heartbeat events by polling current status.
     *
     * <p>When the connection no longer exists, the heartbeat stream is completed.
     */
    private Flux<ConnectionResponse> heartbeatEvents(UUID connectionId) {
        return Flux.interval(Duration.ofSeconds(15))
                .flatMap(tick -> getStatus(connectionId))
                .map(current -> response(
                        current.connectionId(),
                        current.status(),
                        current.key(),
                        "heartbeat"
                ))
                .onErrorResume(ConnectivityException.class, ex -> Flux.empty());
    }

    /**
     * Best-effort channel close; exceptions are intentionally swallowed.
     */
    private void closeChannelQuietly(Channel channel) {
        try {
            channel.close();
        } catch (Exception ignored) {
            //best effort
        }
    }

    /**
     * Gracefully releases Netty resources during application shutdown.
     */
    @PreDestroy
    void shutdownEventLoop() {
        eventLoopGroup.shutdownGracefully();
    }
}
