package ru.projectx.datareference.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис застрахованного — из потока полисов 1С. Отдельной сущности «застрахованное лицо»
 * в контракте нет: полис и есть застрахованный в рамках договора страхования.
 *
 * <p><b>Здесь лежат персональные данные (ПДн) застрахованного</b>: ФИО, дата рождения, почта. Они не попадают
 * в журнал ни значением, ни в тексте исключения. Хранятся они, пока полис действует, и ещё запас после окончания
 * срока или перевода в архив; потом стираются ({@link #erasePersonalDataBeyond}), а строка полиса с реквизитами
 * остаётся: на неё ссылается сервис Договоров. Новое сообщение 1С приносит ПДн снова.
 *
 * <p>Родственники — это <b>отдельные полисы</b>, а не члены семьи внутри одного:
 * связи «чей это родственник» в контракте нет вообще, и поля в описании потока тоже нет.
 * Паспортных данных этот поток не приносит — описание потока прямо говорит, что структура их потока
 * не определена, поэтому таблицы для них здесь нет.
 *
 * <p>Обе табличные части <b>замещаются целиком</b>: сообщение всегда несёт полный состав
 * строк, и строка, пропавшая из сообщения, обязана пропасть из базы. Слить старые
 * с новыми значило бы накапливать программы, которых у человека уже нет.
 */
@Entity
@Table(name = "policies")
public class Policy extends OneCEntity {

    /**
     * Договор страхования, по которому выдан полис. Логическая ссылка GUID'ом,
     * без внешнего ключа: поток договоров страхования из 1С на контуре не создан,
     * и требовать существования строки, которой неоткуда взяться, нельзя.
     */
    @Column(name = "external_contract_id", nullable = false)
    private UUID externalContractId;

    @Column(nullable = false)
    private String number;

    @Nullable
    @Column(name = "last_name")
    private String lastName;

    @Nullable
    @Column(name = "first_name")
    private String firstName;

    @Nullable
    @Column(name = "middle_name")
    private String middleName;

    @Nullable
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Nullable
    private String gender;

    /** Тот адрес, которым человек входит в личный кабинет. Может отсутствовать. */
    @Nullable
    private String email;

    @Column(name = "begin_date", nullable = false)
    private LocalDate beginDate;

    /** {@code null} — открытая дата источника, а не «полис без срока». */
    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean availability;

    /** Когда сообщение 1С перевело полис в архив; пусто, пока полис не в архиве. Повторная присылка его не сдвигает. */
    @Nullable
    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Когда стёрты ПДн; пусто, пока они хранятся. */
    @Nullable
    @Column(name = "personal_data_erased_at")
    private Instant personalDataErasedAt;

    /*
     * Коллекции не final: спецификация Jakarta Persistence (раздел 2.1) запрещает final у сохраняемых
     * полей сущности, а Hibernate при загрузке подменяет коллекцию своей реализацией.
     */
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PolicyProgram> programs = new ArrayList<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PolicyServiceLine> serviceLines = new ArrayList<>();

    protected Policy() {
        // JPA
    }

    private Policy(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /**
     * Новый полис из первого сообщения. Поля заполняет фабрика, а не конструктор: метод, который
     * наследник может переопределить, из конструктора вызывать нельзя — наследник получил бы
     * вызов раньше, чем инициализировались его собственные поля.
     */
    public static Policy create(UUID externalId, Snapshot snapshot, Instant now) {
        Policy policy = new Policy(externalId, now);
        policy.applySnapshot(snapshot, now);
        return policy;
    }

    public void applySnapshot(Snapshot snapshot, Instant now) {
        Details details = snapshot.details();
        this.externalContractId = details.externalContractId();
        this.number = details.number();
        this.lastName = details.lastName();
        this.firstName = details.firstName();
        this.middleName = details.middleName();
        this.dateOfBirth = details.dateOfBirth();
        this.gender = details.gender();
        this.email = details.email();
        this.beginDate = details.beginDate();
        this.endDate = details.endDate();
        this.availability = details.availability();
        this.archivedAt = archivedSince(details.deleteMark(), now);
        this.personalDataErasedAt = null;
        replaceTableParts(snapshot, now);
        applyCommon(details.deleteMark(), now);
    }

    /**
     * Стирает ФИО, дату рождения и почту, если полис пережил границу хранения: срок действия кончился раньше её дня
     * или полис переведён в архив не позже её момента. Реквизиты, пол и время обновления остаются: стирание — не
     * сообщение 1С.
     */
    public void erasePersonalDataBeyond(PersonalDataCutoff cutoff, Instant now) {
        if (endedBefore(cutoff.endedBefore()) || archivedBy(cutoff.archivedBy())) {
            this.lastName = null;
            this.firstName = null;
            this.middleName = null;
            this.dateOfBirth = null;
            this.email = null;
            this.personalDataErasedAt = now;
        }
    }

    /** Стёрты ли ПДн полиса по сроку хранения. */
    public boolean personalDataErased() {
        return personalDataErasedAt != null;
    }

    /** Момент перевода в архив остаётся прежним, пока полис в архиве: повторная присылка — не новый перевод. */
    private @Nullable Instant archivedSince(boolean deleteMark, Instant now) {
        if (!deleteMark) {
            return null;
        }
        return archivedAt == null ? now : archivedAt;
    }

    private boolean endedBefore(LocalDate day) {
        return endDate != null && endDate.isBefore(day);
    }

    private boolean archivedBy(Instant moment) {
        return archivedAt != null && !archivedAt.isAfter(moment);
    }

    /**
     * Обычный цикл, а не стрим: он наполняет коллекции сущности, то есть имеет побочный
     * эффект по определению.
     */
    private void replaceTableParts(Snapshot snapshot, Instant now) {
        programs.clear();
        for (PolicyProgram.Row row : snapshot.programs()) {
            programs.add(PolicyProgram.of(this, row, now));
        }
        serviceLines.clear();
        for (PolicyServiceLine.Row row : snapshot.serviceLines()) {
            serviceLines.add(PolicyServiceLine.of(this, row, now));
        }
    }

    /**
     * Разобранное сообщение 1С о полисе целиком: поля полиса и обе табличные части.
     *
     * <p>Одной записью, а не тремя аргументами, потому что применяются они только
     * вместе — замещение табличных частей без обновления полиса оставило бы запись,
     * у которой версия уже новая, а программы ещё старые.
     */
    public record Snapshot(
            Details details,
            List<PolicyProgram.Row> programs,
            List<PolicyServiceLine.Row> serviceLines) {

        public Snapshot {
            programs = List.copyOf(programs);
            serviceLines = List.copyOf(serviceLines);
        }
    }

    /**
     * Поля самого полиса в том виде, как их прислала 1С. Тот же набор отдаёт {@link #details()}; там пометка
     * удаления — это признак архивности записи, а ФИО и дата рождения пусты, если ПДн стёрты по сроку хранения.
     */
    public record Details(
            UUID externalContractId,
            String number,
            @Nullable String lastName,
            @Nullable String firstName,
            @Nullable String middleName,
            @Nullable LocalDate dateOfBirth,
            @Nullable String gender,
            @Nullable String email,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability,
            boolean deleteMark) {

        /** Без ФИО, даты рождения, пола, почты и номера полиса: запись может случайно попасть в журнал. */
        @Override
        public String toString() {
            return "Details[externalContractId=" + externalContractId + "]";
        }
    }

    /**
     * Поля полиса, как их применило последнее сообщение. Пометка удаления в ответе — это признак
     * архивности записи: иначе, чем через сообщение, он у полиса не меняется.
     */
    public Details details() {
        return new Details(externalContractId, number, lastName, firstName, middleName, dateOfBirth, gender, email,
                beginDate, endDate, availability, isArchive());
    }

    public List<PolicyProgram> programs() {
        return Collections.unmodifiableList(programs);
    }

    public List<PolicyServiceLine> serviceLines() {
        return Collections.unmodifiableList(serviceLines);
    }
}
