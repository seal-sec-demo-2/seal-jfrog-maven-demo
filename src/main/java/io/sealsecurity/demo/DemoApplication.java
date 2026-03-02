package io.sealsecurity.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Seal Security JFrog Maven Demo Application
 *
 * Demonstrates Seal Security remediating open-source vulnerabilities
 * when artifacts are served through JFrog Artifactory as a proxy.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  JFrog + Seal Security Demo Started!");
        System.out.println("==============================================");
    }
}
