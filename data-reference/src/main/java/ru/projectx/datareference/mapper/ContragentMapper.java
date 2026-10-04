package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCGuid;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает поток контрагентов-юрлиц из 1С:УСК (ТЗ-3). В одном потоке приходят два разных
 * справочника, и различает их сама 1С полем {@code thisIsLPU}.
 *
 * <p>Санаторий ({@code thisIsLPU = true}) принимается только с видом деятельности «РВЛ»: поток несёт
 * всех контрагентов 1С:УСК, а нужны из них единицы. Если санатория у нас нет и вида «РВЛ» у него нет,
 * сообщение просто не наше, и оно уходит в пропуск. Если санаторий у нас уже есть, а вид «РВЛ» в новом
 * сообщении пропал, запись не удаляется, а становится архивной, и её реквизиты всё равно обновляются.
 *
 * <p>Страхователь ({@code thisIsLPU = false}) сохраняется без фильтра в справочник страхователей.
 *
 * <p>GUID вида деятельности «РВЛ» берётся из настройки {@code projectx.onec.contract.rvl-activity-id}
 * и читается при создании маппера, то есть при старте сервиса. Запись не в канонической форме 8-4-4-4-12
 * останавливает старт: стандартное чтение UUID превратило бы её в другой GUID, и все санатории молча
 * уходили бы в пропуск. Само значение в текст ошибки не попадает.
 */
@Component
public class ContragentMapper implements StreamMapper {

    private static final String ACTIVITIES = "typesActivities";
    private static final String ACTIVITY_GUID = "guidTypeActivities";
    private static final String RVL_ACTIVITY_NOT_CANONICAL =
            "настройка projectx.onec.contract.rvl-activity-id не в канонической записи GUID 8-4-4-4-12: "
                    + "без неё санатории РВЛ не отличить от прочих контрагентов 1С:УСК";
    private static final String RVL_ACTIVITY_NOT_SET =
            "не задан projectx.onec.contract.rvl-activity-id — отличить санаторий от прочих юрлиц 1С:УСК нечем";
    private static final String WITHOUT_RVL = "контрагент без вида деятельности РВЛ";
    private static final String NOT_A_SANATORIUM =
            "контрагент не ЛПУ (thisIsLPU = false) — сохранён в справочник страхователей";

    private final LpuRepository lpuRepository;
    private final InsurerRepository insurerRepository;
    private final Optional<UUID> rvlActivity;
    private final Clock clock;

    public ContragentMapper(
            LpuRepository lpuRepository,
            InsurerRepository insurerRepository,
            OneCContractProperties contract,
            Clock clock) {

        this.lpuRepository = lpuRepository;
        this.insurerRepository = insurerRepository;
        this.rvlActivity = rvlActivityOf(contract);
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.CONTRAGENTS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "guid");
        return OneCValues.flag(data, "thisIsLPU")
                ? applySanatorium(externalId, data)
                : applyInsurer(externalId, data);
    }

    /** Пустая настройка означает, что GUID не задан; заданная читается только в канонической записи. */
    private static Optional<UUID> rvlActivityOf(OneCContractProperties contract) {
        return contract.rvlActivity().map(setting -> OneCGuid.parse(setting)
                .orElseThrow(() -> new IllegalStateException(RVL_ACTIVITY_NOT_CANONICAL)));
    }

    private ImportResult applySanatorium(UUID externalId, ObjectNode data) {
        return rvlActivity
                .map(rvl -> applySanatorium(externalId, data, rvl))
                .orElseGet(() -> ImportResult.skipped(RVL_ACTIVITY_NOT_SET));
    }

    private ImportResult applySanatorium(UUID externalId, ObjectNode data, UUID rvl) {
        Lpu.Details details = lpuDetailsOf(data);
        Optional<Lpu> known = lpuRepository.findByExternalId(externalId);
        if (!hasActivity(data, rvl)) {
            return known
                    .map(lpu -> archiveAfterLosingRvl(lpu, details))
                    .orElseGet(() -> ImportResult.skipped(WITHOUT_RVL));
        }
        Lpu lpu = known
                .map(existing -> refreshed(existing, details))
                .orElseGet(() -> Lpu.create(externalId, details, clock.instant()));
        lpuRepository.save(lpu);
        return ImportResult.applied();
    }

    private ImportResult archiveAfterLosingRvl(Lpu lpu, Lpu.Details details) {
        lpu.archiveAfterLosingRvl(details, clock.instant());
        lpuRepository.save(lpu);
        return ImportResult.applied();
    }

    private Lpu refreshed(Lpu lpu, Lpu.Details details) {
        lpu.applySnapshot(details, clock.instant());
        return lpu;
    }

    /**
     * Страхователь сохраняется всегда, но исход разбора называется пропуском, хотя строка в справочнике
     * страхователей создана или обновлена.
     *
     * <p>Санатории и страхователи приходят одной очередью. По исходу разбора видно, появился ли санаторий,
     * за которым в этот поток и шли. Если бы страхователь считался применённым, то «санаториев не
     * присылали» и «все присланные оказались страхователями» выглядели бы в базе одинаково. Причина
     * пропуска называет, куда запись всё-таки легла.
     */
    private ImportResult applyInsurer(UUID externalId, ObjectNode data) {
        Insurer.Details details = insurerDetailsOf(data);
        Instant now = clock.instant();

        Insurer insurer = insurerRepository.findByExternalId(externalId)
                .orElseGet(() -> Insurer.create(externalId, details, now));
        insurer.applySnapshot(details, now);
        insurerRepository.save(insurer);

        return ImportResult.skipped(NOT_A_SANATORIUM);
    }

    private static boolean hasActivity(JsonNode data, UUID activityId) {
        return StreamSupport.stream(data.path(ACTIVITIES).spliterator(), false)
                .map(row -> OneCValues.uuid(row, ACTIVITY_GUID))
                .flatMap(Optional::stream)
                .anyMatch(activityId::equals);
    }

    /**
     * Санаторий и страхователь — один и тот же контрагент 1С с одинаковыми реквизитами юрлица. У санатория
     * к ним добавляются телефон и почта, поэтому общие реквизиты читаются один раз.
     */
    private static Lpu.Details lpuDetailsOf(ObjectNode data) {
        Insurer.Details legalEntity = insurerDetailsOf(data);
        return new Lpu.Details(
                legalEntity.name(),
                legalEntity.fullName(),
                legalEntity.inn(),
                legalEntity.kpp(),
                optionalText(data, "Телефон"),
                optionalText(data, "EMAIL"),
                legalEntity.deleteMark());
    }

    /** Имена полей здесь кириллические, потому что это ключи JSON из 1С, а не наш словарь. */
    private static Insurer.Details insurerDetailsOf(ObjectNode data) {
        return new Insurer.Details(
                OneCValues.requiredText(data, "Наименование"),
                optionalText(data, "НаименованиеПолное"),
                optionalText(data, "ИНН"),
                optionalText(data, "КПП"),
                OneCValues.flag(data, "deleteMark"));
    }

    private static @Nullable String optionalText(ObjectNode data, String field) {
        return OneCValues.text(data, field).orElse(null);
    }
}
