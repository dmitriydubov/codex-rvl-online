package ru.projectx.datareference.service.cache;

import org.springframework.integration.util.UUIDConverter;
import ru.projectx.datareference.entity.inbound.OneCStream;

public final class ReferenceCacheChannel {

    static final String TABLE_PREFIX = "reference_cache_";
    public static final String REGION = "reference-cache";

    private static final String SUBSCRIBER_CHANNEL = "channel_message_notify";
    private static final String KEY_SEPARATOR = " ";

    private ReferenceCacheChannel() {
    }

    public static String channel() {
        return TABLE_PREFIX + SUBSCRIBER_CHANNEL;
    }

    public static String payload(OneCStream stream) {
        return REGION + KEY_SEPARATOR + UUIDConverter.getUUID(groupOf(stream));
    }

    static String groupOf(OneCStream stream) {
        return stream.name();
    }
}
