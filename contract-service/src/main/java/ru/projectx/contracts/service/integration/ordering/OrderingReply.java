package ru.projectx.contracts.service.integration.ordering;

import tools.jackson.databind.JsonNode;

record OrderingReply(int status, JsonNode body) {
}
