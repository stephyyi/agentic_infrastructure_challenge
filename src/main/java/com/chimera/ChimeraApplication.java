package com.chimera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Project Chimera — Autonomous AI Influencer Network.
 *
 * <p>Entry point for the Spring Boot application. Virtual Threads are enabled
 * globally via application.yaml (spring.threads.virtual.enabled=true), which
 * upgrades all Tomcat request threads, @Async methods, and @Scheduled tasks
 * to Virtual Threads automatically.
 *
 * <p>See specs/_meta.md for immutable system constraints.
 */
@SpringBootApplication
@EnableScheduling
public class ChimeraApplication {

    /** Private constructor — utility class, not instantiated directly. */
    private ChimeraApplication() { }

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(ChimeraApplication.class, args);
    }
}
