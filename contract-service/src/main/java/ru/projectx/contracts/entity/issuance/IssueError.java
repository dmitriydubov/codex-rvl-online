package ru.projectx.contracts.entity.issuance;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record IssueError(
        @Column(name = "error_code") String code,
        @Column(name = "error_message") String message) {
}
