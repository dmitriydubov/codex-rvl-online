package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ProgramLpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;

@ExtendWith(MockitoExtension.class)
class ProgramQueryServiceTest {

    private static final UUID ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000001");
    private static final UUID EXTERNAL_ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000002");

    @Mock
    private ProgramRepository programs;

    @Mock
    private ProgramLpuRepository programRows;

    @InjectMocks
    private ProgramQueryService service;

    @Test
    void byId_readsProgram() {
        when(programs.findById(ID)).thenReturn(Optional.of(ReadFixtures.program(EXTERNAL_ID, false)));

        assertThat(service.byId(ID).externalId()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void byId_takesServicesInTheOrderOfTheOrderedQuery() {
        Program program = ReadFixtures.program(EXTERNAL_ID, false);
        program.replaceAvailableLpu(List.of(row("Санаторий «Бета»"), row("Санаторий «Альфа»")), ReadFixtures.NOW);
        when(programs.findById(ID)).thenReturn(Optional.of(program));
        when(programRows.findOrderedByProgramId(ID))
                .thenReturn(List.of(program.availableLpu().get(1), program.availableLpu().get(0)));

        ProgramResponse response = service.byId(ID);

        assertThat(response.services()).extracting(ProgramResponse.Service::lpuName)
                .containsExactly("Санаторий «Альфа»", "Санаторий «Бета»");
    }

    @Test
    void byId_unknown_isProgramNotFound() {
        when(programs.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byId(ID))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("PROGRAM_NOT_FOUND"));
        verifyNoInteractions(programRows);
    }

    @Test
    void byId_readsProgramAndServicesFromOneSnapshot() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource transactions = new AnnotationTransactionAttributeSource();
        transactions.setEmbeddedValueResolver(readTimeoutProperty -> "3");

        TransactionAttribute transaction = transactions.getTransactionAttribute(
                ProgramQueryService.class.getMethod("byId", UUID.class), ProgramQueryService.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(transaction.isReadOnly()).isTrue();
    }

    private static ProgramLpu.Row row(String lpuName) {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details(lpuName, null, null, null, null, null, false), ReadFixtures.NOW);
        return new ProgramLpu.Row(lpu, null, LocalDate.of(2026, 1, 1), null, 1, false, BigDecimal.ZERO,
                ProgramLpu.CoverageType.R);
    }
}
