package ru.projectx.datareference.service.integration;

/**
 * Чем кончилась одна доставка из очереди 1С:УСК. Значение метки {@code outcome} счётчика доставок
 * ({@link OneCIngestMetrics}).
 */
enum IngestOutcome {

    /** Сообщение новое, сырьё сохранено, брокеру ушло подтверждение. */
    STORED("stored"),

    /** Сообщение с этим {@code message-id} уже принималось, брокеру ушло подтверждение. */
    DUPLICATE("duplicate"),

    /** Тело не принимает база: сообщение лежит в карантине, брокеру ушло подтверждение. */
    QUARANTINED("quarantined"),

    /** В конверте нет пригодного {@code message-id}: сообщение подтверждено и потеряно. */
    UNIDENTIFIED("unidentified"),

    /** Две доставки одного сообщения столкнулись на записи: сообщение возвращено в очередь после паузы. */
    RETURNED("returned"),

    /** База не принимает записи: сообщение осталось неподтверждённым, приём встаёт на паузу. */
    STORAGE_OUTAGE("storage_outage");

    private final String tagValue;

    IngestOutcome(String tagValue) {
        this.tagValue = tagValue;
    }

    /** Значение метки в метрике. */
    String tagValue() {
        return tagValue;
    }
}
