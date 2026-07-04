package com.logitrust;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LogitrustApplication {

	public static void main(String[] args) {
		SpringApplication.run(LogitrustApplication.class, args);
	}

}
