package com.wl4g.devops;

import com.wl4g.devops.support.config.internal.logback.LogbackLoggingSystem;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.logging.LoggingSystem;

@MapperScan("com.wl4g.devops.dao.*")
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class ShareServer {

	static {
		System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LogbackLoggingSystem.class.getName());
	}

	public static void main(String[] args) {
		SpringApplication.run(ShareServer.class, args);
	}

}