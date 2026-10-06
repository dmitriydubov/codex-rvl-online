package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разбор адреса санатория из потока контактной информации. Контакты принимаются только для
 * санатория из справочника, из строк берутся действительные адреса в порядке предпочтения видов из
 * настроек, а сообщение без подходящего адреса очищает прежний адрес и регион. Контакты контрагента, признанного не
 * нашим, пропускаются, а контрагента, который ещё не пришёл, ждут его. Данные вымышленные.
 */
class LpuContactMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID SANATORIUM = UUID.fromString("1255bbed-0000-4000-8000-000000000001");
    private static final String ACTUAL = "Фактический адрес контрагента";
    private static final String REGISTERED = "Адрес местонахождения (регистрации) контрагента юр.лица";
    private static final String UNLISTED = "Почтовый адрес контрагента";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final LpuRepository repository = mock(LpuRepository.class);
    private final ForeignReferenceRepository foreignRepository = mock(ForeignReferenceRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final LpuContactMapper mapper = new LpuContactMapper(repository,
            new CatalogReferences(repository, mock(LpuContractRepository.class), mock(ContragentServiceRepository.class),
                    new ForeignReferences(foreignRepository, clock)),
            new OneCContractProperties(null, null, null, "Адрес", List.of(ACTUAL, REGISTERED)), clock);
    private final Lpu sanatorium = Lpu.create(SANATORIUM,
            new Lpu.Details("Санаторий «Тестовый»", null, null, null, null, null, false), EARLIER);

    @BeforeEach
    void sanatoriumIsKnown() {
        when(repository.findByExternalId(SANATORIUM)).thenReturn(Optional.of(sanatorium));
    }

    @Test
    void streamIsLpuContacts() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.LPU_CONTACTS);
    }

    @Test
    void addressRow_fillsAddressAndRegion() {
        ImportResult result = mapper.apply(contacts(address(1, ACTUAL, "Сочи, ул. Тестовая, 1", "Тестовый край")));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        verify(repository).save(sanatorium);
        assertThat(sanatorium.address()).isEqualTo("Сочи, ул. Тестовая, 1");
        assertThat(sanatorium.region()).isEqualTo("Тестовый край");
        assertThat(sanatorium.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void addressWithoutRegion_clearsTheRegion() {
        ObjectNode row = address(1, ACTUAL, "Сочи, ул. Тестовая, 1", "Тестовый край");
        row.remove("field2");

        mapper.apply(contacts(row));

        assertThat(sanatorium.address()).isEqualTo("Сочи, ул. Тестовая, 1");
        assertThat(sanatorium.region()).isNull();
    }

    @Test
    void preferredKind_winsWhateverTheRowOrder() {
        mapper.apply(contacts(
                address(1, REGISTERED, "Адрес регистрации", "Регион регистрации"),
                address(2, ACTUAL, "Фактический адрес", "Фактический регион")));

        assertThat(sanatorium.address()).isEqualTo("Фактический адрес");
        assertThat(sanatorium.region()).isEqualTo("Фактический регион");
    }

    @Test
    void listedKind_winsOverUnlistedOne() {
        mapper.apply(contacts(
                address(1, UNLISTED, "Почтовый адрес", "Почтовый регион"),
                address(2, REGISTERED, "Адрес регистрации", "Регион регистрации")));

        assertThat(sanatorium.address()).isEqualTo("Адрес регистрации");
    }

    @Test
    void unlistedKind_isStillAnAddress() {
        mapper.apply(contacts(address(1, UNLISTED, "Почтовый адрес", "Почтовый регион")));

        assertThat(sanatorium.address()).isEqualTo("Почтовый адрес");
    }

    @Test
    void amongEqualKinds_theLowerRowNumberWins() {
        mapper.apply(contacts(
                address(3, UNLISTED, "Третья строка", "Регион"),
                address(2, UNLISTED, "Вторая строка", "Регион")));

        assertThat(sanatorium.address()).isEqualTo("Вторая строка");
    }

    @Test
    void rowWithoutNumber_losesToANumberedOne() {
        ObjectNode unnumbered = address(1, ACTUAL, "Строка без номера", "Регион");
        unnumbered.remove("rowNumber");

        mapper.apply(contacts(unnumbered, address(7, ACTUAL, "Строка с номером", "Регион")));

        assertThat(sanatorium.address()).isEqualTo("Строка с номером");
    }

    @Test
    void phonesAndInvalidAddresses_areIgnored() {
        ObjectNode phone = address(1, ACTUAL, "+7 000 000-00-00", null);
        phone.put("type", "Телефон");
        ObjectNode invalid = address(2, ACTUAL, "Старый адрес", "Старый регион");
        invalid.put("notValid", true);

        mapper.apply(contacts(phone, invalid));

        assertThat(sanatorium.address()).isNull();
        assertThat(sanatorium.region()).isNull();
    }

    @Test
    void messageWithoutAddresses_clearsThePreviousOne() {
        sanatorium.applyContacts("Прежний адрес", "Прежний регион", EARLIER);

        ImportResult result = mapper.apply(contacts());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        verify(repository).save(sanatorium);
        assertThat(sanatorium.address()).isNull();
        assertThat(sanatorium.region()).isNull();
        assertThat(sanatorium.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void contactsOfForeignContragent_areSkippedNamingIt_andNothingIsSaved() {
        UUID stranger = UUID.fromString("1255bbed-0000-4000-8000-0000000000ff");
        when(foreignRepository.existsByExternalIdAndKind(stranger, ParentKind.CONTRAGENT)).thenReturn(true);
        ObjectNode data = contacts(address(1, ACTUAL, "Чужой адрес", "Чужой регион"));
        data.set("filter", filter("object", stranger));

        ImportResult result = mapper.apply(data);

        assertThat(result).isEqualTo(ImportResult.skipped("контакты не нашего контрагента: " + stranger));
        verify(repository, never()).save(any());
    }

    @Test
    void contactsOfContragentNotYetReceived_waitForIt_andNothingIsSaved() {
        UUID notYet = UUID.fromString("1255bbed-0000-4000-8000-0000000000ee");
        ObjectNode data = contacts(address(1, ACTUAL, "Будущий адрес", "Будущий регион"));
        data.set("filter", filter("object", notYet));

        ImportResult result = mapper.apply(data);

        assertThat(result).isEqualTo(ImportResult.awaitingParent(new AwaitedParent(ParentKind.CONTRAGENT, notYet)));
        verify(repository, never()).save(any());
    }

    @Test
    void recordOfTheMessage_isTheContragent() {
        assertThat(mapper.recordOf(contacts())).contains(SANATORIUM);
    }

    @Test
    void withoutFilter_theContragentIsTakenFromTheFirstRow() {
        ObjectNode data = contacts(address(1, ACTUAL, "Сочи, ул. Тестовая, 1", "Тестовый край"));
        data.set("filter", filter("kind", UUID.fromString("b910ecef-0000-4000-8000-000000000001")));

        ImportResult result = mapper.apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(sanatorium.address()).isEqualTo("Сочи, ул. Тестовая, 1");
    }

    @Test
    void messageWithoutContragent_isContractViolation() {
        ObjectNode data = contacts();
        data.remove("filter");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в сообщении контактной информации не найден GUID контрагента");
        verifyNoInteractions(repository);
    }

    /** Контакты санатория: GUID санатория в фильтре сообщения и строки в наборе. */
    private static ObjectNode contacts(ObjectNode... rows) {
        ObjectNode data = JSON.createObjectNode();
        data.set("filter", filter("object", SANATORIUM));
        ArrayNode recordSet = data.putArray("recordSet");
        List.of(rows).forEach(recordSet::add);
        return data;
    }

    private static ArrayNode filter(String fieldName, UUID value) {
        ArrayNode filter = JSON.createArrayNode();
        filter.addObject().put("fieldName", fieldName).putObject("value").put("value", value.toString());
        return filter;
    }

    private static ObjectNode address(int rowNumber, String kind, String representation, String region) {
        ObjectNode row = JSON.createObjectNode();
        row.put("rowNumber", rowNumber);
        row.put("type", "Адрес");
        row.put("notValid", false);
        row.put("representation", representation);
        row.put("field2", region);
        row.putObject("kind").put("representation", kind);
        row.putObject("object").put("type", "CatalogRef.Контрагенты").put("value", SANATORIUM.toString());
        return row;
    }
}
