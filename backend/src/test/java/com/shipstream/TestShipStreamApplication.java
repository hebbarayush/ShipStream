package com.shipstream;

import org.springframework.boot.SpringApplication;

public class TestShipStreamApplication {

	public static void main(String[] args) {
		SpringApplication.from(ShipStreamApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
