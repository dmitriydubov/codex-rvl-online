import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Gherkin acceptance mutation for Cucumber-JVM projects.
 *
 * <p>Follows the idea of unclebob/Acceptance-Pipeline-Specification: mutate one Examples value
 * at a time and expect the acceptance tests to fail. A mutant that still passes ("survives")
 * means the step that consumes that value does not really check it - the classic hollow step
 * definition. Only Examples values are mutated, never step text or literals, so every value
 * that matters must be an Examples parameter.
 *
 * <pre>
 * java GherkinMutator.java generate --features src/acceptanceTest/resources/features \
 *      --out build/gherkin-mutation/mutants [--only a.feature,b.feature]
 * java GherkinMutator.java verdict --mutants build/gherkin-mutation/mutants \
 *      --results build/gherkin-mutation/results
 * </pre>
 * {@code generate} writes one feature file per mutant (a plain Scenario with the Background
 * and the substituted steps) plus manifest.tsv. Run them with Cucumber, then {@code verdict}
 * reads the JUnit XML results: failed = killed (an undefined step for the new value is reported as a
 * failure and counted separately), passed = survived, skipped/aborted or missing = error.
 * {@code no-skipped} fails when a normal acceptance run skipped scenarios, or ran none although
 * {@code --features} holds feature files; without feature files it passes without reading any results.
 * {@code generate} fails when a feature file in scope has scenarios but no Examples value to mutate:
 * gherkinMutation would otherwise pass it without having checked anything.
 * Supports English and Russian keywords ("# language: ru").
 * Exit codes: 0 = OK / all killed, 1 = survivors or errors, 2 = usage or IO error.
 */
public class GherkinMutator {

    // ---------------------------------------------------------------- keywords

    record Keywords(List<String> feature, List<String> background, List<String> rule, List<String> scenario,
                    List<String> outline, List<String> examples, String plainScenario) {
    }

    static final Map<String, Keywords> LANGUAGES = Map.of(
            "en", new Keywords(List.of("Feature", "Business Need", "Ability"), List.of("Background"), List.of("Rule"),
                    List.of("Example", "Scenario"), List.of("Scenario Outline", "Scenario Template"),
                    List.of("Examples", "Scenarios"), "Scenario"),
            "ru", new Keywords(List.of("Функция", "Функциональность", "Функционал", "Свойство", "Фича"),
                    List.of("Предыстория", "Контекст"), List.of("Правило"), List.of("Пример", "Сценарий"),
                    List.of("Структура сценария", "Шаблон сценария"), List.of("Примеры", "Значения"), "Сценарий"));

    enum Kw { FEATURE, BACKGROUND, RULE, SCENARIO, EXAMPLES }

    // ---------------------------------------------------------------- model

    static final class Examples {
        final List<String> tags;
        final int line;
        List<String> headerRaw;
        final List<List<String>> rowsRaw = new ArrayList<>();
        final List<Integer> rowLines = new ArrayList<>();

        Examples(List<String> tags, int line) {
            this.tags = tags;
            this.line = line;
        }
    }

    static final class Rule {
        final List<String> tags;
        final String header;
        final List<String> background = new ArrayList<>();

        Rule(List<String> tags, String header) {
            this.tags = tags;
            this.header = header;
        }
    }

    static final class Scenario {
        final List<String> tags;
        final String name;
        final int line;
        final Rule rule;
        final List<String> body = new ArrayList<>();
        final List<Examples> examples = new ArrayList<>();

        Scenario(List<String> tags, String name, int line, Rule rule) {
            this.tags = tags;
            this.name = name;
            this.line = line;
            this.rule = rule;
        }
    }

    static final class FeatureDoc {
        String path;
        String language = "en";
        String languageLine;
        List<String> tags = new ArrayList<>();
        String header;
        String name = "";
        final List<String> background = new ArrayList<>();
        final List<Scenario> scenarios = new ArrayList<>();
    }

    record Mutant(String id, FeatureDoc feature, Scenario scenario, Examples examples, int row, String key,
                  String original, String mutated) {
    }

    // ---------------------------------------------------------------- entry points

