package ru.projectx.contracts.service.integration.listener;

import java.time.Duration;

public interface Pauses {

    boolean hold(Duration duration);
}
