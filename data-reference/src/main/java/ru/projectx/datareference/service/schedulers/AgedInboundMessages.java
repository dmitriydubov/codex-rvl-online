package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.QuarantineRepository;

/**
 * Запросы к принятым от 1С:УСК сообщениям старше порога для задач по расписанию: стирания персональных данных и
 * проверки зависших передач прайсов. Задача задаёт давность, а здесь она превращается в момент по часам сервиса,
 * которыми тесты управляют без ожидания.
 */
@Component
public class AgedInboundMessages {

    private final InboundMessageRepository repository;
    private final QuarantineRepository quarantine;
    private final Clock clock;

    public AgedInboundMessages(InboundMessageRepository repository, QuarantineRepository quarantine, Clock clock) {
        this.repository = repository;
        this.quarantine = quarantine;
        this.clock = clock;
    }

    /**
     * Стирает тела сообщений с ограниченным сроком хранения, начиная с самых старых. Транзакцию открывает вызывающий.
     *
     * @return сколько тел стёрто
     */
    public int forgetPayloads(Duration age, int limit) {
        return repository.forgetPayloadsReceivedBefore(receivedBefore(age), limit);
    }

    /**
     * Стирает тела карантинных сообщений, начиная с самых старых. Транзакцию открывает вызывающий.
     *
     * @return сколько тел стёрто
     */
    public int forgetQuarantineBodies(Duration age, int limit) {
        return quarantine.forgetBodies(receivedBefore(age), limit);
    }

    /** Передачи прайсов, которые ждут своих частей дольше {@code age}, самые давние первыми, без чтения тел. */
    public List<BufferedTransfer> transfersBufferedLongerThan(Duration age, int limit) {
        return repository.findTransfersBufferedBefore(receivedBefore(age), Limit.of(limit));
    }

    /**
     * Все зависшие передачи без предела, самые давние первыми: проверка закрывает обогнанные из них, сколько бы их ни
     * было. Тела сообщений не читаются.
     */
    public List<BufferedTransfer> allTransfersBufferedLongerThan(Duration age) {
        return repository.findTransfersBufferedBefore(receivedBefore(age), Limit.unlimited());
    }

    private Instant receivedBefore(Duration age) {
        return clock.instant().minus(age);
    }
}
