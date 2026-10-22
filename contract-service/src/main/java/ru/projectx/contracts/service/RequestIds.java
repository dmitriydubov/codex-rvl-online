package ru.projectx.contracts.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIds() {
    }

    public static String current() {
        return received(MDC.get(MDC_KEY));
    }

    public static String received(@Nullable String header) {
        return Optional.ofNullable(header)
                .filter(RequestIds::acceptable)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    public static boolean acceptable(String requestId) {
        return ACCEPTABLE.matcher(requestId).matches();
    }
}
