package ru.projectx.datareference.service.warmup;

import org.slf4j.event.Level;

public enum WarmupResult {
    COMPLETED("закончен", Level.INFO),
    INTERRUPTED("прерван", Level.WARN),
    SKIPPED("пропущен", Level.INFO);

    private final String word;
    private final Level level;

    WarmupResult(String word, Level level) {
        this.word = word;
        this.level = level;
    }

    public String word() {
        return word;
    }

    public Level level() {
        return level;
    }
}
