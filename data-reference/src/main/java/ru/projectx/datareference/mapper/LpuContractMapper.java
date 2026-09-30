package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-5: договоры с санаториями. Самый нагруженный правилами поток — три разных решения
 * на одно сообщение, и все три в ТЗ описаны прозой, а не таблицей маппинга.
 *
 * <ol>
 *   <li><b>Чей это договор</b> (УСЛОВИЕ 2). Если {@code contragentBranchId} отличается
 *       от {@code ownerId}, договор принадлежит отделению — а отделение у нас отдельный
 *       санаторий. Поэтому предпочтение отдаётся отделению, и лишь затем головной
 *       организации.</li>
 *   <li><b>Наш ли он</b> (фильтр 1). Ни то, ни другое не резолвится в санаторий —
 *       договор чужого контрагента, пропуск.</li>
 *   <li><b>Договор ли это на РВЛ</b> (фильтр 2). Признак задан плейсхолдером; пока имя
 *       свойства не настроено, фильтр не применяется — см.
 *       {@link OneCContractProperties#rvlContractProperty()}.</li>
 * </ol>
 *
 * <p><b>Признак «основной договор» больше не вычисляется здесь</b> (ТЗ-5 v2). Раньше
 * маппер выводил {@code main_price_dog} из двух полей источника и сохранял только
 * результат. Теперь сохраняются сами поля — {@code mainContractId}
 * и {@code usePricesMainAgreement}, — а вывод делается при расчёте цены. Маппер снова
 * делает ровно одно: перекладывает то, что прислали.
 *
 * <p><b>Состояние записи — ровно то, что прислала 1С.</b> {@code is_archive} берётся
 * только из {@code deleteMark} сообщения; своих причин архивировать договор у нас нет.
 * Ограничение «не более одного действующего основного договора на санаторий» здесь
 * не поддерживается намеренно: помечать прежние договоры архивными значило бы менять
 * записи, о которых сообщения не приходило, и расходиться с источником молча.
 */
@Component
public class LpuContractMapper implements StreamMapper {

    private static final String EXT_PROPERTIES = "extProperties";
    private static final String PROPERTY_NAME = "propertyName";
    private static final String VALUE = "value";

    /**
     * Имя признака СОБ в {@code extProperties}. Константа, а не свойство конфигурации,
     * в отличие от признака РВЛ: это имя контракт называет прямо, а не плейсхолдером.
     */
    private static final String SOB_EXPORT_PROPERTY = "Выгружать прейскуранты в систему бронирования";

    private final LpuContractRepository repository;
    private final LpuRepository lpuRepository;
    private final OneCContractProperties contract;
    private final Clock clock;

    public LpuContractMapper(
            LpuContractRepository repository,
            LpuRepository lpuRepository,
            OneCContractProperties contract,
            Clock clock) {

        this.repository = repository;
        this.lpuRepository = lpuRepository;
        this.contract = contract;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.LPU_CONTRACTS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        if (!isRvlContract(data)) {
            return ImportResult.skipped("договор без признака " + contract.rvlContract().orElse("РВЛ"));
        }

        Optional<Lpu> sanatorium = resolveSanatorium(data);
        if (sanatorium.isEmpty()) {
            return ImportResult.skipped("договор не нашего контрагента: ownerId="
                    + OneCValues.uuid(data, "ownerId").orElse(null)
                    + ", contragentBranchId=" + OneCValues.uuid(data, "contragentBranchId").orElse(null));
        }

        UUID externalId = OneCValues.requiredUuid(data, "id");
        LpuContract.Details details = detailsOf(data);
        Optional<LpuContract> existing = repository.findByExternalId(externalId);

        Instant now = clock.instant();
        LpuContract lpuContract = existing
                .map(found -> applyTo(found, sanatorium.get(), details, now))
                .orElseGet(() -> LpuContract.create(externalId, sanatorium.get(), details, now));
        repository.save(lpuContract);
        return ImportResult.applied();
    }

    private static LpuContract applyTo(LpuContract existing, Lpu sanatorium, LpuContract.Details details, Instant now) {
        existing.applySnapshot(sanatorium, details, now);
        return existing;
    }

    /**
     * УСЛОВИЕ 2: договор отделения принадлежит отделению, а не головной организации.
     * Запасной вариант — вторая ссылка: ТЗ формулирует фильтр как «{@code ownerId}
     * ИЛИ {@code contragentBranchId} должен резолвиться».
     */
    private Optional<Lpu> resolveSanatorium(JsonNode data) {
        Optional<UUID> owner = OneCValues.uuid(data, "ownerId");
        Optional<UUID> branch = OneCValues.uuid(data, "contragentBranchId");
        boolean branchIsSeparateSanatorium = branch.isPresent() && !branch.equals(owner);

        Optional<UUID> preferred = branchIsSeparateSanatorium ? branch : owner;
        Optional<UUID> fallback = branchIsSeparateSanatorium ? owner : branch;

        return preferred.flatMap(lpuRepository::findByExternalId)
                .or(() -> fallback.flatMap(lpuRepository::findByExternalId));
    }

    /** Пока имя признака не задано, фильтр пропускает всё — см. javadoc класса. */
    private boolean isRvlContract(JsonNode data) {
        return contract.rvlContract()
                .map(propertyName -> extPropertyFlag(data, propertyName))
                .orElse(true);
    }

    private static boolean extPropertyFlag(JsonNode data, String propertyName) {
        return StreamSupport.stream(data.path(EXT_PROPERTIES).spliterator(), false)
                .filter(row -> propertyName.equals(row.path(PROPERTY_NAME).asString(null)))
                .findFirst()
                .map(row -> row.path(VALUE).path(VALUE).asBoolean(false))
                .orElse(false);
    }

    private static LpuContract.Details detailsOf(ObjectNode data) {
        LocalDate beginDate = OneCValues.date(data, "beginDate")
                .orElseThrow(() -> new OneCFieldException("у договора ЛПУ нет даты начала действия"));
        return new LpuContract.Details(
                OneCValues.text(data, "number").orElse(null),
                OneCValues.text(data, "name").orElse(null),
                // Нулевой GUID источника здесь означает «договор сам по себе»,
                // и OneCValues превращает его в отсутствие ссылки.
                OneCValues.uuid(data, "mainContractId").orElse(null),
                OneCValues.flag(data, "usePricesMainAgreement"),
                extPropertyFlag(data, SOB_EXPORT_PROPERTY),
                beginDate,
                OneCValues.date(data, "endDate").orElse(null),
                OneCValues.flag(data, "deleteMark"));
    }
}
