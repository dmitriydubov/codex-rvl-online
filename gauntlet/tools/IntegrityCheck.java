import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Integrity check: finds the usual ways to turn a red gauntlet green without fixing anything.
 *
 * <p>If nobody reads the code, the gates must not be quietly bypassable. This scans tests,
 * production code, feature files and build scripts for skip/suppress/exclude tricks. A finding
 * can be accepted only by the human, in {@code gauntlet/config/integrity-allowlist.txt}:
 * <pre>
 *   &lt;path glob relative to the project root&gt; &lt;RULE-ID&gt;   # reason
 *   src/test/java/**&#47;LegacyReportIT.java T4               # needs a real Oracle, runs in CI only
 * </pre>
 * Usage: {@code java IntegrityCheck.java --root . [--features <dir>] [--allowlist <file>]}
 * Exit codes: 0 = clean, 1 = findings, 2 = usage or IO error.
 */
public class IntegrityCheck {

    enum Kind { TEST, MAIN, FEATURE, BUILD, PROPS }

    record Rule(String id, Kind kind, Pattern pattern, boolean includeComments, String why) {
    }

    record Finding(String path, int line, Rule rule, String text) {
    }

    record Allow(PathMatcher matcher, String ruleId) {
    }

    /** Switching off packaging/doc tasks (e.g. Spring Boot's plain jar) is normal and not a gate. */
    static final String HARMLESS_NAMES = "(jar|bootJar|bootRun|javadoc\\w*|sourcesJar|distZip|distTar|startScripts"
            + "|bootDist\\w*|bootStartScripts|bootBuildImage|publish\\w*|generate\\w*|openApi\\w*|Jar|Javadoc|Zip|Tar)";
    static final Pattern HARMLESS_TASK = Pattern.compile("(?i)[\"'<(]" + HARMLESS_NAMES + "[\"'>)]|^\\s*"
            + HARMLESS_NAMES + "\\s*\\{");
    static final Pattern DISABLED = Pattern.compile("\\b(is)?[eE]nabled\\s*=\\s*false|setEnabled\\s*\\(\\s*false");

    static final List<Rule> RULES = List.of(
            rule("T1", Kind.TEST, "@Disabled\\w*", false, "disabled test"),
            rule("T2", Kind.TEST, "@Enabled(If|On)\\w*", false, "conditionally enabled test"),
            rule("T3", Kind.TEST, "@Ignore\\b", false, "ignored JUnit 4 test"),
            rule("T4", Kind.TEST, "\\bassum(e|ing)(True|False|That)\\s*\\(|\\bAssumptions\\.|(?<![.\\w])abort\\s*\\(", false,
                    "assumption can silently skip the test"),
            rule("T5", Kind.TEST, "disabledWithoutDocker\\s*=\\s*true", false,
                    "Testcontainers tests silently skipped without Docker"),
            rule("M1", Kind.MAIN, "@[\\w.]*(Generated(?!Value)|DoNotMutate|CoverageIgnore)\\w*", false,
                    "annotation hides code from coverage/CRAP/mutation"),
            rule("M2", Kind.MAIN, "CHECKSTYLE\\s*[:.]\\s*OFF|checkstyle:off|NOPMD|CPD-OFF", true,
                    "inline suppression of a quality check"),
            rule("M3", Kind.MAIN, "@SuppressWarnings\\s*\\(\\s*\\{?\\s*\"(checkstyle|PMD|CPD)", false,
                    "suppression of a quality check"),
            rule("F1", Kind.FEATURE,
                    "^\\s*(?:@\\S+\\s+)*@(ignore|ignored|disabled|skip|skipped|wip|pending|manual)(?![\\w-])", true,
                    "tag commonly used to exclude a scenario"),
            rule("B1", Kind.BUILD, "ignoreFailures|\\b(is)?[eE]nabled\\s*=\\s*false|setEnabled\\s*\\(\\s*false|onlyIf\\s*[({]",
                    false, "build script switches a check off"),
            rule("B2", Kind.BUILD, "(exclude|include)TestsMatching|(exclude|include)Tags|excludeEngines|failOnNoDiscoveredTests",
                    false, "build script filters which tests run"),
            rule("B3", Kind.BUILD, "maxSurviving|mutationThreshold|coverageThreshold|testStrengthThreshold"
                    + "|excludedClasses|excludedMethods|excludedTestClasses|excludedGroups|avoidCallsTo|targetClasses"
                    + "|targetTests|classDirectories|cucumber\\.(filter|features|glue)", false,
                    "build script reconfigures a gate (gates live in gauntlet/gauntlet.gradle)"),
            rule("B4", Kind.BUILD, "tasks\\s*\\.\\s*(named|getByName|register)\\s*(<[^>]*>)?\\s*\\(\\s*[\"'](gauntlet\\w*"
                    + "|crapGate|dryGate|integrityCheck|archTest|acceptanceTest|acceptanceCheck|gherkin\\w+"
                    + "|mutationGate|pitest|pitSummary|jacocoTestReport|checkstyleGate|pmdGate|cpdReport)[\"']", false,
                    "build script touches a gauntlet task"),
            rule("B5", Kind.BUILD, "afterEvaluate|taskGraph|whenReady|beforeTask|projectsEvaluated", false,
                    "late build configuration can override the gauntlet"),
            rule("P1", Kind.PROPS, "cucumber\\.(filter|features)", false, "test properties filter scenarios"));

