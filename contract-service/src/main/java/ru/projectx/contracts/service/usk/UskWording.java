package ru.projectx.contracts.service.usk;

public record UskWording(
        String kind,
        String kindGenitive,
        String kindDative,
        String result,
        String notRequested,
        String postponed,
        String undeliveredUnchanged,
        String undeliveredErrored) {
}
