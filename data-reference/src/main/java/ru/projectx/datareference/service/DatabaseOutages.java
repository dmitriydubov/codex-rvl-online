package ru.projectx.datareference.service;

import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
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
 * Отличает временный отказ базы данных от всех прочих ошибок. Временный отказ — тот, после которого
 * ту же операцию стоит повторить чуть позже: сервисы Заявок и Договоров так и делают, когда получают
 * 503. Любая другая ошибка повтором не лечится и уходит как 500. Перечень временных отказов один на
 * весь сервис: кто бы ни решал, повторять ли операцию, он спрашивает здесь. Проверяемое исключение
 * отказом базы не бывает: отказы базы приходят только непроверяемыми исключениями Spring и Hibernate.
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
 * сбой всё равно временный, поэтому и он даёт 503: чтение ничего не меняет, и повтор безопасен.
 * Это сознательно широкое правило для чтений. Разбор сообщений 1С:УСК по расписанию спрашивает
 * этот же перечень, решая, повторить разбор или сразу отклонить сообщение. У транзакции разбора
 * лимита времени нет, поэтому истёкшего лимита там не бывает. Другой сбой транзакции Hibernate без
 * причины из JDBC разбор тоже повторит, а когда повторы кончатся, сообщение уйдёт в отказ.
 *
 * <p>Ещё один временный отказ узнаётся по коду SQLSTATE 25P03: сервер закрыл сеанс, потому что открытая
 * транзакция простаивала дольше {@code idle_in_transaction_session_timeout}. Пул заменит соединение, и та же
 * операция пройдёт. Ни Spring, ни Hibernate этот код не различают, и перевод даёт общий
 * {@link JpaSystemException}. Остальные коды класса 25 означают ошибку в обращении с транзакцией, и повтор
 * их не лечит, поэтому правило — ровно для 25P03.
 *
 * <p>Вместе с отказом наружу отдаётся его описание для журнала: тип исключения после перевода и код
 * SQLSTATE драйвера, если отказ пришёл из базы. Текст исключения в описание не входит: в нём SQL и
 * сообщение драйвера, а с ними могут прийти значения строки, в том числе персональные данные полиса.
 */
@Component
public class DatabaseOutages {

    private static final List<Class<? extends RuntimeException>> TEMPORARY = List.of(
            CannotCreateTransactionException.class,
            TransactionTimedOutException.class,
            DataAccessResourceFailureException.class,
            RecoverableDataAccessException.class,
            TransientDataAccessException.class);

    /** SQLSTATE закрытого по простою транзакции сеанса (PostgreSQL: idle_in_transaction_session_timeout). */
    private static final String IDLE_IN_TRANSACTION_SESSION_TIMEOUT = "25P03";

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
        return translated(failure)
                .filter(DatabaseOutages::isTemporary)
                .map(DatabaseOutages::outageOf);
    }

    /**
     * Описание любого отказа базы, временного или постоянного: тип после перевода и SQLSTATE. Постоянный отказ
     * базы — например, значение, которое не помещается в колонку, — повтором не лечится, но назвать его в журнале
     * нужно так же, без текста исключения.
     *
     * <p>Из базы ошибка пришла, если это исключение доступа к данным Spring или исключение JPA и Hibernate.
     * Переводчик называет ошибкой доступа к данным и обычные {@link IllegalStateException} и
     * {@link IllegalArgumentException}, считая их неверным вызовом JPA, но их бросает и любой другой код: ошибкой
     * базы они не считаются.
     *
     * @param failure ошибка, с которой закончилась операция
     * @return описание или пусто, если ошибка пришла не из базы
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

    /**
     * Тип для журнала. Обёртка Spring вокруг истёкшего лимита Hibernate называется вместе с причиной,
     * иначе в журнале не отличить истёкший лимит транзакции от другого сбоя транзакции.
     */
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
        return TEMPORARY.stream().anyMatch(type -> type.isInstance(failure))
                || isExpiredHibernateTransaction(failure)
                || isSessionClosedForIdleTransaction(failure);
    }

    private static boolean isSessionClosedForIdleTransaction(Throwable failure) {
        return SqlStates.deepest(failure).filter(IDLE_IN_TRANSACTION_SESSION_TIMEOUT::equals).isPresent();
    }

    private static boolean isExpiredHibernateTransaction(Throwable failure) {
        return failure instanceof JpaSystemException && failure.getCause() instanceof org.hibernate.TransactionException;
    }

    /** Код SQLSTATE отказа для журнала; правило, какой код в цепочке причин считается кодом отказа, — {@link SqlStates}. */
    private static String sqlState(Throwable failure) {
        return SqlStates.deepestOrNone(failure);
    }

    /**
     * Что писать в журнал об отказе базы: тип исключения и SQLSTATE драйвера.
     *
     * @param type     простое имя класса исключения после перевода; у обёртки истёкшего лимита Hibernate —
     *                 вместе с именем причины, через косую черту
     * @param sqlState код SQLSTATE или «нет», если в цепочке причин не было ошибки JDBC
     */
    public record Outage(String type, String sqlState) {
    }
}
