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
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.LpuRepository;

@ExtendWith(MockitoExtension.class)
class ActiveLpusTest {

    private static final UUID ID = UUID.fromString("f2000000-0000-4000-8000-000000000001");

    @Mock
    private LpuRepository lpus;

    @InjectMocks
    private ActiveLpus activeLpus;

    @Test
    void active_isFound() {
        Lpu lpu = ReadFixtures.lpu(ID, false);
        when(lpus.findById(ID)).thenReturn(Optional.of(lpu));

        assertThat(activeLpus.byId(ID)).isSameAs(lpu);
    }

    @Test
    void archived_isNotFound() {
        when(lpus.findById(ID)).thenReturn(Optional.of(ReadFixtures.lpu(ID, true)));

        assertThatThrownBy(() -> activeLpus.byId(ID))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("LPU_NOT_FOUND"));
    }

    @Test
    void missing_isNotFound() {
        when(lpus.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activeLpus.byId(ID)).isInstanceOf(ReferenceNotFoundException.class);
    }
}
