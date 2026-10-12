package ru.projectx.contracts.service.integration;

import java.time.Duration;

public interface Pauses {

    boolean hold(Duration duration);
}
