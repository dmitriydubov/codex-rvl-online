package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает договоры санаториев из 1С:УСК (ТЗ-5). По каждому сообщению принимаются три решения.
 *
 * <ol>
 *   <li><b>Договор ли это на восстановительное лечение.</b> Признак лежит в дополнительных свойствах
 *       договора, а его имя задаётся настройкой: в контракте 1С на месте имени пока заглушка. Пока имя
 *       не задано, этот фильтр не применяется, см. {@link OneCContractProperties#rvlContractProperty()}.</li>
 *   <li><b>Чей это договор.</b> Если в договоре указано отделение, отличное от головной организации,
 *       договор принадлежит отделению: отделение у нас отдельный санаторий. Поэтому сначала ищется
 *       санаторий отделения, а затем санаторий головной организации.</li>
 *   <li><b>Наш ли он.</b> Если ни одна из двух ссылок не ведёт к санаторию из справочника, договор
 *       заключён с чужим контрагентом и пропускается с причиной, в которой названы обе ссылки.</li>
 * </ol>
 *
 * <p>Признак ценообразующего договора здесь не вычисляется. Сохраняются поля источника: ссылка на
 * основной договор и признак «использовать цены основного договора». Вывод делается при расчёте цены,
 * поэтому сохранённые данные не устаревают, когда меняется правило расчёта.
 *
 * <p>Состояние записи — ровно то, что прислала 1С. Признак архива берётся только из {@code deleteMark}
 * сообщения, своих причин архивировать договор у сервиса нет. Ограничение «не больше одного
 * действующего основного договора на санаторий» здесь не поддерживается намеренно: пришлось бы менять
 * записи, о которых сообщения не приходило, и молча расходиться с источником.
 */
@Component
public class LpuContractMapper extends OwnedCatalogStreamMapper<LpuContract, Lpu, LpuContract.Details> {

    private static final String VALUE = "value";
    private static final String OWNER_ID = "ownerId";
    private static final String BRANCH_ID = "contragentBranchId";

    /**
     * Имя признака выгрузки в систему онлайн-бронирования. Это константа, а не настройка, как у признака
     * договора на восстановительное лечение: это имя контракт 1С называет прямо.
     */
    private static final String SOB_EXPORT_PROPERTY = "Выгружать прейскуранты в систему бронирования";

    private final LpuRepository lpuRepository;
    private final OneCContractProperties contract;

    public LpuContractMapper(
            LpuContractRepository repository,
            LpuRepository lpuRepository,
            OneCContractProperties contract,
            Clock clock) {

        super(OneCStream.LPU_CONTRACTS, repository::findByExternalId, LpuContract::create,
                LpuContract::applySnapshot, repository::save, clock);
        this.lpuRepository = lpuRepository;
        this.contract = contract;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        Optional<String> unmetProperty = unmetRvlProperty(data);
        if (unmetProperty.isPresent()) {
            return ImportResult.skipped("договор без признака " + unmetProperty.get());
        }
        return sanatoriumOf(data)
                .map(sanatorium -> applyUnder(sanatorium, data))
                .orElseGet(() -> ImportResult.skipped(strangerReason(data)));
    }

    /**
     * Имя признака договора на восстановительное лечение, если оно задано, а в договоре признак не
     * взведён. Пока имя не задано, проходит любой договор.
     */
    private Optional<String> unmetRvlProperty(JsonNode data) {
        return contract.rvlContract().filter(property -> !extPropertyFlag(data, property));
    }

    /**
     * Санаторий отделения важнее санатория головной организации. Если отделение не указано или совпадает
     * с головной организацией, результат тот же, что и при поиске по одной головной организации.
     */
    private Optional<Lpu> sanatoriumOf(JsonNode data) {
        Optional<UUID> owner = OneCValues.uuid(data, OWNER_ID);
        Optional<UUID> branch = OneCValues.uuid(data, BRANCH_ID);
        return branch.flatMap(lpuRepository::findByExternalId)
                .or(() -> owner.flatMap(lpuRepository::findByExternalId));
    }

    private ImportResult applyUnder(Lpu sanatorium, ObjectNode data) {
        return upsertUnder(sanatorium, OneCValues.requiredUuid(data, "id"), detailsOf(data));
    }

    private static String strangerReason(JsonNode data) {
        return "договор не нашего контрагента: ownerId=" + uuidOrNull(data, OWNER_ID)
                + ", contragentBranchId=" + uuidOrNull(data, BRANCH_ID);
    }

    /** Дата начала читается первой: без неё договор нарушает контракт, что бы ни было в остальных полях. */
    private static LpuContract.Details detailsOf(ObjectNode data) {
        LocalDate beginDate = beginDateOf(data);
        return new LpuContract.Details(
                textOrNull(data, "number"),
                textOrNull(data, "name"),
                // Нулевой GUID основного договора значит «договор сам по себе» и становится пустой ссылкой.
                uuidOrNull(data, "mainContractId"),
                OneCValues.flag(data, "usePricesMainAgreement"),
                extPropertyFlag(data, SOB_EXPORT_PROPERTY),
                beginDate,
                dateOrNull(data, "endDate"),
                OneCValues.flag(data, "deleteMark"));
    }

    private static LocalDate beginDateOf(JsonNode data) {
        return OneCValues.date(data, "beginDate")
                .orElseThrow(() -> new OneCFieldException("у договора ЛПУ нет даты начала действия"));
    }

    /** Значение логического свойства из дополнительных свойств договора. Свойства нет — значит, ложь. */
    private static boolean extPropertyFlag(JsonNode data, String propertyName) {
        return StreamSupport.stream(data.path("extProperties").spliterator(), false)
                .filter(row -> propertyName.equals(row.path("propertyName").asString(null)))
                .findFirst()
                .map(row -> row.path(VALUE).path(VALUE).asBoolean(false))
                .orElse(false);
    }
}
