package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/**
 * Общая часть записи, приехавшей из 1С: наш идентификатор, GUID объекта в 1С и признак архивности.
 *
 * <p>Обновление по GUID 1С и перевод пометки удаления в признак архивности одинаковы для всех потоков,
 * поэтому живут в одном классе: копии разошлись бы по поведению. Версии сообщения здесь нет — её
 * присылают не все потоки. Для всех действует одно правило: применяется последнее полученное сообщение,
 * а порядок держит доставка (одна очередь, один потребитель, разбор по времени получения).
 *
 * <p>Класс не абстрактный: абстрактных методов у него нет, а спецификация Jakarta Persistence
 * (раздел 2.13.2) допускает конкретный класс в этой роли. Своей таблицы у него нет, а создают его
 * только наследники: конструкторы защищённые.
 */
@MappedSuperclass
public class OneCEntity {

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
     * Общий хвост применения сообщения: наследник вызывает его последним действием. Метод не публичный:
     * вызванный сам по себе, он пометил бы запись обновлённой, не изменив ни одного поля.
     */
    protected void applyCommon(boolean deleteMark, Instant now) {
        this.archive = deleteMark;
        this.updatedAt = now;
    }

    /**
     * Запись перестала подходить под отбор потока, например программа больше не относится к РВЛ.
     * Запись уходит в архив, а не удаляется; связанные строки не трогаются.
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
