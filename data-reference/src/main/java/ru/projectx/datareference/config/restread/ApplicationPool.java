package ru.projectx.datareference.config.restread;

import com.zaxxer.hikari.HikariDataSource;

public record ApplicationPool(HikariDataSource pool) {
}
