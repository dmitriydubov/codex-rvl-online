package ru.projectx.datareference.controller;

import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.datareference.controller.error.ReferenceErrorAdvice;
import ru.projectx.datareference.controller.error.RequestErrorAdvice;
import ru.projectx.datareference.controller.error.ServerFailureAdvice;
import ru.projectx.datareference.controller.request.DateParameters;
import ru.projectx.datareference.controller.request.RequestIdFilter;
import ru.projectx.datareference.controller.request.UuidParameters;
import ru.projectx.datareference.service.database.DatabaseOutages;

public final class StandaloneMvc {

    private StandaloneMvc() {
    }

    static MockMvcTester of(Object... controllers) {
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new ReferenceErrorAdvice(), new RequestErrorAdvice(), serverFailureAdvice(),
                        new UuidParameters(), new DateParameters())
                .build());
    }

    public static MockMvcTester withRequestId(Object controller, Object... errorAdvices) {
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(errorAdvices)
                .addFilters(new RequestIdFilter())
                .build());
    }

    public static Object[] errorAdvices() {
        return new Object[] {new ReferenceErrorAdvice(), new RequestErrorAdvice(), serverFailureAdvice()};
    }

    public static ServerFailureAdvice serverFailureAdvice() {
        return new ServerFailureAdvice(new DatabaseOutages(new HibernateExceptionTranslator()));
    }
}
