package ru.projectx.datareference.config.restread;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

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
