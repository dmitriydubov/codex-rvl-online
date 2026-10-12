package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "contract_persons")
public class ContractPerson {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Nullable
    @Column(name = "contract_id", updatable = false)
    private UUID contractId;

    @Nullable
    @Column(name = "lm_policy_id", updatable = false)
    private UUID lmPolicyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, updatable = false)
    private PersonRole role;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    @Column(name = "last_name", nullable = false, updatable = false)
    private String lastName;

    @Column(name = "first_name", nullable = false, updatable = false)
    private String firstName;

    @Nullable
    @Column(name = "middle_name", updatable = false)
    private String middleName;

    @Column(name = "birth_date", nullable = false, updatable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, updatable = false)
    private Gender gender;

    @Nullable
    @Column(name = "relation_type", updatable = false)
    private String relationType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "document_type", nullable = false, updatable = false)
    private IdentityDocumentType documentType;

    @Nullable
    @Column(name = "document_series", updatable = false)
    private String documentSeries;

    @Column(name = "document_number", nullable = false, updatable = false)
    private String documentNumber;

    @Nullable
    @Column(name = "document_issued_by", updatable = false)
    private String documentIssuedBy;

    @Nullable
    @Column(name = "document_issued_at", updatable = false)
    private LocalDate documentIssuedAt;

    @Nullable
    @Column(name = "document_division_code", updatable = false)
    private String documentDivisionCode;

    @Nullable
    @Column(updatable = false)
    private String address;

    @Column(name = "snapshot_at", nullable = false, updatable = false)
    private Instant snapshotAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContractPerson() {
    }

    private ContractPerson(PersonOwner owner, PersonRole role, PersonSnapshot person, Instant now) {
        this.contractId = owner.contractId();
        this.lmPolicyId = owner.lmPolicyId();
        this.role = role;
        this.participantId = person.participantId();
        this.lastName = person.lastName();
        this.firstName = person.firstName();
        this.middleName = person.middleName();
        this.birthDate = person.birthDate();
        this.gender = person.gender();
        this.relationType = person.relationType();
        this.address = person.address();
        this.snapshotAt = now;
        this.createdAt = now;
        takeDocument(person.document());
    }

    private void takeDocument(IdentityDocument document) {
        this.documentType = document.type();
        this.documentSeries = document.series();
        this.documentNumber = document.number();
        this.documentIssuedBy = document.issuedBy();
        this.documentIssuedAt = document.issuedAt();
        this.documentDivisionCode = document.divisionCode();
    }

    public static ContractPerson snapshot(PersonOwner owner, PersonRole role, PersonSnapshot person, Instant now) {
        return new ContractPerson(owner, role, person, now);
    }

    public PersonRole role() {
        return role;
    }

    public PersonSnapshot person() {
        IdentityDocument document = new IdentityDocument(
                documentType, documentSeries, documentNumber, documentIssuedBy, documentIssuedAt, documentDivisionCode);
        return new PersonSnapshot(
                participantId, lastName, firstName, middleName, birthDate, gender, relationType, document, address);
    }
}
