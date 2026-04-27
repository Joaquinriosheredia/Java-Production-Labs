package com.labs.common;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests across all Java Production Labs.
 *
 * <p>Provides:
 * <ul>
 *   <li>Full Spring Boot application context with a random HTTP port</li>
 *   <li>Testcontainers lifecycle managed per test class (single container reuse)</li>
 *   <li>{@link #port} injected for REST calls against the running server</li>
 * </ul>
 *
 * <p>Extend this class and declare your containers as {@code static} fields
 * annotated with {@code @Container}. Testcontainers will start them once per
 * class and stop them after the last test completes.
 *
 * <h3>Minimal example</h3>
 * <pre>{@code
 * class OrderServiceIT extends AbstractIntegrationTest {
 *
 *     @Container
 *     static PostgreSQLContainer<?> postgres =
 *         new PostgreSQLContainer<>("postgres:16-alpine");
 *
 *     @DynamicPropertySource
 *     static void overrideDataSource(DynamicPropertyRegistry registry) {
 *         registry.add("spring.datasource.url", postgres::getJdbcUrl);
 *         registry.add("spring.datasource.username", postgres::getUsername);
 *         registry.add("spring.datasource.password", postgres::getPassword);
 *     }
 *
 *     @Test
 *     void createOrder_shouldPersist() {
 *         // HTTP calls against localhost:port
 *     }
 * }
 * }</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * The random port assigned to the embedded server for this test run.
     * Use it to build base URLs: {@code "http://localhost:" + port + "/api/..."}.
     */
    @LocalServerPort
    protected int port;

    /**
     * Convenience method to build an absolute URL against the running server.
     *
     * @param path path starting with {@code /}
     * @return full URL, e.g. {@code http://localhost:54321/api/v1/orders}
     */
    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
