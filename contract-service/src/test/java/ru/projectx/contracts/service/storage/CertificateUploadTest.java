package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.ParticipantAttachment;

class CertificateUploadTest {

    @Test
    void owner_isTheRequestParticipantAndAttachment() {
        assertThat(Uploads.jpeg().owner())
                .isEqualTo(new ParticipantAttachment(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT));
    }

    @Test
    void declaredType_isEmptyWhenThePartHasNone() {
        assertThat(Uploads.sized(null, 1).declaredType()).isEmpty();
        assertThat(Uploads.jpeg().declaredType()).contains("image/jpeg");
    }
}
