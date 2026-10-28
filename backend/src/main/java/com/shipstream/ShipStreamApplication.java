package com.shipstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShipStreamApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShipStreamApplication.class, args);
	}

}
