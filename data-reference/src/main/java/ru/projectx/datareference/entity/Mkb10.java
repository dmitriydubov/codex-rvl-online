package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Код МКБ-10 (ТЗ-4). Справочник иерархический, фильтра нет — сохраняется целиком.
 *
 * <p>Родитель хранится <b>GUID'ом источника</b>, а не разрешённой ссылкой на нашу строку.
 * Причина в порядке доставки: справочник приходит сообщениями по одному коду, порядок
 * не гарантирован, и ссылку на ещё не приехавшего родителя пришлось бы либо ронять
 * в ошибку, либо записывать пустой «до следующего сообщения» — а следующего может
 * не быть годами, и дерево осталось бы рваным без единого следа. GUID пишется всегда
 * и разрешается запросом, когда родитель появится.
 */
@Entity
@Table(name = "mkb10")
public class Mkb10 extends OneCEntity {

    @Nullable
    @Column(name = "external_parent_id")
    private UUID externalParentId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Nullable
    @Column(name = "system_code")
    private String systemCode;

    @Nullable
    @Column(name = "hierarchy_level")
    private Integer hierarchyLevel;

    protected Mkb10() {
        // JPA
    }

    private Mkb10(UUID externalId, Details details, Instant now) {
        super(externalId, now);
        applySnapshot(details, now);
    }

    public static Mkb10 create(UUID externalId, Details details, Instant now) {
        return new Mkb10(externalId, details, now);
    }

    public void applySnapshot(Details details, Instant now) {
        this.externalParentId = details.externalParentId();
        this.code = details.code();
        this.name = details.name();
        this.systemCode = details.systemCode();
        this.hierarchyLevel = details.hierarchyLevel();
        applyCommon(details.deleteMark(), now);
    }

    /** Разобранные поля сообщения ТЗ-4. Разбор — в маппере, инварианты — здесь. */
    public record Details(
            @Nullable UUID externalParentId,
            String code,
            String name,
            @Nullable String systemCode,
            @Nullable Integer hierarchyLevel,
            boolean deleteMark) {
    }

    @Nullable
    public UUID externalParentId() {
        return externalParentId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    @Nullable
    public String systemCode() {
        return systemCode;
    }

    @Nullable
    public Integer hierarchyLevel() {
        return hierarchyLevel;
    }
}
