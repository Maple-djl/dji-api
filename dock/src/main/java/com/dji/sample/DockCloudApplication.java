package com.dji.sample;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.dji.sample.*.dao")
@SpringBootApplication
@EnableScheduling
@ComponentScan("com.dji")
public class DockCloudApplication {

	public static void main(String[] args) {
		SpringApplication.run(DockCloudApplication.class, args);
	}

}
