package ru.projectx.datareference.service.onec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;

/**
 * Передача прайс-листа по одной услуге: во что сложились её отложенные сообщения и какой исход у них общий.
 *
 * <p>Прайс по услуге источник присылает несколькими пакетами строк и одним финальным сообщением, в котором
 * лежат счётчики: сколько пакетов и строк он отправил. Все сообщения передачи связаны общим идентификатором.
 * Передача бывает в одном из трёх состояний: ещё ждёт своих сообщений, пришла целиком, но счётчики не сошлись,
 * или собрана и готова к применению.
 *
 * <p>Здесь только решение и смена статусов сообщений, без обращений к базе. Сохраняет сообщения и применяет
 * прайс сборщик передачи.
 */
sealed interface PriceListTransfer {

    /**
     * Определяет состояние передачи по всем её сообщениям, которые сейчас лежат в буфере.
     *
     * <p>Финальное сообщение узнаётся по ожидаемому числу пакетов, которое есть только у него. Сообщения с уже
     * проставленным исходом в расчёт не идут.
     *
     * @param cid        идентификатор передачи
     * @param correlated все сообщения с этим идентификатором, в любом порядке и в любом статусе
     */
    static PriceListTransfer of(UUID cid, List<InboundMessage> correlated) {
        List<InboundMessage> buffered = correlated.stream()
                .filter(message -> message.status() == InboundStatus.BUFFERED)
                .toList();
        List<InboundMessage> batches = buffered.stream()
                .filter(message -> message.batchCount() == null)
                .toList();
        return buffered.stream()
                .filter(message -> message.batchCount() != null)
                .findFirst()
                .map(finalMessage -> withFinalMessage(cid, finalMessage, batches))
                .orElseGet(() -> new Pending("передача %s ждёт финального сообщения".formatted(cid)));
    }

    /** Пока пришли не все обещанные пакеты, сверять счётчики рано: порядок доставки двух очередей не гарантирован. */
    private static PriceListTransfer withFinalMessage(UUID cid, InboundMessage finalMessage, List<InboundMessage> batches) {
        int expectedBatches = Objects.requireNonNull(finalMessage.batchCount());
        if (batches.size() < expectedBatches) {
            return new Pending("передача %s собрана на %d из %d сообщений"
                    .formatted(cid, batches.size(), expectedBatches));
        }
        return checkedCounters(cid, finalMessage, batches);
    }

    /**
     * Лишний пакет или другое число строк означают, что источник прислал не то, что обещал. Применить такую
     * передачу значит записать заведомо неполный прайс и никогда об этом не узнать.
     */
    private static PriceListTransfer checkedCounters(UUID cid, InboundMessage finalMessage, List<InboundMessage> batches) {
        int expectedBatches = Objects.requireNonNull(finalMessage.batchCount());
        int expectedRows = Objects.requireNonNull(finalMessage.rowCount());
        int actualRows = batches.stream()
                .mapToInt(batch -> Objects.requireNonNullElse(batch.rowCount(), 0))
                .sum();
        if (batches.size() == expectedBatches && actualRows == expectedRows) {
            return new Assembled(finalMessage, batches);
        }
        String reason = "счётчики передачи %s не сошлись: ожидалось %d сообщений / %d строк, получено %d / %d"
                .formatted(cid, expectedBatches, expectedRows, batches.size(), actualRows);
        return new Mismatched(reason, wholeTransfer(finalMessage, batches));
    }

    /** Финальное сообщение первым, за ним пакеты: в этом порядке сообщения передачи получают исход. */
    private static List<InboundMessage> wholeTransfer(InboundMessage finalMessage, List<InboundMessage> batches) {
        List<InboundMessage> messages = new ArrayList<>();
        messages.add(finalMessage);
        messages.addAll(batches);
        return List.copyOf(messages);
    }

    /** Передача ещё не пришла целиком; её сообщения остаются в буфере. */
    record Pending(String reason) implements PriceListTransfer {
    }

    /** Передача пришла целиком, но не совпала со своими счётчиками. Прежний прайс по услуге не трогается. */
    record Mismatched(String reason, List<InboundMessage> messages) implements PriceListTransfer {

        /**
         * Переводит в ошибку все сообщения передачи сразу. Исход у передачи один: если пометить только последнее
         * пришедшее, остальные навсегда останутся в буфере.
         *
         * @return сообщения с новым статусом, которые осталось сохранить
         */
        List<InboundMessage> fail() {
            for (InboundMessage message : messages) {
                message.markFailed(reason);
            }
            return messages;
        }
    }

    /** Передача собрана: пришли финальное сообщение и все обещанные пакеты, счётчики сошлись. */
    record Assembled(InboundMessage finalMessage, List<InboundMessage> batches) implements PriceListTransfer {

        /**
         * Отмечает применёнными все сообщения передачи сразу.
         *
         * @return сообщения с новым статусом, которые осталось сохранить
         */
        List<InboundMessage> markProcessed(Instant processedAt) {
            List<InboundMessage> messages = wholeTransfer(finalMessage, batches);
            for (InboundMessage message : messages) {
                message.markProcessed(processedAt);
            }
            return messages;
        }

        /**
         * Отмечает пропущенными все сообщения передачи сразу, с одной причиной на всех.
         *
         * @return сообщения с новым статусом, которые осталось сохранить
         */
        List<InboundMessage> markSkipped(String reason, Instant processedAt) {
            List<InboundMessage> messages = wholeTransfer(finalMessage, batches);
            for (InboundMessage message : messages) {
                message.markSkipped(reason, processedAt);
            }
            return messages;
        }
    }
}
