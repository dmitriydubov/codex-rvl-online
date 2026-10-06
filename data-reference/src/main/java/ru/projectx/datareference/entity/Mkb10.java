package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Код диагноза по МКБ-10 из 1С. Справочник иерархический и сохраняется целиком.
 *
 * <p>Родитель хранится <b>GUID'ом источника</b>, а не ссылкой на нашу строку. Коды приходят
 * по одному и в любом порядке, и ссылку на ещё не приехавшего родителя пришлось бы либо
 * отвергнуть, либо оставить пустой. GUID пишется всегда и разрешается запросом, когда
 * родитель появится.
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

    private Mkb10(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /** Новая запись из первого сообщения 1С. */
    public static Mkb10 create(UUID externalId, Details details, Instant now) {
        Mkb10 code = new Mkb10(externalId, now);
        code.applySnapshot(details, now);
        return code;
    }

    public void applySnapshot(Details details, Instant now) {
        this.externalParentId = details.externalParentId();
        this.code = details.code();
        this.name = details.name();
        this.systemCode = details.systemCode();
        this.hierarchyLevel = details.hierarchyLevel();
        applyCommon(details.deleteMark(), now);
    }

    /** Разобранные поля сообщения о коде МКБ-10. */
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
