package ru.projectx.contracts.config;

/**
 * Настройки одного брокера, общие для всех брокеров сервиса: подключение и автостарт слушателей его очередей.
 * Фабрика контейнеров слушателей читает их через этот контракт, одинаково для каждого брокера.
 */
public interface BrokerProperties {

    BrokerConnectionProperties connection();

    /** Выключают там, где очередей этого брокера нет, например в локальном контуре; сервис при этом стартует. */
    boolean listenerAutoStartup();
}
