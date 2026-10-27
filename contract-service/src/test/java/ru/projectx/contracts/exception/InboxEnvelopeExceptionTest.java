package ru.projectx.contracts.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InboxEnvelopeExceptionTest {

    @Test
    void unusableField_namesTheFieldAndIsLostAsAnUnusableEnvelope() {
        InboxEnvelopeException failure = InboxEnvelopeException.unusableField("message_id");

        assertThat(failure).hasMessage("в конверте нет пригодного message_id");
        assertThat(failure.lossReason()).isEqualTo("unusable_envelope");
    }

    @Test
    void unreadableBody_keepsItsCauseAndIsLostAsAnUnreadableBody() {
        IllegalArgumentException cause = new IllegalArgumentException("Тестова");
        InboxEnvelopeException failure = InboxEnvelopeException.unreadableBody(cause);

        assertThat(failure).hasMessage("тело не читается как JSON").hasCause(cause);
        assertThat(failure.lossReason()).isEqualTo("unreadable_body");
    }
}
