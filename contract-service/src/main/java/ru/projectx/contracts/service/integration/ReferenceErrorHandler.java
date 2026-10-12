package ru.projectx.contracts.service.integration;

import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceRejectedException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;

/**
 * Переводит ответ справочника со статусом ошибки в исключение домена. Статус 4xx с кодом справочника — окончательный
 * ответ на этот запрос: «не найдено» или отказ с кодом. Всё остальное — временная недоступность: 503 при отказе его
 * базы, другой 5xx, тело не из контракта (страница прокси), потому что верить такому ответу нельзя.
 */
final class ReferenceErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private final ReferenceErrorBodies bodies;

    ReferenceErrorHandler(ReferenceErrorBodies bodies) {
        this.bodies = bodies;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw failure(response.getStatusCode(), bodies.code(response.getBody()));
    }

    private static RuntimeException failure(HttpStatusCode status, Optional<String> code) {
        if (status.is4xxClientError()) {
            return code.<RuntimeException>map(known -> finalAnswer(status, known))
                    .orElseGet(() -> new ReferenceUnavailableException(Reason.UNEXPECTED_RESPONSE));
        }
        return new ReferenceUnavailableException(unavailableReason(status, code));
    }

    private static RuntimeException finalAnswer(HttpStatusCode status, String code) {
        if (HttpStatus.NOT_FOUND.isSameCodeAs(status)) {
            return ReferenceKind.byNotFoundCode(code)
                    .<RuntimeException>map(ReferenceNotFoundException::new)
                    .orElseGet(() -> new ReferenceRejectedException(status.value(), code));
        }
        return new ReferenceRejectedException(status.value(), code);
    }

    private static Reason unavailableReason(HttpStatusCode status, Optional<String> code) {
        boolean databaseOutage = HttpStatus.SERVICE_UNAVAILABLE.isSameCodeAs(status)
                && code.filter(SERVICE_UNAVAILABLE::equals).isPresent();
        return databaseOutage ? Reason.SERVICE_UNAVAILABLE : Reason.UNEXPECTED_RESPONSE;
    }
}
