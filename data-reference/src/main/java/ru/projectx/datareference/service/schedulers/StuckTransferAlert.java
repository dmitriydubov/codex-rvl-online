package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * «Передача ждёт своих частей дольше положенного» — обязательный присмотр за ТЗ-9.
 *
 * <p>Смысл в том, что зависшая передача ничем себя не проявляет. Часть сообщений
 * потерялась у источника, остальные лежат в буфере, ошибок нет, обработка идёт своим
 * чередом — а прайс по услуге остаётся вчерашним, и человек в личном кабинете видит
 * старую цену. Ни исключения, ни отказа при этом не возникает: единственный внешний
 * признак — давность.
 *
 * <p>Строки не трогаются: собрать передачу автоматически нельзя, дособрать её может
 * только источник. Задача этой проверки — сделать молчание заметным.
 */
@Component
public class StuckTransferAlert {

    private static final Logger log = LoggerFactory.getLogger(StuckTransferAlert.class);

    private final InboundMessageRepository repository;
    private final ImportProperties properties;
    private final Clock clock;

    public StuckTransferAlert(InboundMessageRepository repository, ImportProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${projectx.import.stuck-transfer-poll-interval:15m}")
    public void reportStuckTransfers() {
        Instant threshold = clock.instant().minus(properties.stuckTransferThreshold());
        List<InboundMessage> stuck = repository.findByStatusAndReceivedAtLessThan(InboundStatus.BUFFERED, threshold);
        if (stuck.isEmpty()) {
            return;
        }

        // По передачам, а не по сообщениям: у одной незавершённой передачи их могут быть
        // десятки, и строка на каждое сделала бы лог нечитаемым ровно тогда, когда его
        // читают.
        String transfers = stuck.stream()
                .map(InboundMessage::correlationId)
                .distinct()
                .map(UUID::toString)
                .collect(Collectors.joining(", "));

        log.warn("передачи прайсов не собрались за {}: {}. Прайс по их услугам остался прежним; "
                        + "недостающие сообщения может прислать только источник",
                properties.stuckTransferThreshold(), transfers);
    }
}
