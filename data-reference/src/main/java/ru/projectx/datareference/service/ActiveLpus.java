package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.LpuRepository;

@Component
public class ActiveLpus {

    private final LpuRepository lpus;

    public ActiveLpus(LpuRepository lpus) {
        this.lpus = lpus;
    }

    public Lpu byId(UUID lpuId) {
        return lpus.findById(lpuId)
                .filter(lpu -> !lpu.isArchive())
                .orElseThrow(() -> ReferenceNotFoundException.lpu(lpuId));
    }
}
