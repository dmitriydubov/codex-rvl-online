package ru.projectx.datareference.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code X-Request-Id} сквозняком: из запроса — в ответ, в MDC на время обработки
 * и в тело ошибки. По нему ошибку находят в журнале.
 *
 * <p>Если вызывающий заголовок не прислал, идентификатор выдаётся здесь: иначе у ошибки
 * без идентификатора нет ни следа в журнале, ни способа его найти — а вызвавший
 * не узнает даже, что забыл заголовок.
 *
 * <p>MDC чистится в {@code finally}: на виртуальных потоках запрос обрабатывается своим
 * потоком, но привычка не оставлять чужой идентификатор в контексте стоит одной строки.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(Predicate.not(String::isBlank))
                .orElseGet(() -> UUID.randomUUID().toString());

        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        }
        finally {
            MDC.remove(MDC_KEY);
        }
    }
}
