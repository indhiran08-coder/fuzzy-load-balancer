package com.fuzzybalancer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FuzzyLoadBalancerApplication — Main entry point of the Spring Boot application.
 *
 * Annotations explained:
 *
 * @SpringBootApplication — A convenience annotation that combines:
 *   - @Configuration      : Marks this class as a source of bean definitions.
 *   - @EnableAutoConfiguration : Tells Spring Boot to start auto-configuring beans
 *                                based on the classpath (e.g., DataSource if JPA is present).
 *   - @ComponentScan      : Scans the current package and all sub-packages for
 *                           Spring-managed components (@Service, @Repository, @Controller, etc.)
 *
 * @EnableScheduling — Activates Spring's scheduled task execution capability.
 *   Required for the Simulation Module's @Scheduled methods that periodically
 *   update server metrics to simulate real-world load fluctuations.
 *
 * Why Java 21?
 *   Java 21 is the latest LTS version. It provides:
 *   - Virtual Threads (Project Loom) for high-throughput I/O
 *   - Pattern Matching for switch
 *   - Records for immutable data carriers (used in some DTOs)
 *   - Sequenced Collections
 */
@SpringBootApplication
@EnableScheduling
public class FuzzyLoadBalancerApplication {

    /**
     * main() — Standard Java entry point.
     *
     * SpringApplication.run() bootstraps the Spring IoC container:
     *   1. Creates ApplicationContext
     *   2. Registers all beans (Components, Services, Repositories, etc.)
     *   3. Auto-configures DataSource, Security, JPA, Tomcat, etc.
     *   4. Starts the embedded Tomcat web server on port 8080 (default)
     *
     * @param args Command-line arguments (can be used to override properties,
     *             e.g., --server.port=9090)
     */
    public static void main(String[] args) {
        SpringApplication.run(FuzzyLoadBalancerApplication.class, args);
    }
}
