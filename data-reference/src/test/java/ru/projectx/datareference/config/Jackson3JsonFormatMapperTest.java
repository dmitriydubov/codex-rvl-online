package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Jackson3JsonFormatMapperTest {

    private final Jackson3JsonFormatMapper mapper = new Jackson3JsonFormatMapper();

    @Test
    void objectIsWrittenAsJson() {
        String json = mapper.toString(new Contact("Москва", 3), Contact.class);

        assertThat(json).isEqualTo("{\"city\":\"Москва\",\"floor\":3}");
    }

    @Test
    void jsonIsReadBackIntoObjectOfRequestedType() {
        Contact contact = mapper.fromString("{\"city\":\"Москва\",\"floor\":3}", Contact.class);

        assertThat(contact).isEqualTo(new Contact("Москва", 3));
    }

    record Contact(String city, int floor) {
    }
}
