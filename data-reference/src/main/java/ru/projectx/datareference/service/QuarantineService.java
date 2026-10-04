package ru.projectx.datareference.service;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.QuarantinedMessage;
import ru.projectx.datareference.entity.RejectedMessage;
import ru.projectx.datareference.repository.QuarantineRepository;

/**
 * Карантин сообщений 1С:УСК, тела которых база не принимает в сырьё.
 *
 * <p>Запись идёт в своей транзакции, и подтверждение брокеру уходит только после её фиксации: иначе
 * сообщение, подтверждённое, но не записанное, пропало бы без следа. Если подтверждение до брокера не дошло,
 * он пришлёт сообщение снова, и оно уже лежит в карантине — вторая запись не создаётся, брокеру достаточно
 * подтверждения.
 */
@Service
public class QuarantineService {

    private final QuarantineRepository repository;
    private final Clock clock;

    public QuarantineService(QuarantineRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Кладёт отвергнутое базой сообщение в карантин, если его там ещё нет.
     *
     * @param rejected сообщение с телом как прислал брокер
     * @param reason   код SQLSTATE отказа базы; текста ошибки здесь нет намеренно — он печатает значения строки
     * @return {@code true} — записано; {@code false} — уже лежало в карантине, это повторная доставка
     */
    @Transactional
    public boolean quarantine(RejectedMessage rejected, String reason) {
        if (repository.existsById(rejected.messageId())) {
            return false;
        }
        repository.save(QuarantinedMessage.of(rejected, reason, clock.instant()));
        return true;
    }
}
