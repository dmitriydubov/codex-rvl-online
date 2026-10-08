package ru.projectx.datareference.service;

import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * Отличает временный отказ базы данных, после которого операцию стоит повторить (REST отвечает 503), от прочих ошибок.
 * Перечень временных отказов один на весь сервис: его спрашивают и REST, и разбор сообщений 1С:УСК. В описание отказа
 * для журнала не входит текст исключения: в нём SQL и значения строки, в том числе персональные данные полиса.
 */
@Component
public class DatabaseOutages {

    private static final List<Class<? extends RuntimeException>> TEMPORARY = List.of(
            CannotCreateTransactionException.class,
            TransactionTimedOutException.class,
            DataAccessResourceFailureException.class,
            RecoverableDataAccessException.class,
            TransientDataAccessException.class);

    /**
     * Истёкший лимит транзакции Hibernate и закрытый по простою сеанс Spring переводит в общий JpaSystemException,
     * поэтому их узнают отдельными правилами. Для чтения такой сбой временный: оно ничего не меняет, повтор безопасен.
     */
    private static final List<Predicate<Throwable>> TEMPORARY_RULES = List.of(
            DatabaseOutages::isTemporaryType,
            DatabaseOutages::isExpiredHibernateTransaction,
            DatabaseOutages::isSessionClosedForIdleTransaction);

    /** Сеанс закрыт за простой транзакции дольше idle_in_transaction_session_timeout: пул даст новое соединение. */
    private static final String IDLE_IN_TRANSACTION_SESSION_TIMEOUT = "25P03";

    private final PersistenceExceptionTranslator translator;

    public DatabaseOutages(PersistenceExceptionTranslator translator) {
        this.translator = translator;
    }

    /** Описание отказа для журнала, если ошибка временная; пусто, если повтор её не вылечит. */
    public Optional<Outage> recognize(Exception failure) {
        return translated(failure)
                .filter(DatabaseOutages::isTemporary)
                .map(DatabaseOutages::outageOf);
    }

    /**
     * Описание любого отказа базы, временного или постоянного; пусто, если ошибка пришла не из базы.
     * {@link IllegalStateException} и {@link IllegalArgumentException} отказом базы не считаются, хотя переводчик
     * узнаёт и их: такие исключения бросает любой код.
     */
    public Optional<Outage> describe(Exception failure) {
        if (failure instanceof DataAccessException || failure instanceof PersistenceException) {
            return translated(failure).map(DatabaseOutages::outageOf);
        }
        return recognize(failure);
    }

    private Optional<Throwable> translated(Exception failure) {
        if (!(failure instanceof RuntimeException runtime)) {
            return Optional.empty();
        }
        return Optional.of(translate(runtime));
    }

    private static Outage outageOf(Throwable classified) {
        return new Outage(typeOf(classified), sqlState(classified));
    }

    /** Обёртка истёкшего лимита Hibernate называется с причиной, иначе его не отличить от другого сбоя транзакции. */
    private static String typeOf(Throwable classified) {
        String type = classified.getClass().getSimpleName();
        if (isExpiredHibernateTransaction(classified)) {
            return type + "/" + classified.getCause().getClass().getSimpleName();
        }
        return type;
    }

    private Throwable translate(RuntimeException failure) {
        DataAccessException translated = translator.translateExceptionIfPossible(failure);
        return translated == null ? failure : translated;
    }

    private static boolean isTemporary(Throwable failure) {
        return TEMPORARY_RULES.stream().anyMatch(rule -> rule.test(failure));
    }

    private static boolean isTemporaryType(Throwable failure) {
        return TEMPORARY.stream().anyMatch(type -> type.isInstance(failure));
    }

    private static boolean isSessionClosedForIdleTransaction(Throwable failure) {
        return SqlStates.deepest(failure).filter(IDLE_IN_TRANSACTION_SESSION_TIMEOUT::equals).isPresent();
    }

    private static boolean isExpiredHibernateTransaction(Throwable failure) {
        return failure instanceof JpaSystemException && failure.getCause() instanceof org.hibernate.TransactionException;
    }

    private static String sqlState(Throwable failure) {
        return SqlStates.deepestOrNone(failure);
    }

    /** Запись журнала об отказе базы: тип исключения после перевода и SQLSTATE драйвера («нет», если его не было). */
    public record Outage(String type, String sqlState) {
    }
}
