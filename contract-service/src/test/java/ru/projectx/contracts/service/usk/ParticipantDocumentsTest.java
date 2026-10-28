package ru.projectx.contracts.service.usk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.party.IdentityDocument;
import ru.projectx.contracts.entity.party.IdentityDocumentType;
import ru.projectx.contracts.message.ParticipantDocument;

class ParticipantDocumentsTest {

    @Test
    void document_isCopiedFieldByFieldWithTheTypeAsItsName() {
        IdentityDocument document = new IdentityDocument(IdentityDocumentType.BIRTH_CERTIFICATE, "IV-M", "512345",
                "Тестовый орган", LocalDate.parse("2010-04-20"), "770-001");

        assertThat(ParticipantDocuments.of(document)).isEqualTo(new ParticipantDocument("BIRTH_CERTIFICATE", "IV-M",
                "512345", "Тестовый орган", LocalDate.parse("2010-04-20"), "770-001"));
    }

    @Test
    void optionalFields_stayAbsent() {
        IdentityDocument document = new IdentityDocument(IdentityDocumentType.RF_PASSPORT, null, "123456", null, null,
                null);

        assertThat(ParticipantDocuments.of(document)).isEqualTo(new ParticipantDocument("RF_PASSPORT", null, "123456",
                null, null, null));
    }
}