    public static void main(String[] args) {
        System.exit(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)));
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path root = Path.of(".");
        Path features = null;
        Path allowlist = null;
        try {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "--root" -> root = Path.of(args[i + 1]);
                    case "--features" -> features = Path.of(args[i + 1]);
                    case "--allowlist" -> allowlist = Path.of(args[i + 1]);
                    default -> throw new IllegalArgumentException("unknown option " + args[i]);
                }
            }
        } catch (RuntimeException e) {
            err.println("IntegrityCheck: " + e.getMessage());
            return 2;
        }
        root = root.toAbsolutePath().normalize();
        if (features == null) {
            features = root.resolve("src/acceptanceTest/resources/features");
        } else if (!features.isAbsolute()) {
            features = root.resolve(features);
        }
        try {
            List<Allow> allows = readAllowlist(allowlist == null ? null : root.resolve(allowlist));
            List<Finding> findings = new ArrayList<>();
            scanTree(root, root.resolve("src/test/java"), Kind.TEST, findings);
            scanTree(root, root.resolve("src/acceptanceTest/java"), Kind.TEST, findings);
            scanTree(root, root.resolve("src/main/java"), Kind.MAIN, findings);
            scanTree(root, features, Kind.FEATURE, findings);
            for (String f : List.of("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                    "gradle.properties")) {
                scanFile(root, root.resolve(f), Kind.BUILD, findings);
            }
            for (Path script : buildLogic(root)) {
                scanFile(root, script, Kind.BUILD, findings);
            }
            for (Path script : includedBuilds(root)) {
                scanFile(root, script, Kind.BUILD, findings);
            }
            for (String dir : List.of("src/main/resources", "src/test/resources", "src/acceptanceTest/resources")) {
                for (String f : List.of("junit-platform.properties", "cucumber.properties")) {
                    scanFile(root, root.resolve(dir).resolve(f), Kind.PROPS, findings);
                }
            }
            List<Finding> reported = findings.stream().filter(f -> !allowed(f, allows)).toList();
            List<String> structural = structuralProblems(root);
            return print(reported, findings.size() - reported.size(), structural, out);
        } catch (IOException e) {
            err.println("IntegrityCheck: " + e);
            return 2;
        }
    }

    /** Other places build logic can hide: extra script plugins under gradle/ and buildSrc/. */
    static List<Path> buildLogic(Path root) throws IOException {
        List<Path> scripts = new ArrayList<>();
        Path gauntlet = root.resolve("gauntlet/gauntlet.gradle");
        for (String dir : List.of("gradle", "buildSrc")) {
            Path d = root.resolve(dir);
            if (!Files.isDirectory(d)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(d)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> !p.equals(gauntlet) && !p.toString().contains("/build/"))
                        .filter(p -> p.toString().matches(".*\\.(gradle|gradle\\.kts|kt|groovy|java)$"))
                        .sorted().forEach(scripts::add);
            }
        }
        return scripts;
    }

    /**
     * Build scripts of the builds this one includes (settings.gradle: includeBuild("../lib")): they run inside
     * this build too, so they are scanned with the same rules (reported as ../lib/build.gradle).
     */
    static List<Path> includedBuilds(Path root) throws IOException {
        List<Path> scripts = new ArrayList<>();
        Pattern include = Pattern.compile("includeBuild\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
        for (String f : List.of("settings.gradle", "settings.gradle.kts")) {
            Path settings = root.resolve(f);
            if (!Files.isRegularFile(settings)) {
                continue;
            }
            Matcher m = include.matcher(Files.readString(settings, StandardCharsets.UTF_8));
            while (m.find()) {
                Path dir = root.resolve(m.group(1)).normalize();
                for (String b : List.of("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                        "gradle.properties")) {
                    Path p = dir.resolve(b);
                    if (Files.isRegularFile(p)) {
                        scripts.add(p);
                    }
                }
            }
        }
        return scripts;
    }

    static List<String> structuralProblems(Path root) throws IOException {
        List<String> problems = new ArrayList<>();
        boolean applied = false;
        for (String f : List.of("build.gradle", "build.gradle.kts")) {
            Path p = root.resolve(f);
            if (Files.isRegularFile(p) && Files.readString(p, StandardCharsets.UTF_8).contains("gauntlet.gradle")) {
                applied = true;
            }
        }
        if (!applied) {
            problems.add("INTEGRITY build script does not apply gauntlet/gauntlet.gradle");
        }
        return problems;
    }

    static int print(List<Finding> findings, int allowedCount, List<String> structural, PrintStream out) {
        for (Finding f : findings) {
            out.println("INTEGRITY " + f.rule().id() + " " + f.path() + ":" + f.line() + "  " + f.rule().why()
                    + "  |  " + abbreviate(f.text().trim()));
        }
        structural.forEach(out::println);
        int total = findings.size() + structural.size();
        if (total == 0) {
            out.println("Integrity check: OK" + (allowedCount > 0 ? " (" + allowedCount + " allowlisted)" : ""));
            return 0;
        }
        out.println("Integrity check: FAILED - " + total + " finding(s). Fix the cause instead of hiding it.");
        out.println("  Only the human can accept a finding, in gauntlet/config/integrity-allowlist.txt (path-glob RULE-ID).");
        return 1;
    }

    static void scanTree(Path root, Path dir, Kind kind, List<Finding> findings) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        String ext = kind == Kind.FEATURE ? ".feature" : ".java";
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(p -> p.toString().endsWith(ext)).sorted().toList();
        }
        for (Path p : files) {
            scanFile(root, p, kind, findings);
        }
    }

    static void scanFile(Path root, Path file, Kind kind, List<Finding> findings) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String rel = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean comment = isComment(line, kind);
            for (Rule r : RULES) {
                if (r.kind() == kind && (r.includeComments() || !comment) && r.pattern().matcher(line).find()
                        && !(r.id().equals("B1") && harmlessTask(lines, i))) {
                    findings.add(new Finding(rel, i + 1, r, line));
                }
            }
        }
    }

    /**
     * "enabled = false" inside the innermost block that configures a packaging/doc task (e.g. Spring Boot's
     * plain jar). Only that form is exempt - ignoreFailures or onlyIf never are.
     */
    static boolean harmlessTask(List<String> lines, int index) {
        if (!DISABLED.matcher(lines.get(index)).find()) {
            return false;
        }
        int depth = 0;
        for (int j = index; j >= Math.max(0, index - 30); j--) {
            String line = lines.get(j);
            int closes = count(line, '}');
            int opens = count(line, '{');
            if (j == index) {
                if (opens > 1) {
                    return false;                                // several blocks on one line: judge none as harmless
                }
                if (opens == 1) {
                    return HARMLESS_TASK.matcher(line).find();   // single-line block: tasks.named("jar") { enabled = false }
                }
                continue;
            }
            depth += closes - opens;
            if (depth < 0) {
                return HARMLESS_TASK.matcher(line).find();       // the innermost enclosing block opener
            }
        }
        return false;
    }

    static int count(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }

    static boolean isComment(String line, Kind kind) {
        String t = line.trim();
        if (kind == Kind.FEATURE || kind == Kind.PROPS) {
            return t.startsWith("#");
        }
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
    }

    static boolean allowed(Finding f, List<Allow> allows) {
        for (Allow a : allows) {
            if (a.ruleId().equals(f.rule().id()) && a.matcher().matches(Path.of(f.path()))) {
                return true;
            }
        }
        return false;
    }

    static List<Allow> readAllowlist(Path file) throws IOException {
        List<Allow> list = new ArrayList<>();
        if (file == null || !Files.isRegularFile(file)) {
            return list;
        }
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.replaceAll("#.*$", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                list.add(new Allow(FileSystems.getDefault().getPathMatcher("glob:" + parts[0]), parts[1]));
            }
        }
        return list;
    }

    static Rule rule(String id, Kind kind, String regex, boolean includeComments, String why) {
        return new Rule(id, kind, Pattern.compile(regex), includeComments, why);
    }

    static String abbreviate(String s) {
        return s.length() <= 100 ? s : s.substring(0, 97) + "...";
    }
}
