package ru.projectx.datareference.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.integration.util.UUIDConverter;
import ru.projectx.datareference.entity.inbound.OneCStream;

class ReferenceCacheChannelTest {

    @Test
    void channel_isTheSubscriberChannelOfOurPrefix() {
        assertThat(ReferenceCacheChannel.channel()).isEqualTo("reference_cache_channel_message_notify");
    }

    @Test
    void payload_isRegionAndUuidOfStreamName_asSubscriberKeysSubscriptions() {
        assertThat(ReferenceCacheChannel.payload(OneCStream.MKB10))
                .isEqualTo("reference-cache " + UUIDConverter.getUUID("MKB10"));
    }

    @Test
    void payloadsOfDifferentStreams_differ() {
        assertThat(ReferenceCacheChannel.payload(OneCStream.MKB10))
                .isNotEqualTo(ReferenceCacheChannel.payload(OneCStream.PROGRAMS));
    }
}
