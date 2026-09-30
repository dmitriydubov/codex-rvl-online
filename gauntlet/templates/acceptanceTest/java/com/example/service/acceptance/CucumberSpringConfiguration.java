package com.example.service.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * One Spring context for all scenarios (cucumber-spring). Put step definitions in this package
 * (acceptance.glue in gauntlet/config/gauntlet.properties). Add Testcontainers here if the service
 * needs a real database or broker, and clean state in a @Before hook so scenarios stay independent.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
}
