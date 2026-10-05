package ru.projectx.datareference.controller;

import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.datareference.service.DatabaseOutages;

/**
 * Стек MVC для unit-теста контроллера без контекста Spring и без базы: контроллер с подменёнными
 * сервисами и настоящие обработчики ошибок REST.
 *
 * <p>Здесь проверяется маршрутизация (путь, параметры, выбор обработчика) и то, что контроллер отдаёт
 * найденное сервисом. UUID из пути и параметров читаются тем же строгим правилом, что и в приложении:
 * его подключает {@link UuidParameters}. Имена полей JSON в snake_case здесь не проверяются, потому что
 * сборка без контекста не берёт настроек Jackson из Spring Boot. Их проверяют API-тесты на полном
 * контексте.
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
     * Стек для тестов самих обработчиков ошибок: один контроллер, обработчики в заданном тестом порядке
     * регистрации и фильтр {@code X-Request-Id}, чтобы идентификатор в теле ошибки сверялся с заголовком.
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
