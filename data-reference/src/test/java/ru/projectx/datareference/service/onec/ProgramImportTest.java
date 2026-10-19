package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;
import static ru.projectx.datareference.support.OneCMessages.sanatorium;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.catalog.ProgramLpu;
import ru.projectx.datareference.mapper.stream.ProgramMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@Transactional
@IntegrationTest
class ProgramImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_programms-changes";
    private static final UUID PROGRAM_ID = UUID.fromString("7d3a9e60-0000-4000-8000-000000000001");
    private static final UUID SANATORIUM_ID = UUID.fromString("7d3a9e60-0000-4000-8000-00000000000a");
    private static final UUID STRANGER_ID = UUID.fromString("7d3a9e60-0000-4000-8000-0000000000ff");

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private LpuRepository lpuRepository;

    @Autowired
    private ProgramRepository programRepository;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void seedSanatorium() {
        databaseCleaner.clear();
        logged.start();
        mapperLog().addAppender(logged);
        importOne(OneCStream.CONTRAGENTS, sanatorium(SANATORIUM_ID, "Санаторий «Заря»"));
    }

    @AfterEach
    void detachAppender() {
        mapperLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void rvlProgram_isStoredWithItsSanatoriums() {
        importOne(OneCStream.PROGRAMS, program(true, row(SANATORIUM_ID, "r")));

        Program stored = programRepository.findByExternalId(PROGRAM_ID).orElseThrow();
        assertThat(stored.name()).isEqualTo("Санаторно-курортное лечение");
        assertThat(stored.availableLpu()).hasSize(1);
        assertThat(stored.availableLpu().getFirst().coverageType()).isEqualTo(ProgramLpu.CoverageType.R);
        assertThat(stored.availableLpu().getFirst().percentCopayment()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void nonRvlProgram_isSkipped() {
        ImportOutcome result = importOne(OneCStream.PROGRAMS, program(false, row(SANATORIUM_ID, "r")));

        assertThat(result).isInstanceOf(ImportOutcome.Skipped.class);
        assertThat(programRepository.count()).isZero();
    }

    @Test
    void tablePart_isReplacedEntirely() {
        importOne(OneCStream.PROGRAMS, program(true, row(SANATORIUM_ID, "r")));

        importOne(OneCStream.PROGRAMS, program(true, ""));

        assertThat(programRepository.findByExternalId(PROGRAM_ID).orElseThrow().availableLpu()).isEmpty();
    }

    @Test
    void rowWithUnknownSanatorium_isDropped() {
        importOne(OneCStream.PROGRAMS, program(true, row(STRANGER_ID, "r")));

        assertThat(programRepository.findByExternalId(PROGRAM_ID).orElseThrow().availableLpu()).isEmpty();
    }

    @Test
    void rowWithoutCoverageType_isDropped() {
        importOne(OneCStream.PROGRAMS, program(true, row(SANATORIUM_ID, null)));

        assertThat(programRepository.findByExternalId(PROGRAM_ID).orElseThrow().availableLpu()).isEmpty();
    }

    @Test
    void droppedRows_areCountedByReasonSeparately() {
        String bothReasons = row(STRANGER_ID, "r") + "," + row(SANATORIUM_ID, null);

        importOne(OneCStream.PROGRAMS, program(true, bothReasons));

        assertThat(droppedRowLines())
                .anySatisfy(line -> assertThat(line)
                        .contains("2 из 2 строк перечня отброшено")
                        .contains("1 с санаторием вне справочника")
                        .contains("1 без типа покрытия"));
    }

    @Test
    void programLosingRvl_isArchivedAndLosesItsSanatoriums() {
        importOne(OneCStream.PROGRAMS, program(true, row(SANATORIUM_ID, "r")));

        importOne(OneCStream.PROGRAMS, program(false, row(SANATORIUM_ID, "r")));

        Program stored = programRepository.findByExternalId(PROGRAM_ID).orElseThrow();
        assertThat(stored.isArchive()).isTrue();
        assertThat(stored.availableLpu()).isEmpty();
    }

    private static ch.qos.logback.classic.Logger mapperLog() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ProgramMapper.class);
    }

    private List<String> droppedRowLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private ImportOutcome importOne(OneCStream stream, String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private static String program(boolean rvl, String rows) {
        return """
                {"id":"%s","rvl":%b,"name":"Санаторно-курортное лечение","typeProgramm":"СКЛ",\
                "service":[%s]}"""
                .formatted(PROGRAM_ID, rvl, rows);
    }

    private static String row(UUID sanatoriumId, String coverage) {
        String flag = coverage == null ? "" : ",\"%s\":true".formatted(coverage);
        return """
                {"idLPU":"%s","numberLayer":1,"percentCopayment":10.00,"directAttachment":true%s}"""
                .formatted(sanatoriumId, flag);
    }
}
