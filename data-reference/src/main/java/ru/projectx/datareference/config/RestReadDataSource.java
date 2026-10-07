package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Источник соединений приложения: два пула и выбор между ними ({@link RestReadRouting}).
 *
 * <p>Соединение из пула берётся не тогда, когда его попросил Hibernate, а перед первым оператором: к этому моменту
 * Spring уже отметил транзакцию как «только чтение», и маршрутизация выбирает верный пул. Транзакция без операторов
 * пул не трогает вовсе.
 *
 * <p>Пулы закрывает контекст Spring при остановке приложения: пул приложения — как бин Spring Boot, пул REST-чтений —
 * как отдельный бин ({@link RestReadPoolConfig}), раньше пула приложения.
 */
public class RestReadDataSource extends LazyConnectionDataSourceProxy {

    private final HikariDataSource application;
    private final HikariDataSource restRead;

    /**
     * Источник над двумя пулами.
     *
     * @param application пул приёма, разбора и задач по расписанию
     * @param restRead    пул REST-чтений
     */
    public RestReadDataSource(HikariDataSource application, HikariDataSource restRead) {
        super(new RestReadRouting(application, restRead));
        this.application = application;
        this.restRead = restRead;
    }

    /** Пул приёма, разбора и задач по расписанию. */
    public HikariDataSource applicationPool() {
        return application;
    }

    /** Пул REST-чтений. */
    public HikariDataSource restReadPool() {
        return restRead;
    }
}
