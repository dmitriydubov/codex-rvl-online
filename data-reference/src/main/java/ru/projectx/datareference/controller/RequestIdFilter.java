package ru.projectx.datareference.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Сквозной {@code X-Request-Id}: из запроса в ответ, в MDC и в тело ошибки, чтобы ошибку можно было найти в журнале.
 * Отсутствующий или недопустимый идентификатор заменяется новым UUID, а не отклоняется: отказ из-за трассировки
 * остановил бы соседям работу. Ограничение на символы и длину не пускает в журнал переводы строк и мегабайты.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(sent -> ACCEPTABLE.matcher(sent).matches())
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
