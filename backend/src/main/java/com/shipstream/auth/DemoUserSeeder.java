package com.shipstream.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one demo ops login on startup, idempotently, so a live demo has something to log in with
 * without anyone having to run SQL by hand. Change {@code DEMO_OPS_PASSWORD} for anything beyond a
 * local/demo deployment - this is a convenience, not a real user-management flow.
 */
@Component
class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String demoUsername;
    private final String demoPassword;

    DemoUserSeeder(UserRepository users, PasswordEncoder encoder,
            @Value("${app.security.demo-ops-username:demo}") String demoUsername,
            @Value("${app.security.demo-ops-password:demo1234}") String demoPassword) {
        this.users = users;
        this.encoder = encoder;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByUsername(demoUsername).isPresent()) {
            return;
        }
        users.save(new User(demoUsername, encoder.encode(demoPassword), "OPS"));
        log.info("Seeded demo ops user '{}' (override via DEMO_OPS_USERNAME / DEMO_OPS_PASSWORD)", demoUsername);
    }
}
