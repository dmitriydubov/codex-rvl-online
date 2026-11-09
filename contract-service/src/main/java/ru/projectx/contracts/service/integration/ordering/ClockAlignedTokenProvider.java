package ru.projectx.contracts.service.integration.ordering;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

final class ClockAlignedTokenProvider implements OAuth2AuthorizedClientProvider {

    private final OAuth2AuthorizedClientProvider delegate;
    private final Clock clock;

    ClockAlignedTokenProvider(OAuth2AuthorizedClientProvider delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    public @Nullable OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context) {
        return Optional.ofNullable(delegate.authorize(context))
                .map(this::issuedNow)
                .orElse(null);
    }

    private OAuth2AuthorizedClient issuedNow(OAuth2AuthorizedClient issued) {
        OAuth2AccessToken token = issued.getAccessToken();
        Instant now = clock.instant();
        Duration lifetime = Duration.between(token.getIssuedAt(), token.getExpiresAt());
        OAuth2AccessToken aligned = new OAuth2AccessToken(token.getTokenType(), token.getTokenValue(), now,
                now.plus(lifetime), token.getScopes());
        return new OAuth2AuthorizedClient(issued.getClientRegistration(), issued.getPrincipalName(), aligned,
                issued.getRefreshToken());
    }
}
