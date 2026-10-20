package ru.projectx.datareference.service.cache;

import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import ru.projectx.datareference.entity.inbound.OneCStream;

final class StreamCacheSubscription implements PostgresChannelMessageTableSubscriber.Subscription {

    private final OneCStream stream;
    private final ReferenceCacheEviction eviction;

    StreamCacheSubscription(OneCStream stream, ReferenceCacheEviction eviction) {
        this.stream = stream;
        this.eviction = eviction;
    }

    @Override
    public void notifyUpdate() {
        eviction.evict(stream);
    }

    @Override
    public String getRegion() {
        return ReferenceCacheChannel.REGION;
    }

    @Override
    public Object getGroupId() {
        return ReferenceCacheChannel.groupOf(stream);
    }
}
