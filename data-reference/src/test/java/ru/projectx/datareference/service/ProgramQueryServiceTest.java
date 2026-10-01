package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ProgramRepository;

/** R-09: программа с перечнем — одним запросом с {@code join fetch}; нет записи — 404 с кодом программы. */
@ExtendWith(MockitoExtension.class)
class ProgramQueryServiceTest {

    private static final UUID ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000001");
    private static final UUID EXTERNAL_ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000002");

    @Mock
    private ProgramRepository programs;

    @InjectMocks
    private ProgramQueryService service;

    @Test
    void byId_readsProgramWithServices() {
        when(programs.findWithServicesById(ID)).thenReturn(Optional.of(ReadFixtures.program(EXTERNAL_ID, false)));

        assertThat(service.byId(ID).externalId()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    void byId_unknown_isProgramNotFound() {
        when(programs.findWithServicesById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byId(ID))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("PROGRAM_NOT_FOUND"));
    }
}