    public static void main(String[] args) {
        System.exit(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)));
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println("usage: java GherkinMutator.java generate|verdict [options]");
            return 2;
        }
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            opts.put(args[i], args[i + 1]);
        }
        try {
            return switch (args[0]) {
                case "generate" -> generate(Path.of(opts.getOrDefault("--features", "src/acceptanceTest/resources/features")),
                        Path.of(opts.getOrDefault("--out", "build/gherkin-mutation/mutants")),
                        opts.containsKey("--only") ? csv(opts.get("--only")) : null, out);
                case "verdict" -> verdict(Path.of(opts.getOrDefault("--mutants", "build/gherkin-mutation/mutants")),
                        Path.of(opts.getOrDefault("--results", "build/gherkin-mutation/results")), out);
                case "no-skipped" -> noSkipped(Path.of(opts.getOrDefault("--results", "build/test-results/acceptanceTest")),
                        opts.containsKey("--features") ? Path.of(opts.get("--features")) : null, out);
                default -> {
                    err.println("GherkinMutator: unknown command " + args[0]);
                    yield 2;
                }
            };
        } catch (IOException | RuntimeException e) {
            err.println("GherkinMutator: " + e);
            return 2;
        }
    }

    // ---------------------------------------------------------------- generate

    static int generate(Path featuresDir, Path outDir, List<String> only, PrintStream out) throws IOException {
        deleteRecursively(outDir);
        Files.createDirectories(outDir);
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(featuresDir)) {
            try (Stream<Path> s = Files.walk(featuresDir)) {
                files = s.filter(p -> p.toString().endsWith(".feature")).sorted().toList();
            }
        }
        List<Mutant> mutants = new ArrayList<>();
        int inScope = 0;
        int unreadable = 0;
        int vacuous = 0;
        for (Path file : files) {
            if (only != null && !matchesAny(file, only)) {
                continue;
            }
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
                lines.set(0, lines.get(0).substring(1));   // UTF-8 byte order mark
            }
            if (lines.stream().map(String::trim).allMatch(l -> l.isEmpty() || l.startsWith("#"))) {
                continue;   // empty or comment-only feature file: valid for Cucumber, nothing to mutate
            }
            inScope++;
            FeatureDoc doc = parse(file, lines);
            if (doc.header == null) {
                unreadable++;
                out.println("GHERKIN ERROR " + file + ": no recognised Feature keyword (language '" + doc.language
                        + "'; supported: " + LANGUAGES.keySet() + " - add the keywords to GherkinMutator.LANGUAGES)");
                continue;
            }
            int before = mutants.size();
            collectMutants(doc, mutants);
            if (!doc.scenarios.isEmpty() && mutants.size() == before) {
                vacuous++;
                out.println("GHERKIN VACUOUS " + file + ": " + doc.scenarios.size() + " scenario(s), but no Examples value"
                        + " to mutate - gherkinMutation cannot check its step definitions");
            }
        }
        List<String> manifest = new ArrayList<>();
        manifest.add("id\tfeature\tline\tscenario\tkey\toriginal\tmutated");
        for (Mutant m : mutants) {
            Files.write(outDir.resolve(m.id() + ".feature"), render(m), StandardCharsets.UTF_8);
            manifest.add(String.join("\t", m.id(), m.feature().path, String.valueOf(m.examples().rowLines.get(m.row())),
                    tsv(m.scenario().name), tsv(m.key()), tsv(m.original()), tsv(m.mutated())));
        }
        Files.write(outDir.resolve("manifest.tsv"), manifest, StandardCharsets.UTF_8);
        out.println("Gherkin mutation: " + mutants.size() + " mutant(s) from " + inScope + " feature file(s) in scope -> "
                + outDir);
        if (vacuous > 0) {
            out.println("  Every value a scenario depends on must be an Examples parameter (Scenario Outline), so that");
            out.println("  a hollow step definition shows up as a surviving mutant. The specifier rewrites the feature;");
            out.println("  the human approves it.");
        }
        return unreadable == 0 && vacuous == 0 ? 0 : 1;
    }

    static void collectMutants(FeatureDoc doc, List<Mutant> mutants) {
        for (int s = 0; s < doc.scenarios.size(); s++) {
            Scenario sc = doc.scenarios.get(s);
            for (int e = 0; e < sc.examples.size(); e++) {
                Examples ex = sc.examples.get(e);
                if (ex.headerRaw == null) {
                    continue;
                }
                for (int r = 0; r < ex.rowsRaw.size(); r++) {
                    List<String> row = ex.rowsRaw.get(r);
                    for (int k = 0; k < ex.headerRaw.size() && k < row.size(); k++) {
                        String key = unescape(ex.headerRaw.get(k));
                        String original = unescape(row.get(k));
                        String seed = doc.path + "|" + s + "|" + e + "|" + r + "|" + key + "|" + original;
                        String mutated = mutate(original, new Random(seed.hashCode()));
                        if (!mutated.equals(original)) {
                            mutants.add(new Mutant("m" + (mutants.size() + 1), doc, sc, ex, r, key, original, mutated));
                        }
                    }
                }
            }
        }
    }

    static List<String> render(Mutant m) {
        FeatureDoc doc = m.feature();
        Keywords kw = LANGUAGES.get(doc.language);
        List<String> lines = new ArrayList<>();
        if (doc.languageLine != null) {
            lines.add(doc.languageLine);
        }
        lines.add("# Gherkin mutant " + m.id() + " of " + doc.path + " - generated, do not edit");
        lines.addAll(doc.tags);
        lines.add(featureKeyword(doc) + ": MUTANT " + m.id() + " | " + doc.name);
        lines.addAll(doc.background);
        Rule rule = m.scenario().rule;
        if (rule != null) {
            lines.addAll(rule.tags);
            lines.add(rule.header);
            lines.addAll(rule.background);
        }
        List<String> tags = new ArrayList<>(m.scenario().tags);
        tags.addAll(m.examples().tags);
        lines.addAll(tags);
        lines.add("  " + kw.plainScenario() + ": MUTANT " + m.id() + " | " + m.scenario().name + " | " + m.key() + ": "
                + m.original() + " -> " + m.mutated());
        Map<String, String> values = new LinkedHashMap<>();
        List<String> row = m.examples().rowsRaw.get(m.row());
        for (int k = 0; k < m.examples().headerRaw.size() && k < row.size(); k++) {
            String key = unescape(m.examples().headerRaw.get(k));
            values.put(key, key.equals(m.key()) ? m.mutated() : unescape(row.get(k)));
        }
        for (String bodyLine : m.scenario().body) {
            boolean tableLine = bodyLine.trim().startsWith("|");
            String substituted = bodyLine;
            for (Map.Entry<String, String> v : values.entrySet()) {
                String value = tableLine ? escapeCell(v.getValue()) : v.getValue();
                substituted = substituted.replace("<" + v.getKey() + ">", value);
            }
            lines.add(substituted);
        }
        return lines;
    }

    static String featureKeyword(FeatureDoc doc) {
        String t = doc.header.trim();
        return t.substring(0, t.indexOf(':'));
    }

    // ---------------------------------------------------------------- parser

    static FeatureDoc parse(Path file, List<String> lines) {
        FeatureDoc doc = new FeatureDoc();
        doc.path = file.toString().replace('\\', '/');
        for (String l : lines) {
            String t = l.trim();
            if (t.isEmpty()) {
                continue;
            }
            Matcher lang = Pattern.compile("^#\\s*language\\s*:\\s*([\\w-]+)").matcher(t);
            if (lang.find()) {
                doc.language = lang.group(1);
                doc.languageLine = l;
            }
            if (!t.startsWith("#")) {
                break;
            }
        }
        Keywords kw = LANGUAGES.get(doc.language);
        if (kw == null) {
            return doc;
        }
        List<String> pendingTags = new ArrayList<>();
        List<String> block = null;
        Scenario scenario = null;
        Examples examples = null;
        Rule rule = null;
        String docStringFence = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String t = line.trim();
            if (docStringFence != null) {
                if (block != null) {
                    block.add(line);
                }
                if (t.startsWith(docStringFence)) {
                    docStringFence = null;
                }
                continue;
            }
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.startsWith("\"\"\"") || t.startsWith("```")) {
                docStringFence = t.startsWith("```") ? "```" : "\"\"\"";
                if (block != null) {
                    block.add(line);
                }
                continue;
            }
            if (t.startsWith("@")) {
                pendingTags.add(line);
                continue;
            }
            Kw k = keyword(t, kw);
            if (k != null) {
                String name = t.substring(t.indexOf(':') + 1).trim();
                switch (k) {
                    case FEATURE -> {
                        doc.tags = pendingTags;
                        doc.header = line;
                        doc.name = name;
                        block = null;
                    }
                    case BACKGROUND -> {
                        block = rule != null ? rule.background : doc.background;
                        block.add(line);
                    }
                    case RULE -> {
                        rule = new Rule(pendingTags, line);
                        block = null;
                    }
                    case SCENARIO -> {
                        scenario = new Scenario(pendingTags, name, i + 1, rule);
                        doc.scenarios.add(scenario);
                        block = scenario.body;
                    }
                    case EXAMPLES -> {
                        if (scenario != null) {
                            examples = new Examples(pendingTags, i + 1);
                            scenario.examples.add(examples);
                        }
                        block = null;
                    }
                    default -> throw new IllegalStateException();
                }
                pendingTags = new ArrayList<>();
                continue;
            }
            if (t.startsWith("|") && block == null && examples != null && scenario != null
                    && !scenario.examples.isEmpty() && scenario.examples.get(scenario.examples.size() - 1) == examples) {
                List<String> cells = cells(t);
                if (examples.headerRaw == null) {
                    examples.headerRaw = cells;
                } else {
                    examples.rowsRaw.add(cells);
                    examples.rowLines.add(i + 1);
                }
                continue;
            }
            if (block != null) {
                block.add(line);
            }
        }
        return doc;
    }

    static Kw keyword(String trimmed, Keywords kw) {
        if (startsWithAny(trimmed, kw.feature())) {
            return Kw.FEATURE;
        }
        if (startsWithAny(trimmed, kw.background())) {
            return Kw.BACKGROUND;
        }
        if (startsWithAny(trimmed, kw.rule())) {
            return Kw.RULE;
        }
        if (startsWithAny(trimmed, kw.outline()) || startsWithAny(trimmed, kw.scenario())) {
            return Kw.SCENARIO;
        }
        if (startsWithAny(trimmed, kw.examples())) {
            return Kw.EXAMPLES;
        }
        return null;
    }

    static boolean startsWithAny(String trimmed, List<String> keywords) {
        for (String k : keywords) {
            if (trimmed.startsWith(k + ":")) {
                return true;
            }
        }
        return false;
    }

    static List<String> cells(String row) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = null;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '\\' && i + 1 < row.length()) {
                if (cur != null) {
                    cur.append(c).append(row.charAt(i + 1));
                }
                i++;
            } else if (c == '|') {
                if (cur != null) {
                    cells.add(cur.toString().trim());
                }
                cur = new StringBuilder();
            } else if (cur != null) {
                cur.append(c);
            }
        }
        return cells;
    }

    static String unescape(String cell) {
        return cell.replace("\\|", "|").replace("\\n", "\n").replace("\\\\", "\\");
    }

    static String escapeCell(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    // ---------------------------------------------------------------- value mutation (APS rules)

    static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    static final Pattern DECIMAL = Pattern.compile("[+-]?\\d+\\.\\d+");
    static final Pattern COMMA_DECIMAL = Pattern.compile("[+-]?\\d+,\\d+");
    static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    static final Pattern TIME = Pattern.compile("(\\d{2}:\\d{2})(:\\d{2})?");
    static final Pattern DATE_TIME = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})([T ])(\\d{2}:\\d{2})(:\\d{2})?(\\.\\d{1,9})?(Z|[+-]\\d{2}:?\\d{2})?");
    static final Pattern SHORT_DURATION = Pattern.compile("(\\d+)(ms|s|m|h|d)");

    static String mutate(String value, Random rnd) {
        String t = value.trim();
        if (COMMA_DECIMAL.matcher(t).matches()) {   // Russian-style decimal: 1,5 / 750,50
            return mutateDecimal(t.replace(',', '.'), rnd).replace('.', ',');
        }
        if (t.contains(",")) {
            String[] items = t.split(",", -1);
            int pick = rnd.nextInt(items.length);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                String item = items[i].trim();
                result.add(i == pick ? mutate(item, rnd) : item);
            }
            return String.join(", ", result);
        }
        String lower = t.toLowerCase();
        if (lower.equals("true") || lower.equals("false")) {
            return lower.equals("true") ? "false" : "true";
        }
        if (lower.equals("null") || lower.equals("nil") || lower.equals("none")) {
            return dither(t, rnd);
        }
        if (INTEGER.matcher(t).matches()) {
            return mutateInteger(t, rnd);
        }
        if (DECIMAL.matcher(t).matches()) {
            return mutateDecimal(t, rnd);
        }
        String temporal = mutateTemporal(t, rnd);
        if (temporal != null) {
            return temporal;
        }
        return dither(value, rnd);
    }

    static int delta(Random rnd) {
        int d = 1 + rnd.nextInt(9);
        return rnd.nextBoolean() ? d : -d;
    }

    static String mutateInteger(String t, Random rnd) {
        BigInteger v = new BigInteger(t.startsWith("+") ? t.substring(1) : t);
        int d = delta(rnd);
        BigInteger r = v.add(BigInteger.valueOf(d));
        if (v.signum() >= 0 && r.signum() < 0) {
            r = v.add(BigInteger.valueOf(Math.abs(d)));
        }
        String s = r.toString();
        String digits = t.replaceFirst("^[+-]", "");
        if (digits.length() > 1 && digits.startsWith("0") && r.signum() >= 0) {
            s = "0".repeat(Math.max(0, digits.length() - s.length())) + s;
        }
        return s;
    }

    static String mutateDecimal(String t, Random rnd) {
        BigDecimal v = new BigDecimal(t.startsWith("+") ? t.substring(1) : t);
        int scale = v.scale();
        BigDecimal step = BigDecimal.valueOf(1 + rnd.nextInt(99)).movePointLeft(scale);
        BigDecimal r = rnd.nextBoolean() ? v.add(step) : v.subtract(step);
        if (v.signum() >= 0 && r.signum() < 0) {
            r = v.add(step);
        }
        return r.setScale(scale).toPlainString();
    }

    static String mutateTemporal(String t, Random rnd) {
        try {
            if (DATE.matcher(t).matches()) {
                return LocalDate.parse(t).plusDays(delta(rnd) * 3L).toString();
            }
            Matcher tm = TIME.matcher(t);
            if (tm.matches()) {
                boolean seconds = tm.group(2) != null;
                LocalTime lt = LocalTime.parse(t);
                return seconds ? lt.plusSeconds(delta(rnd) * 7L).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        : lt.plusMinutes(delta(rnd) * 7L).format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            Matcher dt = DATE_TIME.matcher(t);
            if (dt.matches()) {
                boolean seconds = dt.group(4) != null;
                LocalDateTime ldt = LocalDateTime.parse(dt.group(1) + "T" + dt.group(3) + (seconds ? dt.group(4) : ":00"));
                LocalDateTime shifted = seconds ? ldt.plusSeconds(delta(rnd) * 7L) : ldt.plusMinutes(delta(rnd) * 7L);
                String time = shifted.format(DateTimeFormatter.ofPattern(seconds ? "HH:mm:ss" : "HH:mm"));
                return shifted.toLocalDate() + dt.group(2) + time + (dt.group(5) == null ? "" : dt.group(5))
                        + (dt.group(6) == null ? "" : dt.group(6));
            }
            if (t.startsWith("P") || t.startsWith("-P")) {
                if (t.contains("T")) {
                    Duration d = Duration.parse(t);
                    Duration r = d.toMinutes() >= 1 ? d.plusMinutes(delta(rnd)) : d.plusSeconds(delta(rnd));
                    return (r.isNegative() || r.isZero() ? d.plusMinutes(Math.abs(delta(rnd))) : r).toString();
                }
                Period p = Period.parse(t);
                Period r = p.plusDays(delta(rnd));
                return (r.isNegative() ? p.plusDays(1 + rnd.nextInt(9)) : r).toString();
            }
            Matcher sd = SHORT_DURATION.matcher(t);
            if (sd.matches()) {
                long n = Long.parseLong(sd.group(1));
                long r = n + delta(rnd);
                return (r < 0 ? n + 1 + rnd.nextInt(9) : r) + sd.group(2);
            }
        } catch (RuntimeException notTemporal) {
            return null;
        }
        return null;
    }

    static String dither(String s, Random rnd) {
        if (s.isEmpty()) {
            return "x";
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            int i = rnd.nextInt(s.length());
            char c = s.charAt(i);
            String r = switch (rnd.nextInt(4)) {
                case 0 -> s.substring(0, i) + similar(c, rnd) + s.substring(i + 1);
                case 1 -> i + 1 < s.length() ? s.substring(0, i) + s.charAt(i + 1) + c + s.substring(i + 2) : s;
                case 2 -> s.substring(0, i) + similar(c, rnd) + s.substring(i);
                default -> s.length() > 1 ? s.substring(0, i) + s.substring(i + 1) : s;
            };
            if (!r.equals(s) && !r.isBlank()) {
                return r;
            }
        }
        return s + "x";
    }

    static char similar(char c, Random rnd) {
        if (c >= 'a' && c <= 'z') {
            return (char) ('a' + rnd.nextInt(26));
        }
        if (c >= 'A' && c <= 'Z') {
            return (char) ('A' + rnd.nextInt(26));
        }
        if (c >= 'а' && c <= 'я') {
            return (char) ('а' + rnd.nextInt(32));
        }
        if (c >= 'А' && c <= 'Я') {
            return (char) ('А' + rnd.nextInt(32));
        }
        if (c >= '0' && c <= '9') {
            return (char) ('0' + rnd.nextInt(10));
        }
        return (char) ('a' + rnd.nextInt(26));
    }

    // ---------------------------------------------------------------- verdict

    static int verdict(Path mutantsDir, Path resultsDir, PrintStream out) throws IOException {
        Path manifestFile = mutantsDir.resolve("manifest.tsv");
        if (!Files.isRegularFile(manifestFile)) {
            out.println("Gherkin mutation: no manifest at " + manifestFile + " - nothing to judge.");
            return 0;
        }
        Map<String, String[]> manifest = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(manifestFile, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split("\t", -1);
            manifest.put(f[0], f);
        }
        if (manifest.isEmpty()) {
            out.println("Gherkin mutation: no mutants (no Examples values in scope) - OK.");
            return 0;
        }
        Map<String, String> outcome = readResults(resultsDir);
        int killed = 0;
        int byUndefinedStep = 0;
        List<String> survived = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String[]> e : manifest.entrySet()) {
            String[] f = e.getValue();
            String what = f[1] + ":" + f[2] + " \"" + f[3] + "\" " + f[4] + ": " + f[5] + " -> " + f[6];
            String o = outcome.get(e.getKey());
            if (o == null) {
                errors.add("MUTANT ERROR    " + e.getKey() + " not executed  " + what);
            } else if (o.equals("passed")) {
                survived.add("MUTANT SURVIVED " + e.getKey() + "  " + what);
            } else if (o.equals("aborted")) {
                errors.add("MUTANT ERROR    " + e.getKey() + " skipped/aborted, not a kill  " + what);
            } else {
                killed++;
                byUndefinedStep += o.equals("undefined") ? 1 : 0;
            }
        }
        out.println("Gherkin mutation: " + manifest.size() + " mutant(s) - " + killed + " killed"
                + (byUndefinedStep > 0 ? " (" + byUndefinedStep + " only because no step definition matched the new value)" : "")
                + ", " + survived.size() + " survived, " + errors.size() + " error(s)");
        survived.forEach(out::println);
        errors.forEach(out::println);
        if (survived.isEmpty() && errors.isEmpty()) {
            out.println("Gherkin mutation: OK");
            return 0;
        }
        if (!survived.isEmpty()) {
            out.println("  A survivor means the scenario still passes after that Examples value changed: the step");
            out.println("  that receives the value ignores it or asserts too weakly. Fix the step definition or the");
            out.println("  production code - not the feature file. If the column truly does not matter, the");
            out.println("  specifier should remove it from the Examples (human approval required).");
        }
        if (!errors.isEmpty()) {
            out.println("  Errors: the mutant scenario did not run to a verdict (skipped by an assumption, parse error,");
            out.println("  crash, timeout). A skipped scenario proves nothing. See the test report.");
        }
        return 1;
    }

    /** Cucumber reports undefined steps as aborted, which Gradle shows as skipped - not as failed. */
    static int noSkipped(Path resultsDir, Path featuresDir, PrintStream out) throws IOException {
        if (featuresDir != null && !hasFeatureFiles(featuresDir)) {
            out.println("Acceptance: no feature files in " + featuresDir + " - nothing to check (older results ignored)");
            return 0;
        }
        List<String> skipped = new ArrayList<>();
        int total = 0;
        for (Element tc : testcases(resultsDir)) {
            total++;
            for (Node n = tc.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getTagName().equals("skipped")) {
                    skipped.add("ACCEPTANCE SKIPPED " + tc.getAttribute("classname") + " > " + tc.getAttribute("name"));
                }
            }
        }
        if (total == 0 && featuresDir != null) {
            out.println("Acceptance: FAILED - feature files exist in " + featuresDir + ", but no scenario ran (no results in "
                    + resultsDir + "). An empty run proves nothing: check cucumber.features, the glue and the engine.");
            return 1;
        }
        if (skipped.isEmpty()) {
            out.println("Acceptance: " + total + " scenario(s), none skipped - OK");
            return 0;
        }
        skipped.forEach(out::println);
        out.println("Acceptance: FAILED - " + skipped.size() + " scenario(s) skipped. Cucumber reports undefined or");
        out.println("  pending steps as skipped, so they would otherwise pass silently. Implement the missing step");
        out.println("  definitions (run with -Dcucumber.plugin=pretty to see the snippets).");
        return 1;
    }

    static boolean hasFeatureFiles(Path featuresDir) throws IOException {
        if (!Files.isDirectory(featuresDir)) {
            return false;
        }
        try (Stream<Path> s = Files.walk(featuresDir)) {
            return s.filter(p -> p.toString().endsWith(".feature")).anyMatch(GherkinMutator::hasGherkin);
        }
    }

    /** Empty and comment-only feature files are valid for Cucumber but hold no specification. */
    static boolean hasGherkin(Path featureFile) {
        try {
            return Files.readAllLines(featureFile, StandardCharsets.UTF_8).stream()
                    .map(l -> l.replace("\uFEFF", "").trim()).anyMatch(l -> !l.isEmpty() && !l.startsWith("#"));
        } catch (IOException unreadable) {
            return true;   // unreadable is not proof of emptiness
        }
    }

    static List<Element> testcases(Path resultsDir) throws IOException {
        List<Element> list = new ArrayList<>();
        if (!Files.isDirectory(resultsDir)) {
            return list;
        }
        List<Path> xmls;
        try (Stream<Path> s = Files.walk(resultsDir)) {
            xmls = s.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        }
        for (Path xml : xmls) {
            try {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setNamespaceAware(false);
                Document doc = f.newDocumentBuilder().parse(xml.toFile());
                NodeList cases = doc.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    list.add((Element) cases.item(i));
                }
            } catch (Exception unreadable) {
                // not a JUnit XML report - ignore
            }
        }
        return list;
    }

    static Map<String, String> readResults(Path resultsDir) throws IOException {
        Map<String, String> outcome = new LinkedHashMap<>();
        Pattern id = Pattern.compile("MUTANT (m\\d+)\\b");
        for (Element tc : testcases(resultsDir)) {
            Matcher m = id.matcher(tc.getAttribute("name") + " " + tc.getAttribute("classname"));
            if (!m.find()) {
                continue;
            }
            String result = "passed";
            for (Node n = tc.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) {
                    switch (e.getTagName()) {
                        case "failure", "error" -> result = undefinedStep(e) ? "undefined" : "failed";
                        case "skipped" -> result = result.equals("passed") ? "aborted" : result;
                        default -> {
                        }
                    }
                }
            }
            String prev = outcome.get(m.group(1));
            if (prev == null || prev.equals("passed") || prev.equals("aborted")) {
                outcome.put(m.group(1), result);
            }
        }
        return outcome;
    }

    /** Cucumber reports a step without a matching definition as a failure (UndefinedStepException). */
    static boolean undefinedStep(Element failure) {
        String text = failure.getAttribute("type") + " " + failure.getAttribute("message") + " " + failure.getTextContent();
        return text.contains("UndefinedStepException") || text.contains("is undefined");
    }

    // ---------------------------------------------------------------- helpers

    static boolean matchesAny(Path file, List<String> only) {
        String p = file.toString().replace('\\', '/');
        for (String o : only) {
            String n = o.replace('\\', '/');
            if (p.equals(n) || p.endsWith("/" + n) || n.endsWith("/" + p) || n.endsWith(p)) {
                return true;
            }
        }
        return false;
    }

    static List<String> csv(String s) {
        List<String> list = new ArrayList<>();
        for (String x : s.split(",")) {
            if (!x.isBlank()) {
                list.add(x.trim());
            }
        }
        return list;
    }

    static String tsv(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(p);
            }
        }
    }
}
