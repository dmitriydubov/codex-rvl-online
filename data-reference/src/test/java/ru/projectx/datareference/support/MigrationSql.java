package ru.projectx.datareference.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * Операторы {@code sql} набора изменений ровно тем текстом, что лежит в миграции Liquibase.
 * Тесты заполнения выполняют сам текст миграции, а не его копию, поэтому правка миграции
 * сразу проверяется тестом.
 *
 * <p>Changelog разбирается как дерево YAML с проверкой типа каждого узла: если миграция
 * устроена не так, как ожидает тест, он падает с понятным сообщением, а не где-то дальше
 * на приведении типа.
 */
public final class MigrationSql {

    private MigrationSql() {
    }

    /**
     * Тексты операторов {@code sql} из раздела {@code changes} набора изменений в порядке миграции.
     *
     * @param changelog путь к changelog в classpath, например {@code db/changelog/017-….yaml}
     * @param changeSetId идентификатор набора изменений
     * @throws IllegalStateException если в миграции нет такого набора или он устроен иначе
     */
    public static List<String> statementsOf(String changelog, String changeSetId) {
        List<String> statements = sequence(field(read(changelog), "databaseChangeLog")).stream()
                .map(entry -> field(entry, "changeSet"))
                .filter(changeSet -> changeSetId.equals(field(changeSet, "id")))
                .flatMap(changeSet -> sequence(field(changeSet, "changes")).stream())
                .map(change -> text(field(field(change, "sql"), "sql")))
                .toList();
        if (statements.isEmpty()) {
            throw new IllegalStateException("в миграции " + changelog + " нет операторов набора " + changeSetId);
        }
        return statements;
    }

    private static Object read(String changelog) {
        try (InputStream content = new ClassPathResource(changelog).getInputStream()) {
            return new Yaml().load(content);
        }
        catch (IOException unreadable) {
            throw new IllegalStateException("миграция " + changelog + " не читается", unreadable);
        }
    }

    private static Object field(Object node, String name) {
        if (node instanceof Map<?, ?> mapping && mapping.get(name) instanceof Object value) {
            return value;
        }
        throw new IllegalStateException("в миграции нет поля " + name);
    }

    private static List<?> sequence(Object node) {
        if (node instanceof List<?> items) {
            return items;
        }
        throw new IllegalStateException("в миграции ожидался список, а не " + typeOf(node));
    }

    private static String text(Object node) {
        if (node instanceof String statement) {
            return statement;
        }
        throw new IllegalStateException("в миграции ожидался текст оператора, а не " + typeOf(node));
    }

    private static String typeOf(Object node) {
        return node.getClass().getSimpleName();
    }
}
