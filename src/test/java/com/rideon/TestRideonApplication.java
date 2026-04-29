package com.rideon;

import org.springframework.boot.SpringApplication;

public class TestRideonApplication {

	public static void main(String[] args) {
		SpringApplication.from(RideonApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
