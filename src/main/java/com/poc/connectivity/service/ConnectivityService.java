package com.poc.connectivity.service;

import com.poc.connectivity.config.SocketProperties;
import com.poc.connectivity.exception.ConnectivityException;
import com.poc.connectivity.model.ConnectionKey;
import com.poc.connectivity.model.ConnectionRequest;
import com.poc.connectivity.model.ConnectionResponse;
import com.poc.connectivity.model.ConnectionSession;
import com.poc.connectivity.domain.ConnectionStatus;
import com.poc.connectivity.repository.ConnectionChannelCache;
import com.poc.connectivity.repository.ConnectionSessionRepository;
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
import java.util.Optional;
import java.util.UUID;


/**
 * Reactive service that orchestrates the full connection lifecycle.
 *
 * <p>This component coordinates:
 * <ul>
 *   <li>Connection state persistence through {@link ConnectionSessionRepository}</li>
 *   <li>Runtime channel tracking through {@link ConnectionChannelCache}</li>
 *   <li>Asynchronous socket connect via Netty {@link EventLoopGroup}</li>
 *   <li>Live status broadcasting through an in-memory reactive sink</li>
 * </ul>
 *
 * <p>Database operations use {@code boundedElastic} because the persistence layer is blocking.
 */
@Service
@AllArgsConstructor
public class ConnectivityService implements IConnectivityService {

    private final ConnectionSessionRepository sessionRepository;
    private final ConnectionChannelCache channelCache;
    private final SocketProperties socketProperties;
    private final EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final Sinks.Many<ConnectionResponse> statusSink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Idempotent connect command.
     *
     * <p>Behavior by current state:
     * <ul>
     *   <li>{@code OPEN}: returns already-active response</li>
     *   <li>{@code CONNECTING}: returns in-progress response</li>
     *   <li>{@code CLOSING}: returns closing response</li>
     *   <li>other states: restarts the connection asynchronously</li>
     * </ul>
     *
     * @param request connection target descriptor
     * @return reactive response with current or newly triggered state
     */
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
                                    "Connessione gia attiva"
                            ));
                            case CONNECTING -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CONNECTING,
                                    key,
                                    "Connessione in corso"
                            ));
                            case CLOSING -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CLOSING,
                                    key,
                                    "Connessione in chiusura"
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
                                    "Connessione gia chiusa"
                            ));
                            case CLOSING -> Mono.just(response(
                                    session.id(),
                                    ConnectionStatus.CLOSING,
                                    key,
                                    "Connessione in chiusura"
                            ));
                            default -> closeSession(session);
                        })
                        .orElseGet(() -> Mono.error(new ConnectivityException("Connessione non trovata"))))
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
                                "Stato connessione"
                        )))
                        .orElseGet(() -> Mono.error(new ConnectivityException("Connessione non trovata"))));
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
                    return response(saved.id(), ConnectionStatus.CONNECTING, key, "Connessione avviata");
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
                    return response(saved.id(), ConnectionStatus.CONNECTING, saved.key(), "Connessione riavviata");
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
                        error -> markErrorAsync(session.id(), failureMessage(error))
                                .doOnNext(this::publishEvent)
                                .subscribe()
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
                    markErrorAsync(session.id(), failureMessage(future.cause()))
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
            markErrorAsync(session.id(), failureMessage(e))
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
                    return response(saved.id(), saved.getStatus(), saved.key(), "Connessione aperta");
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
    private Mono<ConnectionResponse> markErrorAsync(UUID sessionId, String message) {
        return Mono.fromCallable(() -> sessionRepository.findById(sessionId).map(session -> {
                    session.markError(message);
                    ConnectionSession saved = sessionRepository.save(session);
                    return response(saved.id(), saved.getStatus(), saved.key(), message);
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
                    publishEvent(response(closing.id(), ConnectionStatus.CLOSING, closing.key(), "Connessione in chiusura"));
                    channelCache.get(session.id()).ifPresent(this::closeChannelQuietly);
                    channelCache.remove(session.id());
                    closing.markClosed();
                    ConnectionSession closed = sessionRepository.save(closing);
                    return response(closed.id(), ConnectionStatus.CLOSED, closed.key(), "Connessione chiusa");
                })
                .subscribeOn(Schedulers.boundedElastic())
                ;
    }

    /**
     * Creates API status DTO with current timestamp.
     */
    private ConnectionResponse response(UUID id, ConnectionStatus status, ConnectionKey key, String message) {
        return new ConnectionResponse(id, status, key, Instant.now(), message);
    }

    /**
     * Converts any throwable into a client-facing failure message.
     */
    private String failureMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Errore apertura socket verso endpoint remoto";
        }
        return throwable.getMessage();
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
