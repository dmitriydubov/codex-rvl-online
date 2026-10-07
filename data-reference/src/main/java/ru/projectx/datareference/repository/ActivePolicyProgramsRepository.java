package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Программы восстановительного лечения, которые дают действующие полисы договора страхования: из них каталог
 * учреждений берёт перечни санаториев.
 */
public interface ActivePolicyProgramsRepository {

    /**
     * GUID программ из действующих строк условий обслуживания действующих полисов договора, найденных в справочнике
     * программ, без повторов. Правило «действует» то же, что у поиска полисов ({@link PolicySpecifications}).
     */
    List<UUID> findRvlProgramExternalIdsOfActivePolicies(UUID contractExternalId, LocalDate today);
}
