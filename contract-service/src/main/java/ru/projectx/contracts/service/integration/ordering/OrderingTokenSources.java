package ru.projectx.contracts.service.integration.ordering;

import java.time.Clock;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.config.payment.OrderingProperties;

final class OrderingTokenSources {

    private OrderingTokenSources() {
    }

    static OrderingTokens create(OrderingProperties.Token settings, Clock clock) {
        InMemoryClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(
                registration(settings));
        InMemoryOAuth2AuthorizedClientService clients = new InMemoryOAuth2AuthorizedClientService(registrations);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(new ClockAlignedTokenProvider(provider(settings, clock), clock));
        return new OrderingTokens(manager, clients);
    }

    private static ClientRegistration registration(OrderingProperties.Token settings) {
        return ClientRegistration.withRegistrationId(OrderingTokens.REGISTRATION)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientId(settings.clientId())
                .clientSecret(settings.clientSecret())
                .tokenUri(settings.uri())
                .build();
    }

    private static ClientCredentialsOAuth2AuthorizedClientProvider provider(OrderingProperties.Token settings,
            Clock clock) {
        RestClientClientCredentialsTokenResponseClient tokens = new RestClientClientCredentialsTokenResponseClient();
        tokens.setRestClient(RestClient.builder()
                .requestFactory(OrderingRestClients.requests(settings.connectTimeout(), settings.readTimeout()))
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        ClientCredentialsOAuth2AuthorizedClientProvider provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
        provider.setAccessTokenResponseClient(tokens);
        provider.setClock(clock);
        return provider;
    }
}
