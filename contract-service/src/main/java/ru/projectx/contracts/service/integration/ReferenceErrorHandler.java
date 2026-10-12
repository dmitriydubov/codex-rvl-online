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
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;

/**
 * Переводит ответ справочника со статусом ошибки в исключение домена. Окончательный ответ только один: 404 с кодом
 * справочника. 503 с кодом — временная недоступность; любой другой статус или тело не из контракта — тоже
 * недоступность, потому что верить такому ответу нельзя.
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

    static RuntimeException failure(HttpStatusCode status, Optional<String> code) {
        if (HttpStatus.NOT_FOUND.isSameCodeAs(status)) {
            return notFound(code);
        }
        return new ReferenceUnavailableException(unavailableReason(status, code));
    }

    private static Reason unavailableReason(HttpStatusCode status, Optional<String> code) {
        boolean databaseOutage = HttpStatus.SERVICE_UNAVAILABLE.isSameCodeAs(status)
                && code.filter(SERVICE_UNAVAILABLE::equals).isPresent();
        return databaseOutage ? Reason.SERVICE_UNAVAILABLE : Reason.UNEXPECTED_RESPONSE;
    }

    private static RuntimeException notFound(Optional<String> code) {
        return code.flatMap(ReferenceKind::byNotFoundCode)
                .<RuntimeException>map(ReferenceNotFoundException::new)
                .orElseGet(() -> new ReferenceUnavailableException(Reason.UNEXPECTED_RESPONSE));
    }
}
