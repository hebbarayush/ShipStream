package com.shipstream.realtime;

import java.sql.PreparedStatement;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes via Postgres {@code pg_notify}, the fan-out mechanism that lets every app instance's
 * {@link PgNotifyListener} learn about an event, not just the instance that processed it from the
 * outbox. This is what Redis pub/sub would otherwise be used for; Postgres already gives it to us.
 */
@Component
public class NotifyPublisher {

    static final String CHANNEL = "shipment_updates";

    private final JdbcTemplate jdbc;

    public NotifyPublisher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void publish(String payloadJson) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT pg_notify(?, ?)")) {
                ps.setString(1, CHANNEL);
                ps.setString(2, payloadJson);
                ps.execute();
            }
            return null;
        });
    }
}
