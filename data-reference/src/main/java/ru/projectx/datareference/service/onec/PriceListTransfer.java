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
 * <p>Пакеты строк и финальное сообщение со счётчиками связаны общим идентификатором передачи. Передача ещё ждёт
 * своих сообщений, уже отклонена, пришла целиком с несошедшимися счётчиками или собрана. Здесь только решение и
 * смена статусов, без базы: сохраняет сообщения и применяет прайс сборщик передачи.
 */
sealed interface PriceListTransfer {

    /**
     * Состояние передачи по всем её сообщениям. Финальное узнаётся по ожидаемому числу пакетов; в сборку идут
     * только сообщения в буфере.
     *
     * @param correlated все сообщения передачи {@code cid}, в любом порядке и в любом статусе
     */
    static PriceListTransfer of(UUID cid, List<InboundMessage> correlated) {
        List<InboundMessage> buffered = correlated.stream()
                .filter(message -> message.outcome().status() == InboundStatus.BUFFERED)
                .toList();
        if (isRejected(correlated)) {
            return new Rejected("передача %s уже отклонена".formatted(cid), buffered);
        }
        return assembling(cid, buffered);
    }

    /**
     * Передача отклонена, если среди её частей есть отказ, а применённых нет: у применённой передачи отказ бывает
     * только у лишнего пакета.
     */
    static boolean isRejected(List<InboundMessage> correlated) {
        return anyIn(correlated, InboundStatus.FAILED) && !anyIn(correlated, InboundStatus.PROCESSED);
    }

    private static boolean anyIn(List<InboundMessage> messages, InboundStatus status) {
        return messages.stream().anyMatch(message -> message.outcome().status() == status);
    }

    private static PriceListTransfer assembling(UUID cid, List<InboundMessage> buffered) {
        List<InboundMessage> batches = buffered.stream()
                .filter(message -> message.transferPart().batchCount() == null)
                .toList();
        return buffered.stream()
                .filter(message -> message.transferPart().batchCount() != null)
                .findFirst()
                .map(finalMessage -> withFinalMessage(cid, finalMessage, batches))
                .orElseGet(() -> new Pending("передача %s ждёт финального сообщения".formatted(cid)));
    }

    /** Пока пришли не все обещанные пакеты, сверять счётчики рано: порядок доставки двух очередей не гарантирован. */
    private static PriceListTransfer withFinalMessage(
            UUID cid, InboundMessage finalMessage, List<InboundMessage> batches) {

        int expectedBatches = Objects.requireNonNull(finalMessage.transferPart().batchCount());
        if (batches.size() < expectedBatches) {
            return new Pending("передача %s собрана на %d из %d сообщений"
                    .formatted(cid, batches.size(), expectedBatches));
        }
        return checkedCounters(cid, finalMessage, batches, expectedBatches);
    }

    /**
     * Лишний пакет или другое число строк означают, что источник прислал не то, что обещал. Применить такую
     * передачу значит записать заведомо неполный прайс и никогда об этом не узнать.
     */
    private static PriceListTransfer checkedCounters(
            UUID cid, InboundMessage finalMessage, List<InboundMessage> batches, int expectedBatches) {

        int expectedRows = Objects.requireNonNull(finalMessage.transferPart().rowCount());
        int actualRows = batches.stream()
                .mapToInt(batch -> Objects.requireNonNullElse(batch.transferPart().rowCount(), 0))
                .sum();
        if (batches.size() == expectedBatches && actualRows == expectedRows) {
            return new Assembled(finalMessage, batches);
        }
        String reason = "счётчики передачи %s не сошлись: ожидалось %d сообщений / %d строк, получено %d / %d"
                .formatted(cid, expectedBatches, expectedRows, batches.size(), actualRows);
        return new Mismatched(reason, finalMessage, batches);
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

    /** Передача уже отклонена; её части, пришедшие позже, отклоняются сразу и прайс не трогают. */
    record Rejected(String reason, List<InboundMessage> lateParts) implements PriceListTransfer {

        /** @return части с новым статусом, которые осталось сохранить */
        List<InboundMessage> fail() {
            for (InboundMessage message : lateParts) {
                message.markFailed(reason);
            }
            return lateParts;
        }
    }

    /** Передача пришла целиком, но не совпала со своими счётчиками. Прежний прайс по услуге не трогается. */
    record Mismatched(String reason, InboundMessage finalMessage, List<InboundMessage> batches)
            implements PriceListTransfer {

        /**
         * Переводит в ошибку все сообщения передачи сразу. Исход у передачи один: если пометить только последнее
         * пришедшее, остальные навсегда останутся в буфере.
         *
         * @return сообщения с новым статусом, которые осталось сохранить
         */
        List<InboundMessage> fail() {
            List<InboundMessage> messages = wholeTransfer(finalMessage, batches);
            for (InboundMessage message : messages) {
                message.markFailed(reason);
            }
            return messages;
        }
    }

    /** Передача собрана: пришли финальное сообщение и все обещанные пакеты, счётчики сошлись. */
    record Assembled(InboundMessage finalMessage, List<InboundMessage> batches) implements PriceListTransfer {

        /**
         * Отмечает все сообщения передачи исходом применения прайса: применены или пропущены с его причиной.
         *
         * @return сообщения с новым статусом, которые осталось сохранить
         */
        List<InboundMessage> markOutcome(ImportResult result, Instant at) {
            return switch (result) {
                case ImportResult.Applied ignored -> markProcessed(at);
                case ImportResult.Skipped skipped -> markSkipped(skipped.reason(), at);
            };
        }

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
