package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.service.SqlStates;
import ru.projectx.datareference.service.onec.SupersededTransferClosure;

/**
 * Проверка многочастных передач прайсов, которые ждут своих частей дольше положенного: закрывает те, что уже не
 * могут стать последними по своей услуге, и предупреждает об остальных.
 *
 * <p>Зависшая передача ничем себя не проявляет: часть сообщений потерялась у источника, остальные лежат в буфере,
 * ошибок нет, а прайс по услуге остаётся вчерашним. Если по её услуге уже применена передача, которую она по порядку
 * не обгонит, она закрывается пропуском ({@link SupersededTransferClosure}). Об остальных — одно предупреждение с
 * идентификатором передачи, давностью и числом пришедших частей; дособрать их может только источник.
 */
@Component
public class StuckTransferAlert {

    private static final Logger log = LoggerFactory.getLogger(StuckTransferAlert.class);

    private final AgedInboundMessages agedMessages;
    private final SupersededTransferClosure closure;
    private final ImportProperties properties;
    private final Clock clock;

    public StuckTransferAlert(AgedInboundMessages agedMessages, SupersededTransferClosure closure,
            ImportProperties properties, Clock clock) {

        this.agedMessages = agedMessages;
        this.closure = closure;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Одна проверка: закрыть обогнанные передачи, все без предела, затем одно предупреждение об оставшихся или
     * молчание.
     */
    public void reportStuckTransfers() {
        Duration threshold = properties.stuckTransferThreshold();
        for (BufferedTransfer transfer : agedMessages.allTransfersBufferedLongerThan(threshold)) {
            closeIfSuperseded(transfer.getCorrelationId());
        }
        report(agedMessages.transfersBufferedLongerThan(threshold, properties.stuckTransferReportLimit()));
    }

    /**
     * Отказ базы при закрытии одной передачи не останавливает проверку: передача остаётся в буфере, о ней
     * предупредят, а следующая проверка попробует снова.
     */
    private void closeIfSuperseded(UUID cid) {
        try {
            closure.closeIfSuperseded(cid);
        }
        catch (DataAccessException failure) {
            log.warn("передачу прайса {} не удалось проверить на закрытие: {}, SQLSTATE {}; "
                            + "следующая проверка попробует снова",
                    cid, failure.getClass().getSimpleName(), SqlStates.deepestOrNone(failure));
        }
    }

    private void report(List<BufferedTransfer> stuck) {
        if (stuck.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        String transfers = stuck.stream()
                .map(transfer -> describe(transfer, now))
                .collect(Collectors.joining(", "));
        log.warn("передачи прайсов не собрались за {}: {}. Прайс по их услугам остался прежним; "
                        + "недостающие сообщения может прислать только источник{}",
                properties.stuckTransferThreshold(), transfers, limitNote(stuck.size()));
    }

    private static String describe(BufferedTransfer transfer, Instant now) {
        return "%s (ждёт %s, частей %d)".formatted(transfer.getCorrelationId(),
                Duration.between(transfer.getFirstPartReceivedAt(), now), transfer.getParts());
    }

    /** Предупреждение называет не больше заданного числа передач и говорит, когда за пределом могут быть ещё. */
    private String limitNote(int reported) {
        int limit = properties.stuckTransferReportLimit();
        return reported < limit ? "" : ". Названы %d самых давних, зависших может быть больше".formatted(limit);
    }
}
