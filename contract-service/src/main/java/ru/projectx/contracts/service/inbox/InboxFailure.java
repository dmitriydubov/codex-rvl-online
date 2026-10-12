package ru.projectx.contracts.service.inbox;

import java.util.Optional;
import java.util.Set;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.SqlStates;

public sealed interface InboxFailure {

    Set<String> TRANSIENT_CLASSES = Set.of("08", "40", "53");
    Set<String> TRANSIENT_STATES = Set.of("55P03", "57014");

    String reason();

    String sqlState();

    static InboxFailure of(RuntimeException failure) {
        if (failure instanceof ContractViolationException violation) {
            return new Permanent(violation.getMessage(), SqlStates.NONE);
        }
        Optional<String> sqlState = SqlStates.deepest(failure);
        if (sqlState.filter(InboxFailure::transientState).isPresent()) {
            return new Transient(sqlState.get());
        }
        return sqlState.map(state -> new Permanent("отказ базы данных, SQLSTATE " + state, state))
                .orElseGet(() -> new Permanent("внутренняя ошибка обработки: " + failure.getClass().getName(),
                        SqlStates.NONE));
    }

    private static boolean transientState(String state) {
        return TRANSIENT_STATES.contains(state) || TRANSIENT_CLASSES.contains(state.substring(0, 2));
    }

    record Permanent(String reason, String sqlState) implements InboxFailure {
    }

    record Transient(String sqlState) implements InboxFailure {

        @Override
        public String reason() {
            return "временный отказ базы данных, SQLSTATE " + sqlState;
        }
    }
}
