package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Делает пул, который собрал Spring Boot, пулом приложения и ставит рядом пул REST-чтений.
 *
 * <p>Пул Boot не заменяется своим, а оборачивается уже готовым: так у него остаются все настройки
 * {@code spring.datasource.*} и подключение, которое Boot берёт из окружения. Обёртка — {@link RestReadDataSource}.
 * Оборачивается только пул Boot: пул REST-чтений — тоже бин ({@link RestReadPoolConfig}), и его трогать нельзя.
 *
 * <p>Обработчик упорядочен и стоит первым: Spring применяет упорядоченные обработчики раньше остальных, и другая
 * обёртка источника соединений (например, наблюдение или стенд) оборачивает уже готовый источник с двумя пулами, а не
 * подменяет пул Boot до того, как его увидит этот обработчик.
 *
 * <p>Настройки пула REST-чтений берутся из контекста лениво, при обработке пула: обработчик бинов создаётся раньше
 * остальных бинов, а настройки к тому моменту ещё не связаны со свойствами.
 */
public class RestReadDataSourcePostProcessor implements BeanPostProcessor, Ordered {

    /** Имя бина пула, который собирает Spring Boot. */
    static final String BOOT_POOL = "dataSource";

    private final BeanFactory beans;

    public RestReadDataSourcePostProcessor(BeanFactory beans) {
        this.beans = beans;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource application && BOOT_POOL.equals(beanName)) {
            RestReadPoolProperties settings = beans.getBean(RestReadPoolProperties.class);
            return new RestReadDataSource(application, RestReadPools.restReadPool(application, settings));
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
