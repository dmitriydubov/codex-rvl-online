package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-3: контрагенты-юрлица. Один поток несёт два разных справочника, и различает их
 * сама 1С — полем {@code thisIsLPU}:
 *
 * <ul>
 *   <li>{@code true} — санаторий, и он попадает к нам <b>только</b> с видом деятельности
 *       РВЛ. Этот поток несёт всех контрагентов 1С:УСК, а нужны нам единицы;</li>
 *   <li>{@code false} — страхователь. Сохраняется без фильтра, но исход разбора — пропуск
 *       с причиной, а не «применено»: см. {@link #applyInsurer}.</li>
 * </ul>
 *
 * <p>Фильтр РВЛ — не «отбросить лишнее», а различение двух разных состояний, и ведут
 * они себя по-разному:
 * <ul>
 *   <li>контрагента у нас нет и РВЛ у него нет — сообщение просто не наше, пропуск;</li>
 *   <li>контрагент у нас <b>есть</b>, а РВЛ в новом сообщении пропал — запись
 *       не удаляется, а помечается архивной, реквизиты при этом обновляются
 *       (ТЗ-3, п. 3.3).</li>
 * </ul>
 */
@Component
public class ContragentMapper implements StreamMapper {

    private static final String ACTIVITIES = "typesActivities";
    private static final String ACTIVITY_GUID = "guidTypeActivities";
    private static final String NOT_A_SANATORIUM =
            "контрагент не ЛПУ (thisIsLPU = false) — сохранён в справочник страхователей";

    private final LpuRepository lpuRepository;
    private final InsurerRepository insurerRepository;
    private final OneCContractProperties contract;
    private final Clock clock;

    public ContragentMapper(
            LpuRepository lpuRepository,
            InsurerRepository insurerRepository,
            OneCContractProperties contract,
            Clock clock) {

        this.lpuRepository = lpuRepository;
        this.insurerRepository = insurerRepository;
        this.contract = contract;
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

    /** Ветка А: санаторий. Применяется только при виде деятельности РВЛ. */
    private ImportResult applySanatorium(UUID externalId, ObjectNode data) {
        Optional<UUID> rvlActivity = contract.rvlActivity();
        if (rvlActivity.isEmpty()) {
            return ImportResult.skipped(
                    "не задан projectx.onec.contract.rvl-activity-id — "
                            + "отличить санаторий от прочих юрлиц 1С:УСК нечем");
        }

        boolean hasRvl = hasActivity(data, rvlActivity.get());
        Lpu.Details details = lpuDetailsOf(data);
        Optional<Lpu> existing = lpuRepository.findByExternalId(externalId);

        if (existing.isEmpty()) {
            if (!hasRvl) {
                return ImportResult.skipped("контрагент без вида деятельности РВЛ");
            }
            lpuRepository.save(Lpu.create(externalId, details, clock.instant()));
            return ImportResult.applied();
        }

        Lpu lpu = existing.get();

        Instant now = clock.instant();
        if (hasRvl) {
            lpu.applySnapshot(details, now);
        }
        else {
            lpu.archiveAfterLosingRvl(details, now);
        }
        lpuRepository.save(lpu);
        return ImportResult.applied();
    }

    /**
     * Ветка Б: страхователь. Фильтра нет — сохраняются все, но исход всё равно пропуск.
     *
     * <p>Это <b>не</b> значит «ничего не записано»: строка в {@code insurers} создаётся
     * или обновляется. Пропуском исход назван потому, что вопрос к строке сырья на разборе
     * один — <i>появился ли справочник, за которым в этот поток шли</i>. Санатории и
     * страхователи едут одной очередью, и при статусе {@code PROCESSED} у обоих
     * «в {@code lpu} пусто, потому что таких контрагентов не присылали» и «пусто, потому
     * что все они оказались страхователями» выглядят в базе одинаково. Причина пропуска
     * называет и то, куда запись всё-таки легла.
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
     * Имена полей 1С здесь кириллические и приходят как есть — это ключи JSON источника,
     * а не наш словарь.
     */
    private static Lpu.Details lpuDetailsOf(ObjectNode data) {
        return new Lpu.Details(
                OneCValues.requiredText(data, "Наименование"),
                OneCValues.text(data, "НаименованиеПолное").orElse(null),
                OneCValues.text(data, "ИНН").orElse(null),
                OneCValues.text(data, "КПП").orElse(null),
                OneCValues.text(data, "Телефон").orElse(null),
                OneCValues.text(data, "EMAIL").orElse(null),
                OneCValues.flag(data, "deleteMark"));
    }

    private static Insurer.Details insurerDetailsOf(ObjectNode data) {
        return new Insurer.Details(
                OneCValues.requiredText(data, "Наименование"),
                OneCValues.text(data, "НаименованиеПолное").orElse(null),
                OneCValues.text(data, "ИНН").orElse(null),
                OneCValues.text(data, "КПП").orElse(null),
                OneCValues.flag(data, "deleteMark"));
    }
}
