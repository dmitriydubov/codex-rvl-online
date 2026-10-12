package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Jackson3JsonFormatMapperTest {

    private final Jackson3JsonFormatMapper mapper = new Jackson3JsonFormatMapper();

    @Test
    void objectIsWrittenAsJson() {
        String json = mapper.toString(new Envelope("ContractTaskUpserted", 3), Envelope.class);

        assertThat(json).isEqualTo("{\"eventType\":\"ContractTaskUpserted\",\"version\":3}");
    }

    @Test
    void jsonIsReadBackIntoObjectOfRequestedType() {
        Envelope envelope = mapper.fromString("{\"eventType\":\"ContractTaskUpserted\",\"version\":3}", Envelope.class);

        assertThat(envelope).isEqualTo(new Envelope("ContractTaskUpserted", 3));
    }


    record Envelope(String eventType, int version) {
    }
}
