package ru.projectx.contracts.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.BooleanSupplier;

public final class FlagGauges {

    private FlagGauges() {
    }

    public static void register(MeterRegistry registry, String name, String description, BooleanSupplier flag) {
        Gauge.builder(name, flag, FlagGauges::asNumber)
                .strongReference(true)
                .description(description)
                .register(registry);
    }

    private static double asNumber(BooleanSupplier flag) {
        return flag.getAsBoolean() ? 1 : 0;
    }
}
