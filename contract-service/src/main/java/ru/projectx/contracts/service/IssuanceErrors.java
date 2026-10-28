package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.issuance.IssueError;

public record IssuanceErrors(IssueError undelivered, IssueError preparationFailed) {
}
