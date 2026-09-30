package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Сырьё старше порога — общая выборка двух присмотров за таблицей {@code inbound_message}:
 * срока хранения персональных данных ({@link PersonalDataRetention}) и зависших передач
 * ТЗ-9 ({@link StuckTransferAlert}).
 *
 * <p>Оба спрашивают одно и то же — какие строки приняты раньше, чем «сейчас минус порог», —
 * и различаются только тем, какие строки и какой порог. Это решает сам присмотр, там же
 * и объяснено почему; здесь — как порог становится моментом по часам сервиса. Часы
 * инъецируются, поэтому тесты управляют временем без ожидания.
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
     * Строки потока, у которых ещё лежит тело, принятые раньше, чем {@code age} назад.
     *
     * @param limit сколько строк вернуть за раз, не больше
     */
    public List<InboundMessage> withPayload(OneCStream stream, Duration age, int limit) {
        return repository.findByStreamAndPayloadIsNotNullAndReceivedAtLessThan(
                stream, receivedBefore(age), Limit.of(limit));
    }

    /** Строки в статусе {@code status}, принятые раньше, чем {@code age} назад. */
    public List<InboundMessage> inStatus(InboundStatus status, Duration age) {
        return repository.findByStatusAndReceivedAtLessThan(status, receivedBefore(age));
    }

    private Instant receivedBefore(Duration age) {
        return clock.instant().minus(age);
    }
}
