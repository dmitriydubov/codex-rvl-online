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
 * Запросы к принятым от 1С:УСК сообщениям старше порога для задач по расписанию: срока хранения персональных
 * данных ({@link PersonalDataRetention}) и проверки зависших передач прайсов ({@link StuckTransferAlert}).
 *
 * <p>Задачи задают давность — «старше тридцати суток», «дольше часа», — а запросу нужен момент. Здесь
 * давность превращается в момент по часам сервиса: принято раньше, чем «сейчас минус порог». Часы
 * инъецируются, поэтому тесты управляют временем без ожидания. Какие строки и какой порог, решает сама
 * задача, там же объяснено почему.
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
     * Стирает тела не больше {@code limit} сообщений с ограниченным сроком хранения, принятых раньше, чем
     * {@code age} назад, начиная с самых старых. Метод не открывает транзакцию: её открывает вызывающий.
     *
     * @return сколько тел стёрто
     */
    public int forgetPayloads(Duration age, int limit) {
        return repository.forgetPayloadsReceivedBefore(receivedBefore(age), limit);
    }

    /**
     * Стирает тела не больше {@code limit} карантинных сообщений, принятых раньше, чем {@code age} назад,
     * начиная с самых старых. Транзакцию открывает вызывающий.
     *
     * @return сколько тел стёрто
     */
    public int forgetQuarantineBodies(Duration age, int limit) {
        return quarantine.forgetBodies(receivedBefore(age), limit);
    }

    /**
     * Передачи прайсов, которые ждут своих частей дольше {@code age}: самые давние первыми, не больше
     * {@code limit}. Тела сообщений не читаются.
     */
    public List<BufferedTransfer> transfersBufferedLongerThan(Duration age, int limit) {
        return repository.findTransfersBufferedBefore(receivedBefore(age), Limit.of(limit));
    }

    private Instant receivedBefore(Duration age) {
        return clock.instant().minus(age);
    }
}
