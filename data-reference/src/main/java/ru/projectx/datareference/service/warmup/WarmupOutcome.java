package ru.projectx.datareference.service.warmup;

public record WarmupOutcome(WarmupResult result, String reason, int reads) {

    private static final String ALL_READS_DONE = "все чтения выполнены";
    private static final String NO_API_SERVER = "сервер API не запущен";

    public static WarmupOutcome completed(int reads) {
        return new WarmupOutcome(WarmupResult.COMPLETED, ALL_READS_DONE, reads);
    }

    public static WarmupOutcome interrupted(String reason, int reads) {
        return new WarmupOutcome(WarmupResult.INTERRUPTED, reason, reads);
    }

    public static WarmupOutcome withoutApiServer() {
        return new WarmupOutcome(WarmupResult.SKIPPED, NO_API_SERVER, 0);
    }
}
