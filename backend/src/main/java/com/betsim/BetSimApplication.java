package com.betsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BetSimApplication {
    public static void main(String[] args) {
        SpringApplication.run(BetSimApplication.class, args);
    }
}
