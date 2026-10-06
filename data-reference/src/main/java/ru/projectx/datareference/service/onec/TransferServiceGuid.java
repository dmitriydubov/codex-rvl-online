package ru.projectx.datareference.service.onec;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.mapper.OneCGuid;
import ru.projectx.datareference.mapper.OneCValues;
import tools.jackson.databind.node.ObjectNode;

/**
 * GUID услуги в части передачи прайса (поле {@code keyValue}) — в теле пришедшей части и в значении, прочитанном из
 * сырья частей в буфере. Оба читаются по одному правилу: пробелы по краям не в счёт, GUID только в канонической записи.
 */
final class TransferServiceGuid {

    private static final String KEY_VALUE = "keyValue";

    private TransferServiceGuid() {
    }

    /** GUID услуги части, если он читается; иначе пусто. */
    static Optional<UUID> readableIn(ObjectNode part) {
        return OneCValues.text(part, KEY_VALUE).flatMap(OneCGuid::parse);
    }

    /**
     * Обязательный GUID услуги части; нулевой GUID — значения нет.
     *
     * @throws ru.projectx.datareference.exception.OneCFieldException если GUID нет или он записан не как GUID
     */
    static UUID requiredIn(ObjectNode part) {
        return OneCValues.requiredUuid(part, KEY_VALUE);
    }

    /** GUID услуги так, как он записан в сырье части; пусто, если поля нет или оно не GUID. */
    static Optional<UUID> parse(@Nullable String raw) {
        return Optional.ofNullable(raw).map(String::trim).flatMap(OneCGuid::parse);
    }
}
