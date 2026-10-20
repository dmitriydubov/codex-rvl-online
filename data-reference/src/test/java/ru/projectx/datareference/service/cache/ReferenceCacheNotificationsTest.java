package ru.projectx.datareference.service.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.database.CacheNotificationRepository;
import ru.projectx.datareference.service.inbound.OneCMessageApplied;

class ReferenceCacheNotificationsTest {

    private final CacheNotificationRepository repository = mock(CacheNotificationRepository.class);
    private final ReferenceCacheNotifications notifications = new ReferenceCacheNotifications(repository);

    @Test
    void appliedMessage_notifiesOtherInstancesOfItsStream() {
        notifications.onApplied(new OneCMessageApplied(OneCStream.PRICE_LISTS));

        verify(repository).notify(ReferenceCacheChannel.channel(), ReferenceCacheChannel.payload(OneCStream.PRICE_LISTS));
    }
}
