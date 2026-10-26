package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.IssueError;

public record IssuanceErrors(IssueError undelivered, IssueError preparationFailed) {
}
