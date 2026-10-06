package ru.projectx.datareference.service.schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ParentWaitProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.metrics.ParentWaitMetrics;
import ru.projectx.datareference.service.onec.ExpiredParentWait;
import ru.projectx.datareference.service.onec.ParentWaitExpiry;

/**
 * Шаг прохода разбора: отклоняет сообщения 1С, родитель которых не пришёл за срок ожидания, и пишет о них журнал и
 * метрику после фиксации отказа.
 *
 * <p>Таких сообщений при массовой выгрузке тысячи: 1С присылает записи всех контрагентов, и дети тех, кто нашими
 * потоками не приходит, истекают пачками. Поэтому журнал получает одну сводную запись ERROR на поток за проход: поток,
 * число отклонённых и первые GUID родителей, без значений из тел сообщений. Передачу прайса дежурный разбирает
 * целиком, и о ней запись своя, с её идентификатором. Счётчик считает каждое сообщение.
 */
@Component
public class OverdueParentWaits {

    private static final Logger log = LoggerFactory.getLogger(OverdueParentWaits.class);

    private final ParentWaitExpiry expiry;
    private final ParentWaitMetrics metrics;
    private final ParentWaitProperties properties;

    public OverdueParentWaits(ParentWaitExpiry expiry, ParentWaitMetrics metrics, ParentWaitProperties properties) {
        this.expiry = expiry;
        this.metrics = metrics;
        this.properties = properties;
    }

    /**
     * Отклоняет просроченные ожидания своей транзакцией, затем пишет журнал и считает отклонённые сообщения: запись
     * появляется, только когда отказ зафиксирован.
     */
    public void rejectAndReport() {
        report(expiry.expireOverdue());
    }

    /** Обычный цикл, а не стрим: каждый шаг увеличивает счётчик. */
    private void report(List<ExpiredParentWait> expired) {
        for (ExpiredParentWait message : expired) {
            metrics.expired(message.stream());
        }
        reportTransfers(expired);
        reportStreams(expired);
    }

    private void reportTransfers(List<ExpiredParentWait> expired) {
        Map<UUID, List<ExpiredParentWait>> transfers = expired.stream()
                .filter(message -> message.transfer() != null)
                .collect(Collectors.groupingBy(message -> Objects.requireNonNull(message.transfer()),
                        LinkedHashMap::new, Collectors.toList()));
        transfers.forEach((transfer, parts) -> log.error(
                "передача прайса {} не дождалась услуги {} за срок ожидания {}: отклонено сообщений {}",
                transfer, parts.getFirst().parent().id(), properties.parentWaitTimeout(), parts.size()));
    }

    private void reportStreams(List<ExpiredParentWait> expired) {
        Map<OneCStream, List<ExpiredParentWait>> streams = expired.stream()
                .filter(message -> message.transfer() == null)
                .collect(Collectors.groupingBy(ExpiredParentWait::stream, LinkedHashMap::new, Collectors.toList()));
        streams.forEach((stream, messages) -> log.error(
                "сообщения потока {} не дождались родителя за срок ожидания {}: отклонено {}, первые родители: {}",
                stream, properties.parentWaitTimeout(), messages.size(), firstParents(messages)));
    }

    private List<UUID> firstParents(List<ExpiredParentWait> messages) {
        return messages.stream()
                .map(message -> message.parent().id())
                .distinct()
                .limit(properties.parentWaitReportLimit())
                .toList();
    }
}
