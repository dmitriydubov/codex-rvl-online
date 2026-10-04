package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Запросы к сырым сообщениям старше порога для двух задач по расписанию: срока хранения персональных данных
 * ({@link PersonalDataRetention}) и проверки зависших передач прайсов ({@link StuckTransferAlert}).
 *
 * <p>Обе задачи задают давность — «старше тридцати суток», «дольше часа», — а запросу нужен момент. Здесь
 * давность превращается в момент по часам сервиса: принято раньше, чем «сейчас минус порог». Часы
 * инъецируются, поэтому тесты управляют временем без ожидания. Какие строки и какой порог, решает сама
 * задача, там же объяснено почему.
 */
@Component
public class AgedInboundMessages {

    private final InboundMessageRepository repository;
    private final Clock clock;

    public AgedInboundMessages(InboundMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Стирает тела не больше {@code limit} строк потока, принятых раньше, чем {@code age} назад, начиная с самых
     * старых. Транзакция — вызывающего.
     *
     * @return сколько тел стёрто
     */
    public int forgetPayloads(OneCStream stream, Duration age, int limit) {
        return repository.forgetPayloads(stream, receivedBefore(age), limit);
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
