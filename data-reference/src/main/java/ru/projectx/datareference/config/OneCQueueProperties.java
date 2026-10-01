package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Имена очередей брокера 1С:УСК — по одной на поток, у прайсов две: пакеты и финальные
 * сообщения.
 *
 * <p>Здесь, а не константами в коде, потому что имя очереди — это <b>адрес</b>, и его
 * задаёт контур. Вывести имя из документа нельзя, только списать со стенда. Проверено
 * дорогой ценой: в ТЗ и в письме со стороны 1С:УСК очереди названы иначе, чем объявлены
 * на брокере, и расхождение не сводится к одному символу. Очередь договоров страхования
 * успела побывать здесь под именем из ТЗ, через {@code rvl_online}, пока на стенде
 * её не завели через дефис, как остальные, — и сервис на ней не стартовал.
 *
 * <p><b>Умолчаний нет намеренно.</b> Имя приходит из конфигурации контура, а не из кода.
 * Пустое значение не принимается, опечатка в имени роняет запуск: потребитель не находит
 * очередь на брокере ({@code missing-queues-fatal: true}). Неверно настроенный сервис
 * падает при развёртывании, а не через неделю, когда кто-то заметит пустой справочник.
 *
 * <p><b>Имена должны быть разными.</b> Имя очереди служит и идентификатором её точки
 * приёма, и повтор роняет старт: реестр слушателей не принимает вторую точку с тем же
 * идентификатором.
 */
@Validated
@ConfigurationProperties("projectx.onec.queues")
public record OneCQueueProperties(
        @NotBlank String policies,
        @NotBlank String insuranceContracts,
        @NotBlank String contragents,
        @NotBlank String mkb10,
        @NotBlank String lpuContracts,
        @NotBlank String programs,
        @NotBlank String appealTypes,
        @NotBlank String lpuContacts,
        @NotBlank String priceLists,
        @NotBlank String priceListsFinal,
        @NotBlank String contragentServices) {
}
