package ru.projectx.datareference.controller;

import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Стек MVC для unit-теста контроллера — без контекста Spring и без базы: контроллер
 * с подменёнными сервисами и настоящий {@link ApiExceptionHandler}.
 *
 * <p>Здесь проверяется маршрутизация (путь, параметры, выбор обработчика) и то, что
 * контроллер отдаёт найденное сервисом. Имена полей JSON в snake_case здесь не
 * проверяются: standalone-сборка не берёт настроек Jackson из Boot, их закрепляют
 * API-тесты на полном контексте.
 */
final class StandaloneMvc {

    private StandaloneMvc() {
    }

    static MockMvcTester of(Object controller) {
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(new DatabaseOutages(new HibernateExceptionTranslator())))
                .build());
    }
}
