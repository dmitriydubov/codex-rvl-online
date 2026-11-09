package ru.projectx.contracts.service.integration.ordering;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class OrderingDates {

    private static final DateTimeFormatter WITH_NUMERIC_OFFSET =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx", Locale.ROOT);

    private OrderingDates() {
    }

    static String format(Instant moment, ZoneId zone) {
        return WITH_NUMERIC_OFFSET.format(moment.truncatedTo(ChronoUnit.SECONDS).atZone(zone));
    }
}
