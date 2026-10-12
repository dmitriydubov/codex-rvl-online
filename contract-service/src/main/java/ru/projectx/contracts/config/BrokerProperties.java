package ru.projectx.contracts.config;

public interface BrokerProperties {

    BrokerConnectionProperties connection();

    boolean listenerAutoStartup();
}
