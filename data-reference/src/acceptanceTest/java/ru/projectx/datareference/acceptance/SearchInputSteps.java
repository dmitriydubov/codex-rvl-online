package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Запросы на границе пределов поиска: строка поиска из заданного числа символов и перечень МКБ-10 из заданного
 * числа кодов. Значение собирает шаг, а в запрос его кладёт {@link ContractsService} целиком закодированным,
 * поэтому сервер получает ровно столько символов и кодов, сколько написано в сценарии.
 *
 * <p>Строка поиска состоит из строчной кириллической буквы «ж»: в UTF-8 она занимает два байта, и сценарий
 * так отличает предел в символах от предела в байтах. Пробелы по краям проверяют, что длина считается до
 * обрезки. Коды перечня идут по порядку: латинская буква и две цифры ({@code A00}, {@code A01}, …,
 * {@code A99}, {@code B00}); таких кодов в справочнике сценария нет, поэтому принятый запрос получает 200.
 *
 * <p>Для запросов у предела длины строки запроса самого сервера строка поиска состоит из латинской буквы «q»: она
 * занимает один байт и не кодируется, поэтому длина запроса равна числу букв плюс путь.
 */
public class SearchInputSteps {

    private static final String QUERY = "query";
    private static final String CODES = "codes";
    private static final String LETTER = "ж";
    private static final String LATIN_LETTER = "q";
    private static final String EDGE_SPACE = " ";
    private static final int EDGE_SPACES = 2;
    private static final String CODE_SEPARATOR = ",";
    private static final int CODES_PER_LETTER = 100;
    private static final char FIRST_CODE_LETTER = 'A';
    private static final String CODE_FORMAT = "%c%02d";

    private final ContractsService contracts;

    public SearchInputSteps(ContractsService contracts) {
        this.contracts = contracts;
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с query из {int} букв «ж»")
    public void requestsQueryOfLetters(String path, int letters) {
        contracts.requestWithParameter(path, QUERY, LETTER.repeat(letters));
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с query из {int} букв «ж» и ещё одной")
    public void requestsQueryOfLettersAndOneMore(String path, int letters) {
        requestsQueryOfLetters(path, letters + 1);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с query из {int} латинских букв «q»")
    public void requestsQueryOfLatinLetters(String path, int letters) {
        contracts.requestWithParameter(path, QUERY, LATIN_LETTER.repeat(letters));
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с query из {int} символов: пробел, буквы «ж», пробел")
    public void requestsQueryWithEdgeSpaces(String path, int length) {
        String query = EDGE_SPACE + LETTER.repeat(length - EDGE_SPACES) + EDGE_SPACE;
        contracts.requestWithParameter(path, QUERY, query);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с query из {int} символов и ещё одного: пробел, буквы «ж», пробел")
    public void requestsQueryWithEdgeSpacesAndOneMore(String path, int length) {
        requestsQueryWithEdgeSpaces(path, length + 1);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с codes из {int} разных кодов")
    public void requestsDistinctCodes(String path, int count) {
        requestsCodes(path, distinctCodes(count));
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с codes из {int} разных кодов и ещё одного")
    public void requestsDistinctCodesAndOneMore(String path, int count) {
        requestsDistinctCodes(path, count + 1);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с codes из {int} кодов: разные коды и в конце повтор первого")
    public void requestsCodesEndingWithRepeat(String path, int count) {
        List<String> distinct = distinctCodes(count - 1);
        requestsCodes(path, Stream.concat(distinct.stream(), distinct.stream().limit(1)).toList());
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с codes из {int} кодов и ещё одного: разные коды и в конце повтор первого")
    public void requestsCodesEndingWithRepeatAndOneMore(String path, int count) {
        requestsCodesEndingWithRepeat(path, count + 1);
    }

    private void requestsCodes(String path, List<String> codes) {
        contracts.requestWithParameter(path, CODES, String.join(CODE_SEPARATOR, codes));
    }

    private static List<String> distinctCodes(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> CODE_FORMAT.formatted(
                        (char) (FIRST_CODE_LETTER + index / CODES_PER_LETTER), index % CODES_PER_LETTER))
                .toList();
    }
}
