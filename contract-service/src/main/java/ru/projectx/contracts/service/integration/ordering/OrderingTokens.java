package ru.projectx.contracts.service.integration.ordering;

import java.util.Optional;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingUnavailableException;

final class OrderingTokens {

    static final String REGISTRATION = "ordering";
    static final String PRINCIPAL = "contract-service";

    private static final OAuth2AuthorizeRequest REQUEST =
            OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION).principal(PRINCIPAL).build();

    private final OAuth2AuthorizedClientManager manager;
    private final OAuth2AuthorizedClientService clients;

    OrderingTokens(OAuth2AuthorizedClientManager manager, OAuth2AuthorizedClientService clients) {
        this.manager = manager;
        this.clients = clients;
    }

    String accessToken() {
        return authorized()
                .map(client -> client.getAccessToken().getTokenValue())
                .orElseThrow(() -> new OrderingUnavailableException(OrderingOperation.TOKEN, "токен не выдан"));
    }

    void discard() {
        clients.removeAuthorizedClient(REGISTRATION, PRINCIPAL);
    }

    private Optional<OAuth2AuthorizedClient> authorized() {
        try {
            return Optional.ofNullable(manager.authorize(REQUEST));
        }
        catch (OAuth2AuthorizationException refused) {
            throw new OrderingUnavailableException(OrderingOperation.TOKEN,
                    "сервер токенов не выдал токен: " + refused.getError().getErrorCode(), refused);
        }
    }
}
