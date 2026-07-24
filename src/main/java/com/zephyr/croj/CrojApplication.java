package com.zephyr.croj;

import com.zephyr.croj.bootstrap.AdminBootstrapCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CrojApplication {

    public static void main(String[] args) {
        if (AdminBootstrapCommand.isRequested(System.getenv("CROJ_MODE"))) {
            System.exit(AdminBootstrapCommand.runFromEnvironment());
        }
        SpringApplication.run(CrojApplication.class, args);
    }

}
