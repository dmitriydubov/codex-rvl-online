package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.repository.BufferedTransfer;

/**
 * Предупреждение о многочастных передачах прайсов, которые ждут своих частей дольше положенного.
 *
 * <p>Зависшая передача ничем себя не проявляет. Часть сообщений потерялась у источника, остальные лежат
 * в буфере, ошибок нет, обработка идёт своим чередом, а прайс по услуге остаётся вчерашним, и человек в
 * личном кабинете видит старую цену. Ни исключения, ни отказа при этом не возникает: единственный внешний
 * признак — давность.
 *
 * <p>Строки не трогаются: собрать передачу автоматически нельзя, дособрать её может только источник.
 * Задача этой проверки — сделать молчание заметным. В предупреждении — идентификатор передачи, сколько она
 * ждёт и сколько частей уже пришло; тела сообщений проверка не читает.
 */
@Component
public class StuckTransferAlert {

    private static final Logger log = LoggerFactory.getLogger(StuckTransferAlert.class);

    private final AgedInboundMessages agedMessages;
    private final ImportProperties properties;
    private final Clock clock;

    public StuckTransferAlert(AgedInboundMessages agedMessages, ImportProperties properties, Clock clock) {
        this.agedMessages = agedMessages;
        this.properties = properties;
        this.clock = clock;
    }

    /** Одна проверка: одно предупреждение на все зависшие передачи или молчание, если их нет. */
    public void reportStuckTransfers() {
        List<BufferedTransfer> stuck = agedMessages.transfersBufferedLongerThan(
                properties.stuckTransferThreshold(), properties.stuckTransferReportLimit());
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
