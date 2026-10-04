package ru.projectx.datareference.service.integration;

/**
 * Временный отказ хранилища, из-за которого приём из 1С:УСК поставлен на паузу: что о нём можно сказать
 * без персональных данных — код SQLSTATE и тип исключения. Текста ошибки здесь нет: ошибка вставки
 * печатает параметры запроса, а этим путём идут полисы.
 *
 * @param sqlState    код SQLSTATE отказа или слово «нет», если отказ пришёл не из базы
 * @param failureType полное имя класса исключения
 */
public record StorageOutage(String sqlState, String failureType) {
}
