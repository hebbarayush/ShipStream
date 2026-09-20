package com.flowtrack;

import org.springframework.boot.SpringApplication;

public class TestFlowtrackApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlowtrackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
