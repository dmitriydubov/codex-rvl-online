package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Зависшая база — сервер жив для сети, но не отвечает (контейнер на паузе). Лимит REST-чтения тут не помогает:
 * отмену запроса драйвер шлёт в базу отдельным соединением, и зависшая база её не исполняет. Ответ 503 даёт
 * сетевой тайм-аут чтения пула REST-чтений (5 с), а не пула приложения (60 с).
 *
 * <p>Самый долгий путь — соединение, которым только что пользовались: пул выдаёт его без проверки. В HikariCP это
 * окно — полсекунды, и тест растягивает его системным свойством пула на всё время теста, чтобы не ловить паузу
 * в эти полсекунды. С проверкой пул отказал бы раньше — через ожидание соединения.
 *
 * <p>Контекст свой, со своим контейнером: базу на паузе не должен увидеть ни один другой тест. После теста контекст
 * закрывается.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.application.name=data-reference-hung-database")
@DirtiesContext
class HungDatabaseRestReadTest {

    /** Свойство HikariCP: сколько миллисекунд после использования соединение выдаётся без проверки. */
    private static final String ALIVE_BYPASS_WINDOW = "com.zaxxer.hikari.aliveBypassWindowMs";
    /** Сетевой тайм-аут пула REST-чтений по умолчанию (5 с) с запасом на медленную машину сборки. */
    private static final Duration SOCKET_TIMEOUT_WITH_MARGIN = Duration.ofSeconds(10);
    private static final String UNKNOWN_POLICY = "/internal/v1/policies/f662d1c4-5b26-4248-8ba1-47954abd301b";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PostgreSQLContainer database;

    @BeforeAll
    static void validateNoRecentlyUsedConnection() {
        System.setProperty(ALIVE_BYPASS_WINDOW, Long.toString(Duration.ofHours(1).toMillis()));
    }

    @AfterAll
    static void restoreValidation() {
        System.clearProperty(ALIVE_BYPASS_WINDOW);
    }

    @Test
    void hungDatabase_restReadAnswers503WithinRestSocketTimeout() {
        assertThat(mvc.get().uri(UNKNOWN_POLICY).exchange()).hasStatus(404);
        pause();
        try {
            long started = System.nanoTime();

            MvcTestResult result = mvc.get().uri(UNKNOWN_POLICY).exchange();

            assertThat(result).hasStatus(503);
            assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SOCKET_TIMEOUT_WITH_MARGIN);
        }
        finally {
            unpause();
        }
    }

    private void pause() {
        database.getDockerClient().pauseContainerCmd(database.getContainerId()).exec();
    }

    private void unpause() {
        database.getDockerClient().unpauseContainerCmd(database.getContainerId()).exec();
    }
}
