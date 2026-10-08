package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Источник соединений приложения из двух пулов и выбора между ними ({@link RestReadRouting}). Соединение берётся только
 * перед первым оператором, когда транзакция уже отмечена «только чтение», поэтому пул выбирается верно. Пулы закрывает
 * контекст Spring: пул приложения как бин Boot, пул REST-чтений как бин из {@link RestReadPoolConfig}.
 */
public class RestReadDataSource extends LazyConnectionDataSourceProxy {

    private final HikariDataSource application;
    private final HikariDataSource restRead;

    public RestReadDataSource(HikariDataSource application, HikariDataSource restRead) {
        super(new RestReadRouting(application, restRead));
        this.application = application;
        this.restRead = restRead;
    }

    public HikariDataSource applicationPool() {
        return application;
    }

    public HikariDataSource restReadPool() {
        return restRead;
    }
}
