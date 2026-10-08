package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разбор страховых программ: программа РВЛ берёт реквизиты и перечень учреждений из последнего сообщения,
 * чужая неизвестная уходит в пропуск, а потерявшая признак РВЛ становится архивной и теряет перечень.
 */
class ProgramMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID PROGRAM = UUID.fromString("80120842-f3d0-414e-a72e-ebca69765d33");
    private static final UUID PRODUCT = UUID.fromString("8dca0edf-c33b-11ed-9e50-0894ef5d42f9");
    private static final UUID PARENT = UUID.fromString("60c369bb-257c-46a1-a1e3-59cbca43d699");
    private static final UUID SANATORIUM = UUID.fromString("31bd3b09-5609-4321-bd8d-43ee2e7ab4ee");
    private static final UUID STRANGER = UUID.fromString("7e6f9e8b-a9f7-4333-813e-9a098ccbe33a");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final ProgramRepository repository = mock(ProgramRepository.class);
    private final LpuRepository lpuRepository = mock(LpuRepository.class);
    private final ProgramMapper mapper = new ProgramMapper(
            repository, new AvailableLpuReader(lpuRepository), Clock.fixed(NOW, ZoneOffset.UTC));
    private final Lpu sanatorium = Lpu.create(
            SANATORIUM, new Lpu.Details("Санаторий «Заря»", null, null, null, null, null, false), EARLIER);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void knownSanatoriumAndLog() {
        when(lpuRepository.findAllByExternalIdIn(anyCollection())).thenReturn(List.of(sanatorium));
        logged.start();
        mapperLog().addAppender(logged);
    }

    @AfterEach
    void detachLog() {
        mapperLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void streamIsPrograms() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.PROGRAMS);
    }

    @Test
    void newRvlProgram_isCreatedWithItsSanatoriums() {
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(program(true));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        Program saved = saved();
        assertThat(saved.externalId()).isEqualTo(PROGRAM);
        assertThat(saved.externalProductId()).isEqualTo(PRODUCT);
        assertThat(saved.externalParentId()).isEqualTo(PARENT);
        assertThat(saved.name()).isEqualTo("Восстановительное лечение");
        assertThat(saved.typeProgramm()).isEqualTo("Рисковый");
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.availableLpu()).singleElement()
                .extracting(ProgramLpu::lpu)
                .isSameAs(sanatorium);
    }

    /** Строка услуги без обязательного условия отклоняет сообщение до записи: частичной программы нет. */
    @Test
    void rvlProgramWithIncompleteRow_isRejectedWithoutSaving() {
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.empty());
        ObjectNode data = program(true);
        ((ObjectNode) data.path("service").get(0)).remove("percentCopayment");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля percentCopayment");
        verify(repository, never()).save(any());
    }

    @Test
    void programWithEmptyOptionalFields_isStoredWithoutThem() {
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.empty());
        ObjectNode data = program(true);
        data.put("idParent", "00000000-0000-0000-0000-000000000000");
        data.remove("idInsProduct");
        data.put("typeProgramm", "");

        mapper.apply(data);

        assertThat(saved().externalParentId()).isNull();
        assertThat(saved().externalProductId()).isNull();
        assertThat(saved().typeProgramm()).isNull();
    }

    @Test
    void newProgramWithoutRvl_isSkipped_andNotSaved() {
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(program(false));

        assertThat(result).isEqualTo(ImportResult.skipped("программа не РВЛ"));
        verify(repository, never()).save(any());
        verify(lpuRepository, never()).findAllByExternalIdIn(anyCollection());
        assertThat(logged.list).isEmpty();
    }

    @Test
    void knownRvlProgram_takesTheLastSnapshotAndList() {
        Program known = knownProgram();
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.of(known));
        ObjectNode data = program(true);
        data.put("deleteMark", true);

        ImportResult result = mapper.apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.name()).isEqualTo("Восстановительное лечение");
        assertThat(known.availableLpu()).hasSize(1);
        assertThat(known.isArchive()).isTrue();
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void knownProgramThatLostRvl_isArchived_andLosesItsSanatoriums() {
        Program known = knownProgram();
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.of(known));

        ImportResult result = mapper.apply(program(false));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.name()).isEqualTo("Восстановительное лечение");
        assertThat(known.isArchive()).isTrue();
        assertThat(known.availableLpu()).isEmpty();
        assertThat(known.updatedAt()).isEqualTo(NOW);
        verify(lpuRepository, never()).findAllByExternalIdIn(anyCollection());
        assertThat(logged.list).isEmpty();
    }

    @Test
    void droppedRows_areLoggedByReason() {
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.empty());
        ObjectNode data = program(true);
        data.withArray("service").addObject().put("idLPU", STRANGER.toString()).put("r", true);
        data.withArray("service").addObject().put("idLPU", SANATORIUM.toString());

        mapper.apply(data);

        assertThat(logged.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("программа " + PROGRAM + ": 2 из 3 строк перечня отброшено — "
                        + "1 с санаторием вне справочника, 1 без типа покрытия");
    }

    @Test
    void fullList_isNotLogged() {
        when(repository.findByExternalId(PROGRAM)).thenReturn(Optional.empty());

        mapper.apply(program(true));

        assertThat(logged.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name"})
    void messageWithoutRequiredField_isContractViolation_andNothingIsSaved(String field) {
        ObjectNode data = program(true);
        data.remove(field);

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" " + field);
        verify(repository, never()).save(any());
    }

    @Test
    void messageWithoutSeveralRequiredFields_namesTheGuidFirst() {
        ObjectNode data = program(true);
        data.remove("name");
        data.remove("id");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" id");
        verify(repository, never()).save(any());
    }

    private Program saved() {
        ArgumentCaptor<Program> saved = ArgumentCaptor.forClass(Program.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private Program knownProgram() {
        Program known = Program.create(PROGRAM, new Program.Details(null, null, "Старое название", null, false),
                EARLIER);
        var row = new ProgramLpu.Row(sanatorium, null, null, null, 1, false, BigDecimal.ONE,
                ProgramLpu.CoverageType.D);
        known.replaceAvailableLpu(List.of(row, row), EARLIER);
        return known;
    }

    private static Logger mapperLog() {
        return (Logger) LoggerFactory.getLogger(ProgramMapper.class);
    }

    private static ObjectNode program(boolean rvl) {
        ObjectNode data = JSON.createObjectNode();
        data.put("id", PROGRAM.toString());
        data.put("name", "Восстановительное лечение");
        data.put("typeProgramm", "Рисковый");
        data.put("rvl", rvl);
        data.put("idParent", PARENT.toString());
        data.put("idInsProduct", PRODUCT.toString());
        data.put("deleteMark", false);
        data.putArray("service").addObject()
                .put("idLPU", SANATORIUM.toString())
                .put("numberLayer", 1)
                .put("directAttachment", true)
                .put("percentCopayment", new BigDecimal("10.00"))
                .put("r", true);
        return data;
    }
}
