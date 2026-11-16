package ru.projectx.contracts.service.payment.cancellation;

sealed interface CancelResult {

    record Cancelled(String orderId) implements CancelResult {
    }

    record NotFound() implements CancelResult {
    }

    record Skipped() implements CancelResult {
    }
}
