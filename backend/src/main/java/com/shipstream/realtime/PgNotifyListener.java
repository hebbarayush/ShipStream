package com.shipstream.realtime;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Holds one dedicated (non-pooled-away) JDBC connection per instance, issues {@code LISTEN}, and
 * forwards whatever {@link NotifyPublisher} publishes to this instance's locally-connected
 * WebSocket clients. This is what makes a location update processed on instance A reach a socket
 * open on instance B, without Redis.
 */
@Component
public class PgNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(PgNotifyListener.class);

    private final DataSource dataSource;
    private final SimpMessagingTemplate ws;
    private final ObjectMapper mapper;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "pg-notify-listener");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean running = true;

    public PgNotifyListener(DataSource dataSource, SimpMessagingTemplate ws, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.ws = ws;
        this.mapper = mapper;
    }

    // Starts after the context is fully up, not at bean construction, so it never races migrations.
    @EventListener(ContextRefreshedEvent.class)
    void start() {
        executor.submit(this::listenLoop);
    }

    @PreDestroy
    void stop() {
        running = false;
        executor.shutdownNow();
    }

    private void listenLoop() {
        while (running) {
            try (Connection raw = dataSource.getConnection()) {
                PGConnection pg = raw.unwrap(PGConnection.class);
                try (Statement st = raw.createStatement()) {
                    st.execute("LISTEN " + NotifyPublisher.CHANNEL);
                    log.info("Listening on Postgres channel '{}'", NotifyPublisher.CHANNEL);
                    while (running) {
                        // pgjdbc only surfaces queued notifications when the protocol is pumped by a
                        // round-trip, so this cheap no-op query doubles as our poll tick.
                        st.execute("SELECT 1");
                        PGNotification[] notifications = pg.getNotifications();
                        if (notifications != null) {
                            for (PGNotification n : notifications) {
                                handle(n.getParameter());
                            }
                        }
                        Thread.sleep(1000);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (SQLException e) {
                if (running) {
                    log.warn("Postgres LISTEN connection lost, reconnecting in 5s", e);
                    sleepQuietly(5000);
                }
            }
        }
    }

    private void handle(String payloadJson) {
        try {
            Map<?, ?> body = mapper.readValue(payloadJson, Map.class);
            Object trackingId = body.get("trackingId");
            if (trackingId != null) {
                ws.convertAndSend("/topic/shipments/" + trackingId, body);
            }
        } catch (Exception e) {
            log.warn("Dropping malformed notification payload", e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
