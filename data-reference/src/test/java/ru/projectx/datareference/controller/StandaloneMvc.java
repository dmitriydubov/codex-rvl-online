package ru.projectx.datareference.controller;

import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.datareference.service.DatabaseOutages;

/**
 * Стек MVC для unit-теста контроллера без контекста Spring и базы: маршрутизация и то, что контроллер отдаёт
 * найденное сервисом. Имена полей в snake_case здесь не проверяются, потому что без контекста настройки Jackson
 * из Spring Boot не берутся; их проверяют API-тесты на полном контексте.
 */
final class StandaloneMvc {

    private StandaloneMvc() {
    }

    static MockMvcTester of(Object... controllers) {
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new ReferenceErrorAdvice(), new RequestErrorAdvice(), serverFailureAdvice(),
                        new UuidParameters(), new DateParameters())
                .build());
    }

    /**
     * Стек для тестов обработчиков ошибок в заданном порядке регистрации. Фильтр {@code X-Request-Id}
     * подключён, чтобы идентификатор в теле ошибки сверялся с заголовком.
     */
    static MockMvcTester withRequestId(Object controller, Object... errorAdvices) {
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(errorAdvices)
                .addFilters(new RequestIdFilter())
                .build());
    }

    /** Все обработчики ошибок в том порядке, в каком их регистрирует приложение. */
    static Object[] errorAdvices() {
        return new Object[] {new ReferenceErrorAdvice(), new RequestErrorAdvice(), serverFailureAdvice()};
    }

    static ServerFailureAdvice serverFailureAdvice() {
        return new ServerFailureAdvice(new DatabaseOutages(new HibernateExceptionTranslator()));
    }
}
