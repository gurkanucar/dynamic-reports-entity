package com.gucardev.dynamicreportsentity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class DynamicReportsEntityApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicReportsEntityApplication.class, args);
    }

    /** "Now" for every module (and for relative date filters in datasets), so tests can pin it. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
