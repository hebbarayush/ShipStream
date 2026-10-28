package com.shipstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration excluded: it creates a random default "user" login that nothing
// in SecurityConfig uses (auth is pure JWT verification, no UserDetailsService in the chain) - without
// this it just prints a pointless generated password to the log on every startup.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class ShipStreamApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShipStreamApplication.class, args);
	}

}
