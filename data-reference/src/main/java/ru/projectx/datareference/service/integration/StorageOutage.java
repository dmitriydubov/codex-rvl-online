package ru.projectx.datareference.service.integration;

/**
 * Временный отказ хранилища, поставивший приём из 1С:УСК на паузу: код SQLSTATE (или «нет», если отказ не из базы) и
 * имя класса исключения. Текста ошибки нет: он печатает параметры запроса, а этим путём идут полисы.
 */
public record StorageOutage(String sqlState, String failureType) {
}
