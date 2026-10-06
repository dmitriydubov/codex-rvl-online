package ru.projectx.datareference.service.onec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.mapper.ImportResult;

/**
 * Передача прайс-листа по одной услуге: во что сложились её отложенные сообщения и какой исход у них общий.
 *
 * <p>Пакеты строк и финальное сообщение со счётчиками связаны общим идентификатором передачи. Передача ещё ждёт
 * своих сообщений, уже отклонена или пропущена, пришла целиком с несошедшимися счётчиками или собрана. Собранная
 * передача может ждать услугу, которой ещё нет в справочнике, и при пересмотре собирается заново из тех же частей.
 * Здесь только решение и смена статусов, без базы: сохраняет сообщения и применяет прайс сборщик передачи.
 */
sealed interface PriceListTransfer {

    /**
     * Состояние передачи по всем её сообщениям. Финальное узнаётся по ожидаемому числу пакетов; в сборку идут
     * только собранные сообщения ({@link #isCollected}).
     *
     * @param correlated все сообщения передачи {@code cid}, в любом порядке и в любом статусе
     */
    static PriceListTransfer of(UUID cid, List<InboundMessage> correlated) {
        List<InboundMessage> buffered = correlated.stream()
                .filter(PriceListTransfer::isCollected)
                .toList();
        if (isRejected(correlated)) {
            return new Rejected("передача %s уже отклонена".formatted(cid), buffered);
        }
        if (anyIn(correlated, InboundStatus.SKIPPED)) {
            return new Closed("передача %s уже пропущена".formatted(cid), buffered);
        }
        return assembling(cid, buffered);
    }

    /**
     * Часть передачи, исход которой ещё не записан: она в буфере или вместе со всей собранной передачей ждёт услугу,
     * которой пока нет в справочнике.
     */
    static boolean isCollected(InboundMessage part) {
        InboundStatus status = part.outcome().status();
        return status == InboundStatus.BUFFERED || status == InboundStatus.AWAITING_PARENT;
    }

    /** Исход передачи записан отказом или пропуском: её части, пришедшие позже, разделяют этот исход. */
    static boolean isClosed(List<InboundMessage> correlated) {
        return isRejected(correlated) || anyIn(correlated, InboundStatus.SKIPPED);
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

    /** Отклоняет все сообщения передачи с одной причиной; возвращает их же, чтобы сохранить. */
    private static List<InboundMessage> failAll(List<InboundMessage> messages, String reason) {
        for (InboundMessage message : messages) {
            message.markFailed(reason);
        }
        return messages;
    }

    /** Передача ещё не пришла целиком; её сообщения остаются в буфере. */
    record Pending(String reason) implements PriceListTransfer {
    }

    /** Передача уже отклонена; её части, пришедшие позже, отклоняются сразу и прайс не трогают. */
    record Rejected(String reason, List<InboundMessage> lateParts) implements PriceListTransfer {

        /** @return части с новым статусом, которые осталось сохранить */
        List<InboundMessage> fail() {
            return failAll(lateParts, reason);
        }
    }

    /** Передача уже пропущена; её части, пришедшие позже, пропускаются сразу и прайс не трогают. */
    record Closed(String reason, List<InboundMessage> lateParts) implements PriceListTransfer {

        /** @return части с новым статусом, которые осталось сохранить */
        List<InboundMessage> skip(Instant processedAt) {
            for (InboundMessage message : lateParts) {
                message.markSkipped(reason, processedAt);
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
            return failAll(wholeTransfer(finalMessage, batches), reason);
        }
    }

    /** Передача собрана: пришли финальное сообщение и все обещанные пакеты, счётчики сошлись. */
    record Assembled(InboundMessage finalMessage, List<InboundMessage> batches) implements PriceListTransfer {

        /**
         * Отмечает все сообщения передачи исходом применения прайса: применены, пропущены с его причиной или ждут
         * услугу, которой ещё нет.
         *
         * @return сообщения с новым статусом, которые осталось сохранить
         */
        List<InboundMessage> markOutcome(ImportResult result, Instant at) {
            return switch (result) {
                case ImportResult.Applied ignored -> markProcessed(at);
                case ImportResult.Skipped skipped -> markSkipped(skipped.reason(), at);
                case ImportResult.AwaitingParent awaiting -> markAwaitingParent(awaiting.parent(), at);
            };
        }

        /** Все сообщения передачи ждут услугу с одного момента: срок ожидания у передачи общий. */
        List<InboundMessage> markAwaitingParent(AwaitedParent service, Instant at) {
            List<InboundMessage> messages = wholeTransfer(finalMessage, batches);
            for (InboundMessage message : messages) {
                message.markAwaitingParent(service, null, at);
            }
            return messages;
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
