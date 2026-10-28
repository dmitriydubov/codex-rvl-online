package ru.projectx.contracts.config.broker;

public interface BrokerProperties {

    BrokerConnectionProperties connection();

    boolean listenerAutoStartup();
}
