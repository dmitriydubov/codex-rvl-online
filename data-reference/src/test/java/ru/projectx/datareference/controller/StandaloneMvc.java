package ru.projectx.datareference.controller;

import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.datareference.service.DatabaseOutages;

/**
 * Стек MVC для unit-теста контроллера — без контекста Spring и без базы: контроллер
 * с подменёнными сервисами и настоящий {@link ApiExceptionHandler}.
 *
 * <p>Здесь проверяется маршрутизация (путь, параметры, выбор обработчика) и то, что
 * контроллер отдаёт найденное сервисом. UUID из пути и параметров читаются тем же строгим
 * правилом, что и в приложении: его подключает {@link UuidParameters}. Имена полей JSON в
 * snake_case здесь не проверяются: standalone-сборка не берёт настроек Jackson из Boot, их
 * закрепляют API-тесты на полном контексте.
 */
final class StandaloneMvc {

    private StandaloneMvc() {
    }

    static MockMvcTester of(Object... controllers) {
        var exceptionHandler = new ApiExceptionHandler(new DatabaseOutages(new HibernateExceptionTranslator()));
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(exceptionHandler, new UuidParameters())
                .build());
    }
}
