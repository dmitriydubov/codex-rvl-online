package ru.projectx.contracts.dto;

import java.util.UUID;

/**
 * Общее у записей справочников: свой идентификатор сервиса справочных данных, идентификатор той же записи в 1С:УСК
 * и признак архива. По признаку клиент отличает живую запись от помеченной в 1С на удаление.
 */
public interface ReferenceRecord {

    UUID id();

    UUID externalId();

    boolean isArchive();
}
