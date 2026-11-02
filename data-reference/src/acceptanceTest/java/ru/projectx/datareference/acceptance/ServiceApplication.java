package ru.projectx.datareference.acceptance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import ru.projectx.datareference.DataReferenceApplication;

final class ServiceApplication {

    private static final String WITHOUT_ACCEPTANCE_STAND = "withoutAcceptanceStand";

    private ServiceApplication() {
    }

    static SpringApplication withoutAcceptanceStand() {
        SpringApplication application = new SpringApplicationBuilder(DataReferenceApplication.class)
                .initializers(context -> context.getBeanFactory()
                        .registerSingleton(WITHOUT_ACCEPTANCE_STAND, new WithoutAcceptanceStand()))
                .application();
        application.setListeners(application.getListeners().stream()
                .filter(listener -> !(listener instanceof LoggingApplicationListener))
                .toList());
        return application;
    }

    private static final class WithoutAcceptanceStand extends TypeExcludeFilter {

        private static final String STAND_PACKAGE = ServiceApplication.class.getPackageName() + ".";

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
            return metadataReader.getClassMetadata().getClassName().startsWith(STAND_PACKAGE);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof WithoutAcceptanceStand;
        }

        @Override
        public int hashCode() {
            return WithoutAcceptanceStand.class.hashCode();
        }
    }
}
