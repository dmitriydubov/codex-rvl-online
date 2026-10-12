package ru.projectx.datareference.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

public final class MigrationSql {

    private MigrationSql() {
    }

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
