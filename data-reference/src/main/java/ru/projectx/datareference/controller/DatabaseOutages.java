package ru.projectx.datareference.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
 * Отличает временный отказ базы данных от всех прочих ошибок REST-чтения. Временный отказ — тот,
 * после которого тот же запрос стоит повторить чуть позже: сервисы Заявок и Договоров так и
 * делают, когда получают 503. Любая другая ошибка повтором не лечится и уходит как 500.
 *
 * <p>Сначала ошибка переводится в иерархию исключений Spring тем же переводчиком, которым
 * пользуются прокси репозиториев. Это нужно для исключений, которые пришли сырыми из Hibernate:
 * ленивая догрузка связей идёт в коде сервиса, мимо репозитория, и отмена оператора по таймауту
 * приходит оттуда как {@code org.hibernate.QueryTimeoutException}, а истёкший лимит транзакции —
 * как {@code org.hibernate.TransactionException}. Без перевода оба выглядели бы как внутренняя
 * ошибка. Исключение, которое переводчик не узнал, классифицируется как есть.
 *
 * <p>Временными считаются отказы из иерархии Spring: нет соединения при входе в транзакцию
 * ({@link CannotCreateTransactionException}); соединение оборвалось или его не дал пул
 * ({@link DataAccessResourceFailureException}, {@link RecoverableDataAccessException}); оператор
 * отменён по таймауту или не дождался блокировки ({@link TransientDataAccessException}); истёк лимит
 * времени транзакции чтения ({@link TransactionTimedOutException}). Тот же истёкший лимит Hibernate
 * сообщает своим {@code TransactionException} без причины из JDBC, и перевод даёт
 * {@link JpaSystemException}, не различая его с другими сбоями транзакции. Для чтений REST такой
 * сбой всё равно временный, поэтому и он даёт 503.
 *
 * <p>Вместе с отказом наружу отдаётся его описание для журнала: тип исключения после перевода и код
 * SQLSTATE драйвера, если отказ пришёл из базы. Текст исключения в описание не входит: в нём SQL и
 * сообщение драйвера, а с ними могут прийти значения строки, в том числе персональные данные полиса.
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

    private final PersistenceExceptionTranslator translator;

    public DatabaseOutages(PersistenceExceptionTranslator translator) {
        this.translator = translator;
    }

    /**
     * Временный отказ, если ошибка им является.
     *
     * @param failure ошибка, с которой закончился запрос
     * @return описание отказа для журнала или пусто, когда ошибка постоянная
     */
    public Optional<Outage> recognize(Exception failure) {
        Throwable classified = translate(failure);
        if (!isTemporary(classified)) {
            return Optional.empty();
        }
        return Optional.of(new Outage(classified.getClass().getSimpleName(), sqlState(classified)));
    }

    private Throwable translate(Exception failure) {
        if (!(failure instanceof RuntimeException runtime)) {
            return failure;
        }
        DataAccessException translated = translator.translateExceptionIfPossible(runtime);
        return translated == null ? failure : translated;
    }

    private static boolean isTemporary(Throwable failure) {
        return TEMPORARY.stream().anyMatch(type -> type.isInstance(failure)) || isExpiredHibernateTransaction(failure);
    }

    private static boolean isExpiredHibernateTransaction(Throwable failure) {
        return failure instanceof JpaSystemException && failure.getCause() instanceof org.hibernate.TransactionException;
    }

    /**
     * Код SQLSTATE самого глубокого {@link SQLException} в цепочке причин. Он говорит, что именно
     * случилось в базе: 55P03 — не дождались блокировки, 57014 — оператор отменён по таймауту,
     * класс 08 — проблема с соединением. Именно самого глубокого, а не корневой причины: при обрыве
     * соединения драйвер PostgreSQL кладёт код класса 08 поверх {@code IOException}, и у корня кода нет.
     */
    private static String sqlState(Throwable failure) {
        @Nullable String found = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                found = sql.getSQLState();
            }
        }
        return Optional.ofNullable(found).orElse(NO_SQL_STATE);
    }

    /**
     * Что писать в журнал о временном отказе: тип исключения и SQLSTATE драйвера.
     *
     * @param type     простое имя класса исключения после перевода
     * @param sqlState код SQLSTATE или «нет», если в цепочке причин не было ошибки JDBC
     */
    public record Outage(String type, String sqlState) {
    }
}
