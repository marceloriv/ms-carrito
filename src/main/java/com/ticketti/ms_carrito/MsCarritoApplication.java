package com.ticketti.ms_carrito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableDiscoveryClient
public class MsCarritoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsCarritoApplication.class, args);
	}

}
