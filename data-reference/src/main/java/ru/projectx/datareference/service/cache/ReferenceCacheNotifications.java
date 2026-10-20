package ru.projectx.datareference.service.cache;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.repository.database.CacheNotificationRepository;
import ru.projectx.datareference.service.inbound.OneCMessageApplied;

@Component
public class ReferenceCacheNotifications {

    private final CacheNotificationRepository repository;

    public ReferenceCacheNotifications(CacheNotificationRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onApplied(OneCMessageApplied event) {
        repository.notify(ReferenceCacheChannel.channel(), ReferenceCacheChannel.payload(event.stream()));
    }
}
