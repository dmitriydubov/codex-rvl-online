package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/**
 * Общая часть записи, приехавшей из 1С: ключ upsert-а и признак архивности.
 *
 * <p>Базовый класс здесь оправдан ровно потому, что эти поля — не «похожие колонки
 * в похожих таблицах», а один и тот же механизм контракта, одинаковый для всех потоков:
 * upsert по {@code external_id} и {@code deleteMark → is_archive}. Разъехавшись
 * по копиям, он разойдётся и по поведению.
 *
 * <p><b>Версии сообщения ({@code dataVersion}) здесь нет.</b> Контракт объявляет её
 * не у всех потоков — ТЗ-2, ТЗ-3, ТЗ-8 и ТЗ-9 обходятся без неё, — и правило «применяем
 * только более старшую» работало бы у половины справочников, а у второй половины молча
 * не работало. Половина механизма хуже его отсутствия: она выглядит защитой, ею не являясь.
 * Действует одно правило и для всех: <b>применяется последнее полученное сообщение</b>.
 * Порядок держится на пути доставки — очередь одна, потребитель один, разбор идёт
 * по возрастанию {@code received_at}.
 *
 * <p>Общего базового класса <i>репозитория</i> при этом нет: он потребовал бы общего
 * интерфейса сущности и потянул бы связанность, которой здесь незачем быть.
 */
@MappedSuperclass
public abstract class OneCEntity {

    @Id
    private UUID id;

    /** GUID объекта в 1С. Именно по нему идёт upsert, а не по нашему {@link #id}. */
    @Column(name = "external_id", nullable = false, unique = true, updatable = false)
    private UUID externalId;

    @Column(name = "is_archive", nullable = false)
    private boolean archive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OneCEntity() {
        // JPA
    }

    protected OneCEntity(UUID externalId, Instant now) {
        this.id = UUID.randomUUID();
        this.externalId = externalId;
        this.archive = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Общий хвост применения снимка. Вызывается наследником последним действием
     * своего перехода и публичным намеренно не выставлен: вызванный вместо перехода,
     * он пометил бы запись обновлённой, не тронув ни одного поля.
     */
    protected void applyCommon(boolean deleteMark, Instant now) {
        this.archive = deleteMark;
        this.updatedAt = now;
    }

    /**
     * Запись перестала подходить под фильтр потока: у ЛПУ пропал вид деятельности РВЛ
     * (ТЗ-3), у программы пришло {@code rvl = false} (ТЗ-6). Физического удаления
     * нет нигде — связанные строки при этом не трогаются.
     */
    protected void archiveCommon(Instant now) {
        applyCommon(true, now);
    }

    public UUID id() {
        return id;
    }

    public UUID externalId() {
        return externalId;
    }

    public boolean isArchive() {
        return archive;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
