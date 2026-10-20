package ru.projectx.datareference.service.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import ru.projectx.datareference.config.cache.ReferenceCacheNotificationProperties;
import ru.projectx.datareference.entity.inbound.OneCStream;

@Configuration(proxyBeanMethods = false)
public class ReferenceCacheNotificationsConfiguration {

    @Bean
    PostgresChannelMessageTableSubscriber referenceCacheSubscriber(PgConnectionSupplier referenceCacheListenerConnections,
            ReferenceCacheNotificationProperties properties, ReferenceCacheEviction eviction) {

        PostgresChannelMessageTableSubscriber subscriber = new PostgresChannelMessageTableSubscriber(
                referenceCacheListenerConnections, ReferenceCacheChannel.TABLE_PREFIX);
        subscriber.setNotificationTimeout(properties.silenceCheck());
        for (OneCStream stream : OneCStream.values()) {
            subscriber.subscribe(new StreamCacheSubscription(stream, eviction));
        }
        return subscriber;
    }
}
