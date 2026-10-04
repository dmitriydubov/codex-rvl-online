package ru.projectx.datareference.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * Отличает временный отказ базы данных от всех прочих ошибок REST-чтения. Временный отказ — тот,
 * после которого тот же запрос стоит повторить чуть позже: сервисы Заявок и Договоров так и
 * делают, когда получают 503. Любая другая ошибка повтором не лечится и уходит как 500.
 *
 * <p>Временными считаются отказы из иерархии исключений Spring: нет соединения при входе
 * в транзакцию ({@link CannotCreateTransactionException}); соединение оборвалось или его не дал пул
 * ({@link DataAccessResourceFailureException}, {@link RecoverableDataAccessException}); оператор
 * отменён по таймауту или не дождался блокировки ({@link TransientDataAccessException}); истёк лимит
 * времени транзакции чтения ({@link TransactionTimedOutException}). Тот же истёкший лимит Hibernate
 * сообщает своим {@code org.hibernate.TransactionException} без причины из JDBC, и Spring заворачивает
 * его в {@link JpaSystemException}, не различая с другими сбоями транзакции. Для чтений REST такой
 * сбой всё равно временный, поэтому и он даёт 503.
 *
 * <p>Вместе с отказом наружу отдаётся его описание для журнала: тип исключения и код SQLSTATE
 * драйвера, если отказ пришёл из базы. Текст исключения в описание не входит: в нём SQL и сообщение
 * драйвера, а с ними могут прийти значения строки, в том числе персональные данные полиса.
 */
@Component
public class DatabaseOutages {

    private static final String NO_SQL_STATE = "нет";

    private static final List<Class<? extends RuntimeException>> TEMPORARY = List.of(
            CannotCreateTransactionException.class,
            TransactionTimedOutException.class,
            DataAccessResourceFailureException.class,
            RecoverableDataAccessException.class,
            TransientDataAccessException.class);

    /**
     * Временный отказ, если ошибка им является.
     *
     * @param failure ошибка, с которой закончился запрос
     * @return описание отказа для журнала или пусто, когда ошибка постоянная
     */
    public Optional<Outage> recognize(Exception failure) {
        if (!isTemporary(failure)) {
            return Optional.empty();
        }
        return Optional.of(new Outage(failure.getClass().getSimpleName(), sqlState(failure)));
    }

    private static boolean isTemporary(Throwable failure) {
        return TEMPORARY.stream().anyMatch(type -> type.isInstance(failure)) || isExpiredHibernateTransaction(failure);
    }

    private static boolean isExpiredHibernateTransaction(Throwable failure) {
        return failure instanceof JpaSystemException && failure.getCause() instanceof org.hibernate.TransactionException;
    }

    /**
     * Код SQLSTATE из цепочки причин. Он говорит, что именно случилось в базе: 55P03 — не дождались
     * блокировки, 57014 — оператор отменён по таймауту, класс 08 — проблема с соединением.
     */
    private static String sqlState(Throwable failure) {
        return Optional.of(NestedExceptionUtils.getMostSpecificCause(failure))
                .filter(SQLException.class::isInstance)
                .map(cause -> ((SQLException) cause).getSQLState())
                .orElse(NO_SQL_STATE);
    }

    /**
     * Что писать в журнал о временном отказе: тип исключения и SQLSTATE драйвера.
     *
     * @param type     простое имя класса исключения
     * @param sqlState код SQLSTATE или «нет», если в цепочке причин не было ошибки JDBC
     */
    public record Outage(String type, String sqlState) {
    }
}
