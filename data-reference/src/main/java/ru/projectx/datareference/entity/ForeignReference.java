package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Контрагент, договор санатория или услуга 1С:УСК, которые сервис признал не нашими: санаторий без вида
 * деятельности РВЛ, страхователь, договор чужого контрагента, услуга такого договора.
 *
 * <p>В справочниках таких записей нет. Без этой отметки сообщение, которое на них ссылается, нельзя отличить от
 * сообщения, родитель которого ещё не пришёл: первое пропускается сразу, второе ждёт. Строки пишет и читает разбор
 * запросами репозитория; сама сущность нужна, чтобы у репозитория был тип.
 */
@Entity
@Table(name = "foreign_reference")
public class ForeignReference {

    @Id
    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ParentKind kind;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ForeignReference() {
        // JPA
    }
}
