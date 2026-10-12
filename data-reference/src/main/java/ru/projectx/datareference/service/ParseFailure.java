package ru.projectx.datareference.service;

public sealed interface ParseFailure {

    String reason();

    String type();

    String sqlState();

    record Temporary(String reason, String type, String sqlState) implements ParseFailure {
    }

    record Permanent(String reason, String type, String sqlState) implements ParseFailure {
    }
}
