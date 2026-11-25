import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
 * Usage: {@code java IntegrityCheck.java --root . [--features <dir>] [--allowlist <file>] [--encoding <charset>]
 * [--test-encoding <charset>] [--acceptance-encoding <charset>]}
 * {@code --encoding} is the charset javac reads src/main/java with (default UTF-8); src/test/java and
 * src/acceptanceTest/java are compiled by tasks of their own and take {@code --test-encoding} and
 * {@code --acceptance-encoding}, which default to {@code --encoding}. A Java source that is not valid text in
 * its charset is finding E1, never silence. Feature files, build scripts, properties and the allowlist are read
 * as UTF-8, and a byte that is not UTF-8 never stops the scan there.
 *
 * <p>Before a rule looks at a Java source or a build script the file is read the way its compiler reads it
 * (see {@link Lexer}): comments are no code, a brace in a string is no brace.
 * Exit codes: 0 = clean, 1 = findings, 2 = usage or IO error.
 */
public class IntegrityCheck {

    enum Kind { TEST, MAIN, FEATURE, BUILD, PROPS }

    /** Whose reading of a file the rules follow: javac's, Groovy's, Kotlin's - or plain lines with comment lines. */
    enum Lang {
        JAVA, GROOVY, KOTLIN, PROPERTIES, FEATURE;

        static Lang of(Path file) {
            String name = file.getFileName().toString();
            return name.endsWith(".java") ? JAVA
                    : name.endsWith(".kts") || name.endsWith(".kt") ? KOTLIN
                    : name.endsWith(".properties") ? PROPERTIES
                    : name.endsWith(".feature") ? FEATURE
                    : GROOVY;
        }
    }

    record Rule(String id, Kind kind, Pattern pattern, boolean includeComments, String why) {
    }

    record Finding(String path, int line, Rule rule, String text) {
    }

    record Allow(PathMatcher matcher, String ruleId) {
    }

    /**
     * A block that configures a packaging/doc task. Inside {@code each}/{@code forEach} the task is only the
     * closure's argument; everywhere else it is also what a bare {@code enabled = false} addresses.
     */
    record Block(boolean each, String parameter, boolean ownParameters) {
    }

    /** Switching off packaging/doc tasks (e.g. Spring Boot's plain jar) is normal and not a gate. */
    static final String HARMLESS_NAMES = "(jar|bootJar|bootRun|javadoc\\w*|sourcesJar|distZip|distTar|startScripts"
            + "|bootDist\\w*|bootStartScripts|bootBuildImage|publish\\w*|generate\\w*|openApi\\w*|Jar|Javadoc|Zip|Tar)";
    static final Pattern HARMLESS_NAME = Pattern.compile("(?i)^(?:" + HARMLESS_NAMES + ")$");
    static final Pattern DISABLED = Pattern.compile("\\b(is)?[eE]nabled\\s*=\\s*false|setEnabled\\s*\\(\\s*false");
    /**
     * The end of a call chain that names tasks: {@code named("x")}, {@code getByName<T>("x")},
     * {@code register("x", T)} (groups 1 and 3: type and name) or {@code withType<T>()}, {@code withType(T)}
     * (groups 4 and 5). The name is a plain literal - {@code "${x}jar"} names nothing.
     */
    static final Pattern TASK_CALL = Pattern.compile("(?<![\\w$])(?:(?:named|getByName|findByName|register|create"
            + "|maybeCreate)(?:<(\\w+)>)?\\(([\"'])([^\"'$\\\\]*)\\2(?:,[^()]*)?\\)"
            + "|withType(?:<(\\w+)>(?:\\(\\))?|\\((\\w+)\\)))$");
    /** A task named by its accessor. In front of a block: {@code jar} or {@code tasks.jar}; as a receiver: any last name. */
    static final Pattern OWN_TASK = Pattern.compile("(?:^|(?<![\\w$])tasks\\.)([A-Za-z_$][\\w$]*)$");
    static final Pattern LAST_ID = Pattern.compile("(?:^|\\.)([A-Za-z_$][\\w$]*)$");
    /**
     * The include and exclude patterns of a Test task ({@code test { exclude 'com/acme/OrdersTest.class' }}). The
     * same words mean something else for jar, a copy, a source set or a dependency, so B2 reports them only where
     * they configure a Test task the gauntlet runs.
     */
    static final String TEST_PATTERNS = "(?<![\\w$])(?:exclude|include|excludes|includes|setExcludes|setIncludes)(?![\\w$])";
    static final Pattern TEST_PATTERN = Pattern.compile(TEST_PATTERNS);
    /** What writes the patterns behind {@code includes}/{@code excludes}: an assignment, {@code <<}, a changing call. */
    static final Pattern ASSIGNED = Pattern.compile("(?:[-+*/%&|^?]|<<|>>>?|\\*\\*)?=(?![=~])|<<");
    static final Pattern MUTATED = Pattern.compile("(?:\\?|!!)?\\.\\s*(?:add|addAll|clear|remove|removeAll|removeIf|retainAll"
            + "|leftShift|plusAssign|minusAssign|apply|also|run|let|with|tap)(?![\\w$])");
    /** Kotlin's with(...) around the patterns: {@code with(excludes) { add("x") }} changes them as well. */
    static final Pattern WITH_CALL = Pattern.compile("(?<![\\w$.])with\\s*$");
    /** The Test tasks the gauntlet runs; testTask is the Test task of a JVM test suite (testing.suites.test). */
    static final Pattern GATE_TEST = Pattern.compile("test|archTest|acceptanceTest|gherkinMutantsRun|testTask");
    /**
     * Test tasks by their type: {@code withType(Test)}, {@code withType<Test>()}, {@code withType(Test::class.java)};
     * in Kotlin also a delegate of a task that exists already, {@code val test by tasks.getting(Test::class)}.
     */
    static final Pattern TEST_TYPE = Pattern.compile("(?<![\\w$])(?:withType(?:<(?:[\\w$]+\\.)*Test>(?:\\(\\))?"
            + "|\\((?:[\\w$]+\\.)*Test(?:::class(?:\\.java)?|\\.class)?\\))"
            + "|(?:getting|existing)\\((?:[\\w$]+\\.)*Test::class(?:\\.java)?(?:,[^()]*)?\\))$");
    /** The task behind a provider: {@code tasks.named("test").get()}. */
    static final Pattern PROVIDED = Pattern.compile("(?:\\?|!!)?\\.get\\(\\)$");
    /**
     * Blocks whose bare calls still reach the Test task they sit in: {@code test { filter { exclude 'x' } }}, with
     * empty parentheses too ({@code doFirst() { }}). Behind a receiver they configure the task it names:
     * {@code test.doFirst { setExcludes([...]) }}.
     */
    static final String TEST_INNERS = "(?:filter|useJUnitPlatform|useJUnit|useTestNG|options|doFirst|doLast)(?:\\(\\))?";
    static final Pattern TEST_INNER = Pattern.compile(TEST_INNERS);
    static final Pattern TEST_INNER_OF = Pattern.compile("(.+)\\." + TEST_INNERS);
    /** Control blocks run in the block around them: {@code test { if (ci) { exclude 'x' } }} configures test. */
    static final Pattern CONTROL = Pattern.compile(
            "(?:if|for|while|switch|catch|synchronized|when)\\(.*\\)|else|try|finally|do|when");
    /**
     * A name in a condition: {@code name}, {@code getName()}, {@code it.name} (group 1 is the receiver). Only the
     * task's own name chooses which of the Test tasks around it a statement configures (see selectsTask);
     * {@code project.name}, {@code file('x').name} or {@code a.b.name} is some other name.
     */
    static final Pattern NAME_CONDITION = Pattern.compile(
            "(?<![\\w$.])(?:([A-Za-z_$][\\w$]*)\\s*\\??\\.\\s*)?(?:name|getName\\(\\))(?![\\w$(])");
    /** A loop variable or a catch parameter called name: {@code for (name in [...])} declares a name, it tests none. */
    static final Pattern NAME_DECLARED = Pattern.compile("for\\s*\\(\\s*(?:[\\w$.<>\\[\\],?\\s]*\\s)?name\\s*(?::|in(?![\\w$]))"
            + "|catch\\s*\\(\\s*(?:[\\w$.|\\s]*\\s)?name\\s*\\)");
    /**
     * A name with its arguments: the suite in {@code suites { integrationTest(JvmTestSuite) { } }}, the task after
     * the keyword in {@code task smokeTest(type: Test) { }}.
     */
    static final Pattern DECLARED = Pattern.compile("^([A-Za-z_$][\\w$]*)(?:\\(.*\\))?$");
    /** Kotlin's delegated task or suite, named by its val: {@code val smokeTest by tasks.registering(Test::class)}. */
    static final Pattern DELEGATED = Pattern.compile("(?:^|(?<![\\w$])tasks\\.)(?:registering|creating|getting|existing)"
            + "(?:<[\\w$.]+>)?(?:\\([^()]*\\))?$");
    static final Pattern VAL_BY = Pattern.compile("(?<![\\w$])va[lr]\\s+([A-Za-z_$][\\w$]*)(?:\\s*:[^=\\n]*?)?\\s+by\\s*$");
    /** The JVM test suites ({@code testing { suites { } }}) and a suite on the way: {@code testing.suites.integrationTest}. */
    static final Pattern SUITES = Pattern.compile("(?:testing\\.)?suites");
    static final Pattern SUITE_PATH = Pattern.compile("(?<![\\w$])suites\\.(?:(?:named|getByName|findByName|getAt|register|create"
            + "|maybeCreate)(?:<[\\w$.]+>)?\\(([\"'])([^\"'$\\\\]*)\\1|([A-Za-z_$][\\w$]*)(?=[.{]|$))");
    /** Kotlin's delegate of a suite, named by its val: {@code val integrationTest by testing.suites.registering(...)}. */
    static final Pattern SUITE_DELEGATE = Pattern.compile("(?<![\\w$])suites\\.(?:registering|creating|getting|existing)"
            + "(?:<[\\w$.]+>)?(?:\\([^()]*\\))?$");
    /** A block in suites { } that names its suite: {@code test { }}, {@code integrationTest(JvmTestSuite) { }}. */
    static final Pattern SUITE_BLOCK = Pattern.compile(
            "^([A-Za-z_$][\\w$]*)(?:\\((?:(?:[\\w$]+\\.)*JvmTestSuite(?:::class(?:\\.java)?|\\.class)?)?\\))?$");
    /**
     * Words of the suite container, not names of suites: every suite, an opaque choice of them, a lookup whose name is
     * an expression ({@code named(unit)}, {@code findByName(...)}, {@code named { it == 'test' }}).
     */
    static final Pattern EVERY_SUITE = Pattern.compile("configureEach|configure|all|each|forEach|withType|matching|findAll"
            + "|find|named|getByName|findByName|getAt|register|create|maybeCreate|registering|creating|getting|existing");
    /** The test selection names B2 reported before 4.3: still a finding wherever they stand. */
    static final Pattern SELECTION_ANYWHERE = Pattern.compile(
            "(?:exclude|include)TestsMatching|(?:exclude|include)Tags|excludeEngines|failOnNoDiscoveredTests");
    /** What a selection call may hang on and still configure its block's task: {@code filter.includePatterns = [...]}. */
    static final Pattern OWN_SELECTION = Pattern.compile("(?:(?:it|this|delegate)\\.)?(?:filter|options)|it|this|delegate");
    /** A task by its accessor on the container: {@code tasks.smokeTest}. */
    static final Pattern TASKS_ACCESSOR = Pattern.compile("(?<![\\w$])tasks\\.([A-Za-z_$][\\w$]*)$");
    /** A task the script declares: after the keyword ({@code task smokeTest(type: Test)}) or {@code task('smokeTest', type: Test)}. */
    static final Pattern TASK_KEYWORD = Pattern.compile("(?<![\\w$.])task\\s+$");
    static final Pattern DECLARED_CALL = Pattern.compile("(?<![\\w$.])task(?:<[\\w$.]+>)?\\(([\"'])([^\"'$\\\\]*)\\1[^()]*\\)$");
    /**
     * Where a script declares a task or a JVM test suite by name - what a bare {@code smokeTest { }} then names:
     * {@code task smokeTest(type: Test)}, {@code register('smokeTest', Test)}, {@code val smokeTest by tasks.registering},
     * {@code integrationTest(JvmTestSuite)}.
     */
    static final Pattern DECLARATION = Pattern.compile("(?<![\\w$.])task\\s+([A-Za-z_$][\\w$]*)"
            + "|(?<![\\w$])(?:register|create|maybeCreate|task)(?:<[\\w$.]+>)?\\(\\s*([\"'])([A-Za-z_$][\\w$]*)\\2"
            + "|(?<![\\w$])va[lr]\\s+([A-Za-z_$][\\w$]*)[^=\\n]*?\\s+by\\s+(?:tasks\\.)?(?:registering|creating)"
            + "|(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*\\(\\s*JvmTestSuite\\s*\\)");
    static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
    /** What may stand between the tasks and their block: {@code tasks.withType(Jar).matching { }.configureEach { }}. */
    static final Pattern CONFIGURER = Pattern.compile(
            "(?:(?:\\?|!!)?\\.matching\\{\\})*(?:(?:\\?|!!)?\\.(configure|configureEach|all|each|forEach)(?:\\(\\))?)?$");
    /** Single named parameter of a task-configuration closure: {@code jar { t -> }} / {@code jar { Task t -> }}. */
    static final Pattern CLOSURE_PARAM = Pattern.compile("^\\s*(?:final\\s+)?"
            + "(?:[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}$.]*(?:\\s*<[^>]*>)?(?:\\[\\])?\\s+)?"
            + "([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}$]*)\\s*->");
    /** Any parameter list of its own, the empty one too ({@code { -> }}): such a closure has no implicit {@code it}. */
    static final Pattern CLOSURE_HEAD = Pattern.compile("^[\\w$.<>\\[\\]?,:()\\s]*->");

    /** An annotation as javac reads it: '@' and a simple or qualified name, white space allowed between the tokens. */
    static final String AT = "@\\s*(?:[\\w$]+\\s*\\.\\s*)*";

    static final List<Rule> RULES = List.of(
            rule("T1", Kind.TEST, AT + "Disabled\\w*", false, "disabled test"),
            rule("T2", Kind.TEST, AT + "Enabled(If|On)\\w*", false, "conditionally enabled test"),
            rule("T3", Kind.TEST, AT + "Ignore\\b", false, "ignored JUnit 4 test"),
            rule("T4", Kind.TEST, "\\bassum(e|ing)(True|False|That)\\s*\\(|\\bAssumptions\\.|(?<![.\\w])abort\\s*\\(", false,
                    "assumption can silently skip the test"),
            rule("T5", Kind.TEST, "disabledWithoutDocker\\s*=\\s*true", false,
                    "Testcontainers tests silently skipped without Docker"),
            rule("M1", Kind.MAIN, AT + "[\\w$]*(Generated(?!Value)|DoNotMutate|CoverageIgnore)\\w*", false,
                    "annotation hides code from coverage/CRAP/mutation"),
            rule("M2", Kind.MAIN, "CHECKSTYLE\\s*[:.]\\s*OFF|checkstyle:off|NOPMD|CPD-OFF", true,
                    "inline suppression of a quality check"),
            rule("M3", Kind.MAIN, AT + "SuppressWarnings\\s*\\(\\s*\\{?\\s*\"(checkstyle|PMD|CPD)", false,
                    "suppression of a quality check"),
            rule("F1", Kind.FEATURE,
                    "^\\s*(?:@\\S+\\s+)*@(ignore|ignored|disabled|skip|skipped|wip|pending|manual)(?![\\w-])", true,
                    "tag commonly used to exclude a scenario"),
            rule("B1", Kind.BUILD, "ignoreFailures|\\b(is)?[eE]nabled\\s*=\\s*false|setEnabled\\s*\\(\\s*false|onlyIf\\s*[({]",
                    false, "build script switches a check off"),
            rule("B2", Kind.BUILD, "(?<![\\w$])(?:set)?(?:[Ee]xclude|[Ii]nclude)(?:TestsMatching|Test|Patterns|Tags|Engines"
                    + "|Categories|Groups)(?![\\w$])|(?:set)?[Tt]estNameIncludePatterns|failOnNoDiscoveredTests|"
                    + TEST_PATTERNS, false, "build script filters which tests run"),
            rule("B3", Kind.BUILD, "maxSurviving|mutationThreshold|coverageThreshold|testStrengthThreshold"
                    + "|excludedClasses|excludedMethods|excludedTestClasses|excludedGroups|avoidCallsTo|targetClasses"
                    + "|targetTests|classDirectories|cucumber\\.(filter|features|glue)|junit\\.platform\\.execution\\.dryRun",
                    false, "build script reconfigures a gate (gates live in gauntlet/gauntlet.gradle)"),
            rule("B4", Kind.BUILD, "tasks\\s*\\.\\s*(named|getByName|register)\\s*(<[^>]*>)?\\s*\\(\\s*[\"'](gauntlet\\w*"
                    + "|crapGate|dryGate|integrityCheck|testCensus|archTest|acceptanceTest|acceptanceCheck|gherkin\\w+"
                    + "|mutationGate|pitest|pitSummary|jacocoTestReport|checkstyleGate|pmdGate|cpdReport)[\"']", false,
                    "build script touches a gauntlet task"),
            rule("B5", Kind.BUILD, "afterEvaluate|taskGraph|whenReady|beforeTask|projectsEvaluated", false,
                    "late build configuration can override the gauntlet"),
            // a dry run of the JUnit Platform reports every test as skipped and runs none: the census counts a skipped
            // test as run, the build stays green
            rule("P1", Kind.PROPS, "cucumber\\.(filter|features)|junit\\.platform\\.execution\\.dryRun", false,
                    "test properties filter scenarios or run no test (dry run)"));

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
        Charset mainEncoding = StandardCharsets.UTF_8;
        Charset testEncoding = null;
        Charset acceptanceEncoding = null;
        try {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "--root" -> root = Path.of(args[i + 1]);
                    case "--features" -> features = Path.of(args[i + 1]);
                    case "--allowlist" -> allowlist = Path.of(args[i + 1]);
                    case "--encoding" -> mainEncoding = Charset.forName(args[i + 1]);
                    case "--test-encoding" -> testEncoding = Charset.forName(args[i + 1]);
                    case "--acceptance-encoding" -> acceptanceEncoding = Charset.forName(args[i + 1]);
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
            scanTree(root, root.resolve("src/test/java"), Kind.TEST, testEncoding == null ? mainEncoding : testEncoding,
                    findings);
            scanTree(root, root.resolve("src/acceptanceTest/java"), Kind.TEST,
                    acceptanceEncoding == null ? mainEncoding : acceptanceEncoding, findings);
            scanTree(root, root.resolve("src/main/java"), Kind.MAIN, mainEncoding, findings);
            scanTree(root, features, Kind.FEATURE, StandardCharsets.UTF_8, findings);
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
            err.println("IntegrityCheck: " + e.getMessage());
            return 2;
        }
    }

    /** Other places build logic can hide: extra script plugins under gradle/ and buildSrc/. */
    static List<Path> buildLogic(Path root) throws IOException {
        List<Path> scripts = new ArrayList<>();
        Path gauntlet = root.resolve("gauntlet/gauntlet.gradle");
        for (String dir : List.of("gradle", "buildSrc")) {
            Path d = root.resolve(dir);
            if (Files.isDirectory(d)) {
                scripts.addAll(files(d, p -> Files.isRegularFile(p) && !p.equals(gauntlet) && !p.toString().contains("/build/")
                        && p.toString().matches(".*\\.(gradle|gradle\\.kts|kt|groovy|java)$")));
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
            Matcher m = include.matcher(read(settings, StandardCharsets.UTF_8));
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
            if (Files.isRegularFile(p) && read(p, StandardCharsets.UTF_8).contains("gauntlet.gradle")) {
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

    static void scanTree(Path root, Path dir, Kind kind, Charset encoding, List<Finding> findings) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        String ext = kind == Kind.FEATURE ? ".feature" : ".java";
        for (Path p : files(dir, p -> p.toString().endsWith(ext))) {
            scanFile(root, p, kind, encoding, findings);
        }
    }

    static List<Path> files(Path dir, Predicate<Path> wanted) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(wanted).sorted().toList();
        } catch (IOException | UncheckedIOException e) {
            throw new IOException("cannot list " + dir + ": " + e, e);
        }
    }

    static void scanFile(Path root, Path file, Kind kind, List<Finding> findings) throws IOException {
        scanFile(root, file, kind, StandardCharsets.UTF_8, findings);
    }

    static void scanFile(Path root, Path file, Kind kind, Charset encoding, List<Finding> findings) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String rel = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        byte[] bytes = bytes(file);
        // Lenient on purpose: a byte that is not valid in the charset becomes U+FFFD and the scan goes on.
        String content = new String(bytes, encoding);
        if (kind == Kind.TEST || kind == Kind.MAIN) {
            unreadable(rel, kind, bytes, encoding, content, findings);
        }
        new Source(rel, Lang.of(file), content).scan(kind, findings);
    }

    /**
     * A Java source the rules cannot read is a finding of its own (E1). Text that is not valid in the charset
     * javac compiles it with is a different text for javac: a UTF-16 test read as UTF-8 matches no rule at all.
     * U+0000 is what such a file turns into when the charset can decode any byte - and javac skips it in a name.
     */
    static void unreadable(String rel, Kind kind, byte[] bytes, Charset encoding, String content, List<Finding> findings) {
        int line = undecodableLine(bytes, encoding);
        String why = "source is not valid " + encoding + ", so no rule can read it";
        if (line == 0 && content.indexOf('\0') >= 0) {
            line = (int) content.substring(0, content.indexOf('\0') + 1).lines().count();
            why = "source contains U+0000 (another charset, or a character hidden in a name), so no rule can read it";
        }
        if (line > 0) {
            findings.add(new Finding(rel, line, new Rule("E1", kind, null, false, why),
                    "give the gauntlet the charset javac reads this source with: options.encoding of its JavaCompile task"));
        }
    }

    /** The line of the first byte sequence that is not text in the charset; 0 when the whole file is. */
    static int undecodableLine(byte[] bytes, Charset encoding) {
        CharsetDecoder decoder = encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decoded = CharBuffer.allocate((int) (bytes.length * (double) decoder.maxCharsPerByte()) + 1);
        if (!decoder.decode(ByteBuffer.wrap(bytes), decoded, true).isError()) {
            return 0;
        }
        return (int) (decoded.flip() + "?").lines().count();
    }

    static byte[] bytes(Path file) throws IOException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IOException("cannot read " + file + ": " + e, e);
        }
    }

    /** Build scripts, settings and the allowlist: no javac decides their charset, so a stray byte is not an error. */
    static String read(Path file, Charset encoding) throws IOException {
        return new String(bytes(file), encoding);
    }

    /**
     * A file as the rules see it. {@code text} is the file the way its compiler reads it, {@code code} the same
     * text with every comment blanked, {@code literal} the characters of its string and character literals.
     * Both keep the line structure, so an offset is a place in a line of the file.
     */
    static final class Source {
        final String path;
        final Lang lang;
        final List<String> lines;
        final int[] lineStart;
        final String text;
        final String code;
        final BitSet literal;
        Set<String> declared;
        int[] innermost;

        Source(String path, Lang lang, String content) {
            this.path = path;
            this.lang = lang;
            this.lines = content.lines().toList();
            this.lineStart = new int[lines.size()];
            StringBuilder read = new StringBuilder(content.length() + 1);
            for (int i = 0; i < lines.size(); i++) {
                lineStart[i] = read.length();
                read.append(lang == Lang.JAVA ? asJavacReads(lines.get(i)) : lines.get(i)).append('\n');
            }
            this.text = read.toString();
            Lexer lexer = new Lexer(lang, text);
            this.code = new String(lexer.code);
            this.literal = lexer.literal;
        }

        /**
         * A rule is searched in the whole source - a statement or an annotation may be split over lines - and line
         * by line in a feature or properties file. One finding per rule and line, in the order of the lines.
         */
        void scan(Kind kind, List<Finding> findings) {
            List<Finding> found = new ArrayList<>();
            boolean byLine = lang == Lang.PROPERTIES || lang == Lang.FEATURE;
            for (Rule r : RULES) {
                if (r.kind() != kind) {
                    continue;
                }
                Matcher m = r.pattern().matcher(r.includeComments() ? text : code);
                int reported = -1;
                for (int region = 0; region < (byLine ? lines.size() : 1); region++) {
                    if (byLine) {
                        m.region(lineStart[region], lineStart[region] + lines.get(region).length());
                    }
                    while (m.find()) {
                        int line = lineOf(m.start());
                        if (line != reported && fires(r, kind, m)) {
                            found.add(new Finding(path, line + 1, r, lines.get(line)));
                            reported = line;
                        }
                    }
                }
            }
            found.sort(Comparator.comparingInt(Finding::line));
            findings.addAll(found);
        }

        boolean fires(Rule r, Kind kind, Matcher m) {
            if (kind != Kind.BUILD) {
                return r.includeComments() || !literal.get(m.start());   // "@Disabled" in a string is no annotation
            }
            if (r.id().equals("B2") && TEST_PATTERN.matcher(m.group()).matches()) {
                return filtersGateTests(m.start(), m.end());
            }
            if (r.id().equals("B2") && !SELECTION_ANYWHERE.matcher(m.group()).matches()) {
                return !selectsOtherTests(m.start());      // includeEngines in tasks.register('smokeTest', Test) { }
            }
            // Only "enabled = false" for a packaging/doc task is exempt - ignoreFailures or onlyIf never are.
            return !(r.id().equals("B1") && DISABLED.matcher(m.group()).matches() && switchesOffOwnTask(m.start()));
        }

        /**
         * True when the exclude or include at {@code at} narrows the classes of a Test task the gauntlet runs:
         * called bare in a block that configures such tasks - {@code test { }}, {@code tasks.named('test') { }},
         * {@code tasks.withType(Test).configureEach { }} - or on one: {@code test.exclude}, {@code tasks.test.exclude},
         * {@code it.exclude} in such a block, {@code test."exclude"(...)}. The words are not a finding for anything
         * else: {@code jar { exclude }}, {@code configurations.all { exclude group: }},
         * {@code sourceSets.main.java.exclude}, a named argument ({@code fileTree(dir: d, include: '*.jar')}), the
         * coverage exclusions of {@code test { jacoco { excludes = [...] } }}, or a string that only mentions them.
         * The patterns read ({@code logger.info("${test.includes}")}) narrow nothing either: only written they count.
         */
        boolean filtersGateTests(int at, int end) {
            boolean property = code.startsWith("excludes", at) || code.startsWith("includes", at);
            boolean quoted = literal.get(at);
            if (quoted) {                                  // Groovy calls a method by a string: test."exclude"('x')
                char quote = at > 0 ? code.charAt(at - 1) : ' ';
                if ("\"'`".indexOf(quote) < 0 || end >= code.length() || code.charAt(end) != quote
                        || at > 1 && literal.get(at - 2)) {
                    return false;
                }
                at--;
                end++;
            }
            int after = end;
            while (after < code.length() && (code.charAt(after) == ' ' || code.charAt(after) == '\t')) {
                after++;
            }
            if (after < code.length() && code.charAt(after) == ':' && !code.startsWith("::", after) && !literal.get(after)) {
                return false;                              // a named argument: fileTree(dir: d, include: '*.jar')
            }
            Block block = testBlock(enclosingBrace(at));
            int dot = back(at, true);
            char before = dot == 0 || literal.get(dot - 1) ? ' ' : code.charAt(dot - 1);
            if (property && !written(before == '[' && quoted ? behindBracket(end) : end) && !inWith(at)) {
                return false;                              // read for a log line or a copy: no filter
            }
            if (before != '.' && !(before == '[' && quoted)) {
                // a bare call configures the block's task; test.&exclude and Test::exclude only name the method
                return "@&:".indexOf(before) < 0 && block != null && !block.each() && !quoted;
            }
            int receiverEnd = back(dot - 1, true);
            if (before == '.' && receiverEnd > 0 && "?*".indexOf(code.charAt(receiverEnd - 1)) >= 0) {
                receiverEnd--;                             // tasks.findByName("test")?.exclude, tasks.withType(Test)*.exclude
            } else if (before == '.' && receiverEnd > 1 && code.startsWith("!!", receiverEnd - 2)) {
                receiverEnd -= 2;
            }
            String receiver = chain(chainStart(receiverEnd), receiverEnd);
            if (block != null) {
                if (receiver.equals(block.parameter())) {
                    return true;
                }
                if (receiver.equals("it")) {               // as for B1: in Kotlin "it" there is an outer lambda's
                    return !block.ownParameters() && (lang != Lang.KOTLIN || block.each());
                }
                if (receiver.equals("this") || receiver.equals("delegate") && lang != Lang.KOTLIN) {
                    return !block.each();
                }
            }
            return namesGateTest(receiver, LAST_ID) && !otherSuiteTask(receiver, enclosingBrace(receiverEnd));
        }

        /**
         * True when the property that ends at {@code k} is written - assigned ({@code =}, {@code +=}), added to
         * ({@code <<}) or changed by a call ({@code .add}, {@code .addAll}, {@code .clear}, {@code .remove},
         * {@code .removeAll}, {@code .removeIf}, {@code .retainAll}) - and not only read. Groovy takes the operator
         * and the call from the next line as well: {@code excludes\n    = ['x']}, {@code excludes\n    .add('x')}.
         */
        boolean written(int k) {
            k = skipBlanks(k);
            return k < code.length() && !literal.get(k) && (ASSIGNED.matcher(code).region(k, code.length()).lookingAt()
                    || MUTATED.matcher(code).region(k, code.length()).lookingAt());
        }

        /**
         * The first offset from {@code k} on that is no white space: comments are blank in {@code code} already,
         * and a Groovy backslash at the end of a line is white space too.
         */
        int skipBlanks(int k) {
            while (k < code.length() && !literal.get(k) && (Character.isWhitespace(code.charAt(k))
                    || code.charAt(k) == '\\' && k + 1 < code.length() && code.charAt(k + 1) == '\n')) {
                k++;
            }
            return k;
        }

        /** True when the patterns at {@code at} are the argument of Kotlin's with: {@code with(test.excludes) { }}. */
        boolean inWith(int at) {
            int start = at;
            int dot = back(at, true);
            if (dot > 0 && code.charAt(dot - 1) == '.' && !literal.get(dot - 1)) {
                start = chainStart(back(dot - 1, true));    // with(test.excludes): the receiver starts the argument
            }
            int open = back(start, true);
            return open > 0 && code.charAt(open - 1) == '(' && !literal.get(open - 1)
                    && WITH_CALL.matcher(code.substring(Math.max(0, open - 41), open - 1)).find();
        }

        /** The offset behind the ']' that closes a subscript whose key ends at {@code k}: test['excludes'] = [...]. */
        int behindBracket(int k) {
            k = skipBlanks(k);                             // a line break inside [ ] is no token: test['excludes'\n]
            return k < code.length() && code.charAt(k) == ']' && !literal.get(k) ? k + 1 : k;
        }

        /**
         * The block a brace opens, when it configures Test tasks the gauntlet runs; -1 is no brace. A block in
         * between whose bare calls still reach the task ({@code filter}, {@code useJUnitPlatform}, {@code doFirst})
         * is looked through; any other block is the one the call belongs to.
         */
        Block testBlock(int brace) {
            int owner = ownBrace(brace);
            return owner < 0 ? null : block(owner, chain -> namesGateTest(chain, OWN_TASK) && !namesOtherTest(chain, owner)
                    || innerOfGateTest(chain, owner));
        }

        /** True when the chain is an inner block behind a receiver that names a gate task: test.doFirst, tasks.test.filter. */
        boolean innerOfGateTest(String chain, int brace) {
            Matcher inner = TEST_INNER_OF.matcher(chain);
            return inner.matches() && namesGateTest(inner.group(1), LAST_ID) && !otherSuiteTask(inner.group(1), brace);
        }

        /**
         * True when the test selection at {@code at} (includeEngines, a filter pattern, a JUnit 4 category) sits in
         * the block of a Test task the gauntlet does not run, named as such: {@code tasks.register('smokeTest', Test)
         * { useJUnitPlatform { includeEngines 'junit-jupiter' } }}, {@code task integrationTest(type: Test)},
         * {@code tasks.named('integrationTest')}, the testTask of a suite other than test. These names mean nothing
         * but test selection, so anything else - a gate task, every Test task, a receiver or a block that names no
         * such task - stays a finding.
         */
        boolean selectsOtherTests(int at) {
            int name = at;
            if (literal.get(at) && at > 0 && !literal.get(at - 1) && "\"'`".indexOf(code.charAt(at - 1)) >= 0) {
                name = at - 1;                             // test.filter."includePatterns": the receiver decides
            }
            int dot = back(name, true);
            if (dot > 0 && !literal.get(dot - 1) && (code.charAt(dot - 1) == '.' || code.charAt(dot - 1) == '[')) {
                int end = back(dot - 1, true);
                if (!OWN_SELECTION.matcher(chain(chainStart(end), end)).matches()) {
                    return false;                          // smokeTest.filter.includePatterns: not a block of its own
                }
            }
            int owner = ownBrace(enclosingBrace(at));
            return owner >= 0 && block(owner, chain -> namesOtherTest(chain, owner)) != null;
        }

        /**
         * The block a call in the block at {@code brace} configures: an inner block ({@code filter},
         * {@code doFirst}) and a control block ({@code if (ci) { }}, {@code else}, {@code for}) are looked through -
         * up to a condition that chooses among the Test tasks of a block of several (see selectsTask), which is not.
         */
        int ownBrace(int brace) {
            List<Integer> controls = new ArrayList<>();
            int owner = brace;
            for (; owner >= 0; owner = enclosingBrace(owner)) {
                String chain = chain(chainStart(owner), owner);
                if (CONTROL.matcher(chain).matches()) {
                    controls.add(owner);
                } else if (!TEST_INNER.matcher(chain).matches()) {
                    break;
                }
            }
            Block several = controls.isEmpty() || owner < 0 ? null : block(owner, Source::severalTests);
            for (int control : controls) {
                if (several != null && selectsTask(control, several)) {
                    return control;
                }
            }
            return owner;
        }

        /**
         * True when the condition of the control block at {@code control} chooses which of the Test tasks of the
         * block {@code several} a statement configures: {@code tasks.withType(Test) { if (name == 'integrationTest')
         * { include '**}{@code /*IT*' } }} configures integrationTest only. That takes the task's own name: bare, or
         * behind it, the block's parameter, Groovy's delegate or Kotlin's this - a Groovy closure's this is the
         * script, and this.name the project's. A block of one named task is never several - its one name chooses
         * nothing, {@code test { if (name == 'test') { } }} is looked through - and project.name, a string that holds
         * 'name', a loop variable or a catch parameter called name choose no task.
         */
        boolean selectsTask(int control, Block several) {
            int start = chainStart(control);
            // the condition as written: chain() drops the blanks, and "name in [...]" would read "namein[...]"
            if (NAME_DECLARED.matcher(code).region(start, control).lookingAt()) {
                return false;
            }
            String self = lang == Lang.KOTLIN ? "this" : "delegate";
            Matcher name = NAME_CONDITION.matcher(code).region(start, control);
            while (name.find()) {
                String receiver = name.group(1);
                if (!literal.get(name.start()) && (receiver == null || receiver.equals("it") || receiver.equals(self)
                        || receiver.equals(several.parameter()))) {
                    return true;
                }
            }
            return false;
        }

        /** True when the chain names several Test tasks: {@code tasks.withType(Test)} or the testTask of suites. */
        static boolean severalTests(String chain) {
            chain = PROVIDED.matcher(chain).replaceFirst("");
            Matcher type = TEST_TYPE.matcher(chain);
            Matcher last = LAST_ID.matcher(chain);
            return type.find() && chain.startsWith("withType", type.start())
                    || last.find() && last.group(1).equals("testTask");
        }

        /**
         * True when the chain in front of the block at {@code brace} names one Test task the gauntlet does not run:
         * {@code tasks.register('smokeTest', Test)}, {@code tasks.named('integrationTest')}, {@code tasks.smokeTest},
         * {@code task smokeTest(type: Test)}, {@code task('smokeTest', type: Test)}, Kotlin's
         * {@code val integrationTest by tasks.registering(Test::class)}, a bare {@code smokeTest} that this script
         * declares as a task or suite, or the testTask of a suite other than test.
         */
        boolean namesOtherTest(String chain, int brace) {
            int start = chainStart(brace);
            String ahead = code.substring(Math.max(0, start - 200), start);
            Matcher call = TASK_CALL.matcher(chain);
            Matcher declared = DECLARED.matcher(chain);
            Matcher declaredCall = DECLARED_CALL.matcher(chain);
            Matcher accessor = TASKS_ACCESSOR.matcher(chain);
            String name;
            if (chain.equals("testTask")) {
                return otherSuite(brace);
            } else if (call.find()) {
                name = call.group(3);                      // null: withType(T) names no one task
            } else if (TASK_KEYWORD.matcher(ahead).find() && declared.matches()) {
                name = declared.group(1);
            } else if (declaredCall.find()) {
                name = declaredCall.group(2);
            } else if (DELEGATED.matcher(chain).find()) {
                Matcher val = VAL_BY.matcher(ahead);
                name = val.find() ? val.group(1) : null;
            } else if (accessor.find()) {
                name = accessor.group(1);
            } else if (NAME.matcher(chain).matches() && declared().contains(chain)) {
                name = chain;                              // smokeTest { }: a task this script declares
            } else {
                return false;
            }
            return name != null && !GATE_TEST.matcher(name).matches();
        }

        /** The tasks and JVM test suites this script declares by name. */
        Set<String> declared() {
            if (declared == null) {
                declared = new HashSet<>();
                Matcher m = DECLARATION.matcher(code);
                while (m.find()) {
                    for (int group : new int[] {1, 3, 4, 5}) {
                        if (m.group(group) != null && !literal.get(m.start())) {
                            declared.add(m.group(group));
                        }
                    }
                }
            }
            return declared;
        }

        /** True when the chain is the testTask of a JVM test suite (or its task) and that suite is not test. */
        boolean otherSuiteTask(String chain, int brace) {
            Matcher name = LAST_ID.matcher(PROVIDED.matcher(chain).replaceFirst(""));
            return name.find() && name.group(1).equals("testTask") && otherSuite(brace);
        }

        /**
         * True when the block at {@code brace} sits in a JVM test suite other than test, named as such:
         * {@code suites { integrationTest(JvmTestSuite) { } }}, {@code testing.suites.integrationTest.targets.all { }},
         * {@code suites.named('integrationTest')}, Kotlin's {@code val integrationTest by registering(JvmTestSuite::class)}.
         * Every suite ({@code configureEach}, {@code withType(JvmTestSuite)}), or none that is known, is no other suite.
         */
        boolean otherSuite(int brace) {
            for (int b = brace; b >= 0; b = enclosingBrace(b)) {
                String chain = chain(chainStart(b), b);
                Matcher path = SUITE_PATH.matcher(chain);
                if (path.find()) {
                    return isOtherSuite(path.group(2) != null ? path.group(2) : path.group(3));
                }
                if (SUITE_DELEGATE.matcher(chain).find()) {
                    return isOtherSuite(valName(b));
                }
                int outer = enclosingBrace(b);
                while (outer >= 0 && CONTROL.matcher(chain(chainStart(outer), outer)).matches()) {
                    outer = enclosingBrace(outer);          // suites { if (all) { test { } } }: test is still the child
                }
                if (outer >= 0 && SUITES.matcher(chain(chainStart(outer), outer)).matches()) {
                    return isOtherSuite(suiteName(chain, b));
                }
            }
            return false;
        }

        /**
         * The name a block in {@code suites { }} gives its suite - test, integrationTest(JvmTestSuite), register('x'),
         * Kotlin's {@code val x by registering(JvmTestSuite::class)} - or null when it names none (named(unit), if (x)).
         */
        String suiteName(String chain, int brace) {
            Matcher call = TASK_CALL.matcher(chain);
            if (call.find()) {
                return call.group(3);
            }
            if (DELEGATED.matcher(chain).find()) {
                return valName(brace);
            }
            Matcher block = SUITE_BLOCK.matcher(chain);
            return block.matches() ? block.group(1) : null;
        }

        /** The name of the val a Kotlin delegate in front of this block declares: {@code val x by ...}. */
        String valName(int brace) {
            int start = chainStart(brace);
            Matcher val = VAL_BY.matcher(code.substring(Math.max(0, start - 200), start));
            return val.find() ? val.group(1) : null;
        }

        static boolean isOtherSuite(String name) {
            return name != null && !name.equals("test") && !EVERY_SUITE.matcher(name).matches();
        }

        int lineOf(int offset) {
            int line = Arrays.binarySearch(lineStart, offset);
            return line >= 0 ? line : -line - 2;
        }

        /**
         * True when the statement at this offset sits in a block that configures a packaging/doc task (e.g.
         * Spring Boot's plain jar) and switches off that task itself. The block is the innermost one really open
         * here; {@code jar { test.enabled = false }} disables a gate and is not exempt.
         */
        boolean switchesOffOwnTask(int at) {
            int brace = enclosingBrace(at);
            Block block = brace < 0 ? null : taskBlock(brace);
            if (block == null) {
                return false;
            }
            int end = back(at, true);
            char before = end == 0 ? ' ' : code.charAt(end - 1);
            if (before != '.' || literal.get(end - 1)) {
                // a bare assignment configures the block's task; test.@enabled, test.&setEnabled, test::setEnabled do not
                return "@&:".indexOf(before) < 0 && !block.each();
            }
            end = back(end - 1, true);
            String receiver = chain(chainStart(end), end);
            if (receiver.equals(block.parameter())) {
                return true;                               // nothing in front of it: tasks.test is not the parameter "test"
            }
            if (receiver.equals("it")) {                   // Kotlin configures with a receiver: "it" there is an outer lambda's
                return !block.ownParameters() && (lang != Lang.KOTLIN || block.each());
            }
            if (receiver.equals("this") || receiver.equals("delegate") && lang != Lang.KOTLIN) {
                return !block.each();
            }
            return namesHarmlessTask(receiver, LAST_ID);
        }

        /** The innermost '{' that is open at this offset, or -1. A brace in a comment or a string is no brace. */
        int enclosingBrace(int at) {
            if (innermost == null) {                        // once per file: the rules ask for it again and again
                innermost = new int[code.length() + 1];
                Deque<Integer> open = new ArrayDeque<>();
                for (int k = 0; k <= code.length(); k++) {
                    innermost[k] = open.isEmpty() ? -1 : open.peek();
                    if (k == code.length() || literal.get(k)) {
                        continue;
                    }
                    if (code.charAt(k) == '{') {
                        open.push(k);
                    } else if (code.charAt(k) == '}' && !open.isEmpty()) {
                        open.pop();
                    }
                }
            }
            return innermost[Math.max(0, Math.min(at, code.length()))];
        }

        /**
         * The block this brace opens, when the call chain right in front of it names packaging/doc tasks:
         * {@code jar}, {@code tasks.jar}, {@code tasks.named("jar")}, {@code tasks.withType(Jar).configureEach}.
         * A name that is only mentioned - {@code if (project.hasProperty('jar'))} - opens no such block.
         */
        Block taskBlock(int brace) {
            return block(brace, chain -> namesHarmlessTask(chain, OWN_TASK));
        }

        /** The block this brace opens, when the call chain in front of it names tasks that {@code names} accepts. */
        Block block(int brace, Predicate<String> names) {
            String chain = chain(chainStart(brace), brace);
            Matcher how = CONFIGURER.matcher(chain);
            if (!how.find() || !names.test(chain.substring(0, how.start()))) {
                return null;
            }
            Matcher parameter = CLOSURE_PARAM.matcher(code).region(brace + 1, code.length());
            Matcher head = CLOSURE_HEAD.matcher(code).region(brace + 1, code.length());
            return new Block("each".equals(how.group(1)) || "forEach".equals(how.group(1)),
                    parameter.lookingAt() && isCode(brace + 1, parameter.end()) ? parameter.group(1) : null,
                    head.lookingAt() && isCode(brace + 1, head.end()));
        }

        boolean isCode(int from, int to) {
            int next = literal.nextSetBit(from);
            return next < 0 || next >= to;
        }

        /**
         * Where the call chain that ends at {@code end} starts: names joined by dots, each with its arguments and
         * blocks. A chain that hangs on something else (a string, a bracket, {@code .@}) starts at that joint.
         */
        int chainStart(int end) {
            int k = end;
            while (true) {
                for (int open = opening(back(k, false) - 1); open >= 0; open = opening(back(k, false) - 1)) {
                    k = open;                              // arguments, type arguments and blocks, right to left
                }
                int name = back(k, false);
                while (name > 0 && isNameChar(name - 1)) {
                    name--;
                }
                if (name == back(k, false)) {
                    return k;                              // no name here: the chain hangs on what stands in front
                }
                int joint = back(name, true);
                char before = joint == 0 || literal.get(joint - 1) ? ' ' : code.charAt(joint - 1);
                if ("@&:".indexOf(before) >= 0) {
                    return joint - 1;                      // x.@name, x.&name, x::name: a member of x, not the name itself
                }
                if (before != '.') {
                    return name;
                }
                joint--;
                if (joint > 0 && code.charAt(joint - 1) == '?') {
                    joint--;
                } else if (joint > 1 && code.startsWith("!!", joint - 2)) {
                    joint -= 2;
                }
                k = back(joint, true);
            }
        }

        boolean isNameChar(int k) {
            char c = code.charAt(k);
            return !literal.get(k) && (c == '$' || Character.isJavaIdentifierPart(c) && !Character.isIdentifierIgnorable(c));
        }

        /** The offset behind the last character before {@code k} that is no blank (or no white space at all). */
        int back(int k, boolean overLines) {
            while (k > 0 && !literal.get(k - 1) && (overLines ? Character.isWhitespace(code.charAt(k - 1))
                    : code.charAt(k - 1) == ' ' || code.charAt(k - 1) == '\t')) {
                k--;
            }
            return k;
        }

        /** The bracket that the closing bracket at {@code close} matches: ( ), { } or the < > of type arguments; else -1. */
        int opening(int close) {
            int kind = close < 0 || literal.get(close) ? -1 : ")}>".indexOf(code.charAt(close));
            if (kind < 0) {
                return -1;
            }
            int depth = 0;
            for (int k = close; k >= 0; k--) {
                char c = code.charAt(k);
                if (literal.get(k)) {
                    continue;
                }
                if (c == ")}>".charAt(kind)) {
                    depth++;
                } else if (c == "({<".charAt(kind)) {
                    if (--depth == 0) {
                        return k;
                    }
                } else if (kind == 2 && !Character.isLetterOrDigit(c) && " \t\n.,?*_$".indexOf(c) < 0) {
                    return -1;                             // "a > b" or "->", not type arguments
                }
            }
            return -1;
        }

        /** The chain's text without white space; a block in it is reduced to {} - what it says names no task. */
        String chain(int from, int to) {
            StringBuilder chain = new StringBuilder();
            for (int k = from; k < to; k++) {
                char c = code.charAt(k);
                if (literal.get(k)) {
                    chain.append(c);
                } else if (c == '{') {
                    chain.append("{}");
                    for (int depth = 0; k < to; k++) {
                        if (!literal.get(k) && code.charAt(k) == '{') {
                            depth++;
                        } else if (!literal.get(k) && code.charAt(k) == '}' && --depth == 0) {
                            break;
                        }
                    }
                } else if (!Character.isWhitespace(c)) {
                    chain.append(c);
                }
            }
            return chain.toString();
        }
    }

    /**
     * True when the call chain names Test tasks the gauntlet runs: by type ({@code tasks.withType(Test)}), by name
     * ({@code tasks.named("test")}, {@code tasks.getByName('acceptanceTest')}) or by accessor ({@code test},
     * {@code tasks.test}), the provider of one ({@code tasks.named("test").get()}) too.
     */
    static boolean namesGateTest(String chain, Pattern accessor) {
        chain = PROVIDED.matcher(chain).replaceFirst("");
        if (TEST_TYPE.matcher(chain).find()) {
            return true;
        }
        Matcher call = TASK_CALL.matcher(chain);
        if (call.find()) {
            return call.group(3) != null && GATE_TEST.matcher(call.group(3)).matches();
        }
        Matcher name = accessor.matcher(chain);
        return name.find() && GATE_TEST.matcher(name.group(1)).matches();
    }

    /** True when the call chain names packaging/doc tasks by name or by type, not merely mentions one. */
    static boolean namesHarmlessTask(String chain, Pattern accessor) {
        Matcher call = TASK_CALL.matcher(chain);
        if (call.find()) {
            return Stream.of(1, 3, 4, 5).map(call::group).anyMatch(n -> n != null && HARMLESS_NAME.matcher(n).matches());
        }
        Matcher name = accessor.matcher(chain);
        return name.find() && HARMLESS_NAME.matcher(name.group(1)).matches();
    }

    /**
     * A Java source line as javac reads it (JLS 3.3, 3.8). Unicode escapes are resolved before anything else and
     * a character that is ignorable in an identifier (U+0000, a soft hyphen, a zero-width space) does not count
     * inside one: {@code @Dis&#92;u0061bled} and {@code @Dis<U+00AD>abled} both compile as {@code @Disabled}.
     */
    static String asJavacReads(String line) {
        StringBuilder unescaped = new StringBuilder(line.length());
        int backslashes = 0;                               // a backslash after an odd number of them starts no escape
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            int digits = i + 1;
            while (c == '\\' && digits < line.length() && line.charAt(digits) == 'u') {
                digits++;
            }
            if (c == '\\' && backslashes % 2 == 0 && digits > i + 1 && digits + 4 <= line.length()
                    && line.substring(digits, digits + 4).matches("[0-9a-fA-F]{4}")) {
                char escaped = (char) Integer.parseInt(line.substring(digits, digits + 4), 16);
                unescaped.append(escaped == '\r' ? '\n' : escaped);   // an escaped line end ends a comment as well
                i = digits + 3;
                backslashes = 0;
            } else {
                unescaped.append(c);
                backslashes = c == '\\' ? backslashes + 1 : 0;
            }
        }
        StringBuilder read = new StringBuilder(unescaped.length());
        boolean identifier = false;
        for (int i = 0, c; i < unescaped.length(); i += Character.charCount(c)) {
            c = unescaped.codePointAt(i);
            if (Character.isIdentifierIgnorable(c)) {
                if (identifier) {
                    continue;
                }
            } else {
                identifier = Character.isJavaIdentifierPart(c);
            }
            read.appendCodePoint(c);
        }
        return read.toString();
    }

    /**
     * Reads a source the way its compiler does, as far as the rules need it: what is a comment, what is a string
     * and what is code. Comments are blanked in {@code code}; the characters of string and character literals
     * are set in {@code literal} and keep their text (a rule needs the 'jar' of tasks.named('jar')); the code
     * inside a string template {@code ${...}} is code again. Follows the lexers of javac, Groovy and Kotlin:
     * block comments nest in Kotlin only, a Groovy {@code /} starts a slashy string only where the previous
     * token allows a regular expression, {@code $/.../$} is a string, a text block or raw string ends at its
     * own three quotes.
     */
    static final class Lexer {
        /** After a Groovy keyword a slash starts a string; after a name, a literal or this/null/true/false it divides. */
        static final Set<String> GROOVY_KEYWORDS = Set.of("as", "def", "in", "trait", "threadsafe", "var", "boolean",
                "char", "byte", "short", "int", "long", "float", "double", "abstract", "assert", "break", "case", "catch",
                "class", "const", "continue", "default", "do", "else", "enum", "extends", "final", "finally", "for", "if",
                "goto", "implements", "import", "instanceof", "interface", "native", "new", "package", "private",
                "protected", "public", "return", "static", "strictfp", "super", "switch", "synchronized", "throw",
                "throws", "transient", "try", "void", "volatile", "while");

        final Lang lang;
        final String s;
        final char[] code;
        final BitSet literal = new BitSet();
        final Deque<Character> open = new ArrayDeque<>();  // Groovy: a line break inside ( ) or [ ] is no token
        boolean regex = true;                              // Groovy: a '/' here starts a slashy string
        boolean afterTry;                                  // Groovy: "try (" keeps its line breaks
        int i;

        Lexer(Lang lang, String text) {
            this.lang = lang;
            this.s = text;
            this.code = text.toCharArray();
            if (lang == Lang.PROPERTIES || lang == Lang.FEATURE) {
                commentLines();
                return;
            }
            scriptHeader();
            code(false);
        }

        /** Feature and properties files know only comment lines: '#', in a properties file '!' as well. */
        void commentLines() {
            for (int start = 0; start < s.length(); start = lineEnd(start) + 1) {
                int first = start;
                while (first < lineEnd(start) && s.charAt(first) <= ' ') {
                    first++;
                }
                if (at(first) == '#' || at(first) == '!' && lang == Lang.PROPERTIES) {
                    blank(start, lineEnd(start));
                }
            }
        }

        /** "#!" lines at the very start of a script are a comment (Groovy: after blanks too, and line after line). */
        void scriptHeader() {
            int start = 0;
            while (lang == Lang.GROOVY && (at(start) == ' ' || at(start) == '\t')) {
                start++;
            }
            while (lang != Lang.JAVA && s.startsWith("#!", start)) {
                i = lineEnd(start);
                blank(start, i);
                start = lang == Lang.GROOVY ? i + 1 : s.length();
            }
        }

        /** Code up to the end of the text or, in a string template, up to the brace that closes the template. */
        void code(boolean template) {
            int depth = 0;
            while (i < s.length()) {
                char c = s.charAt(i);
                char next = at(i + 1);
                if (c == '/' && next == '/') {
                    blank(i, lineEnd(i));
                    i = lineEnd(i);
                    if (!inParens()) {
                        token(true);
                    }
                } else if (c == '/' && next == '*') {
                    blockComment();
                } else if (c == '"' || c == '\'') {
                    quoted(c);
                    token(false);
                } else if (c == '`' && lang == Lang.KOTLIN) {
                    backticked();
                } else if (c == '$' && next == '/' && lang == Lang.GROOVY && dollarSlashy()) {
                    token(false);
                } else if (c == '/' && lang == Lang.GROOVY && regex && slashy()) {
                    token(false);
                } else if (isWordStart(i)) {
                    int from = i;
                    while (i < s.length() && isWordStart(i)) {
                        i += Character.charCount(s.codePointAt(i));
                    }
                    String word = s.substring(from, i);
                    token(GROOVY_KEYWORDS.contains(word));
                    afterTry = word.equals("try");
                } else if (c == '{' || c == '(' || c == '[') {
                    depth += c == '{' ? 1 : 0;
                    open.push(c == '(' && afterTry ? 't' : c);
                    token(true);
                    i++;
                } else if (c == '}' || c == ')' || c == ']') {
                    if (c == '}' && depth-- == 0) {
                        if (template) {
                            return;
                        }
                        depth = 0;
                    }
                    open.poll();
                    token(false);
                    i++;
                } else if (c == '\n') {
                    if (!inParens()) {
                        token(true);
                    }
                    i++;
                } else if (c == ' ' || c == '\t') {
                    i++;
                } else if (c == '\\' && next == '\n') {
                    i += 2;                                // Groovy: an escaped line break is white space
                } else if ((c == '+' || c == '-') && next == c) {
                    token(false);                          // after ++ and -- a slash divides
                    i += 2;
                } else {
                    token(true);
                    i++;
                }
            }
        }

        void token(boolean regexMayFollow) {
            regex = regexMayFollow;
            afterTry = false;
        }

        boolean inParens() {
            return !open.isEmpty() && (open.peek() == '(' || open.peek() == '[');
        }

        boolean isWordStart(int k) {
            int c = s.codePointAt(k);
            return c < 128 ? c == '$' || c == '_' || Character.isLetterOrDigit(c)
                    : Character.isJavaIdentifierPart(c) && !Character.isIdentifierIgnorable(c);
        }

        void blockComment() {
            int from = i;
            i += 2;
            for (int depth = 1; depth > 0 && i < s.length(); ) {
                if (s.startsWith("*/", i)) {
                    depth--;
                    i += 2;
                } else if (s.startsWith("/*", i) && lang == Lang.KOTLIN) {
                    depth++;                               // Kotlin comments nest, Java and Groovy end at the first */
                    i += 2;
                } else {
                    i++;
                }
            }
            blank(from, Math.min(i, s.length()));
            // Groovy takes a comment that ends its line for the line break itself
            if (!inParens() && s.substring(Math.min(i, s.length()), lineEnd(i)).isBlank()) {
                token(true);
            }
        }

        /** A quoted literal: "..." and '...' end with their line, three quotes open one that does not. */
        void quoted(char quote) {
            int from = i;
            boolean triple = s.startsWith(String.valueOf(quote).repeat(3), i) && (quote == '"' || lang == Lang.GROOVY);
            boolean raw = triple && lang == Lang.KOTLIN;
            boolean templates = quote == '"' && lang != Lang.JAVA;
            int dollars = 0;                               // Kotlin: $$"..." takes $${ } for a template
            while (lang == Lang.KOTLIN && from > dollars && s.charAt(from - dollars - 1) == '$') {
                dollars++;
            }
            boolean gstring = false;                       // Groovy: a string with a $ ends at the first three quotes
            i += triple ? 3 : 1;
            while (i < s.length()) {
                char c = s.charAt(i);
                int run = 1;
                while ((c == quote || c == '$') && at(i + run) == c) {
                    run++;
                }
                if (c == '\\' && !raw) {
                    i += at(i + 1) == '\n' && !triple && lang != Lang.GROOVY ? 1 : 2;   // only Groovy escapes a line break
                } else if (c == '\n' && !triple) {
                    break;                                 // unterminated: the literal ends with its line
                } else if (c == quote && !triple) {
                    i++;
                    break;
                } else if (c == quote && run >= 3) {
                    // Kotlin closes with the last three quotes of a run, Java with the first; Groovy takes one or
                    // two quotes in front of the closing three into the string
                    i += lang == Lang.KOTLIN || lang == Lang.GROOVY && !gstring && run < 6 ? run : 3;
                    break;
                } else if (c == '$' && templates && lang == Lang.GROOVY) {
                    gstring = true;
                    if (at(i + 1) == '{') {
                        from = template(from, i + 2);
                    }
                    i++;
                } else if (c == '$' && templates) {
                    if (at(i + run) == '`' && lineEnd(i) > s.indexOf('`', i + run + 1) && s.indexOf('`', i + run + 1) > i + run + 1) {
                        i = s.indexOf('`', i + run + 1);   // $`a name`: its quotes and braces are part of the name
                    } else if (at(i + run) == '{' && run >= Math.max(dollars, 1)) {
                        from = template(from, i + run + 1);
                    } else {
                        i += run - 1;
                    }
                    i++;
                } else {
                    i++;
                }
            }
            literal.set(from, Math.min(i, s.length()));
        }

        /** Kotlin `a name`: an identifier that may hold quotes and braces. */
        void backticked() {
            int close = s.indexOf('`', i + 1);
            if (close > i + 1 && close < lineEnd(i)) {
                literal.set(i, close + 1);
                i = close;
            }
            i++;
        }

        /**
         * The code of a template that starts behind "${": the string so far is literal, the code is not. Returns
         * the place of the closing brace, where the string goes on.
         */
        int template(int from, int codeStart) {
            literal.set(from, codeStart);
            i = codeStart;
            open.push('{');
            token(true);
            code(true);
            open.poll();
            return i;
        }

        /**
         * Groovy /.../ where a regular expression may start. The lexer takes the longest text it can: an escaped
         * slash ends the literal only when no other slash does. A $name or ${ makes it a GString.
         */
        boolean slashy() {
            int end = -1;
            for (int j = i + 1; j < s.length() && s.charAt(j) != '\0'; j++) {
                char c = s.charAt(j);
                if (c == '\\' && at(j + 1) == '/') {
                    end = ++j + 1;
                } else if (c == '/') {
                    end = j + 1;
                    break;
                } else if (c == '$' && interpolates(j + 1)) {
                    gstring(j, false);
                    return true;
                }
            }
            if (end < 0) {
                return false;                              // no closing slash: a division
            }
            literal.set(i, end);
            i = end;
            return true;
        }

        /**
         * Groovy $/.../$. "$/" and "$$" are escapes - or a lone dollar and what follows it - so every reading is
         * followed and the longest literal wins, as in the lexer; a $name or ${ that is reached first makes it a
         * GString.
         */
        boolean dollarSlashy() {
            int end = -1;
            int gstring = -1;
            int lastDollar = s.lastIndexOf('$');
            boolean here = true;
            boolean next = false;
            boolean afterNext = false;
            for (int p = i + 2; p <= lastDollar && (here || next || afterNext); p++) {
                char c = s.charAt(p);
                char d = at(p + 1);
                if (here && c == '$') {
                    afterNext |= d == '$' || d == '/';
                    if (interpolates(p + 1)) {
                        gstring = p;
                    } else {
                        next = true;
                    }
                } else if (here && c == '/' && d == '$') {
                    end = p > i + 2 ? p + 2 : end;
                } else if (here && c != '\0') {
                    next = true;
                }
                here = next;
                next = afterNext;
                afterNext = false;
            }
            if (end > gstring) {
                literal.set(i, end);
                i = end;
            } else if (gstring >= 0) {
                gstring(gstring, true);
            }
            return end >= 0 || gstring >= 0;
        }

        /** The rest of a slashy or dollar-slashy GString from its first $: templates, escapes, the closing mark. */
        void gstring(int firstDollar, boolean dollarSlashy) {
            int from = i;
            i = firstDollar;
            while (i < s.length()) {
                char c = s.charAt(i);
                char next = at(i + 1);
                if (dollarSlashy ? c == '/' && next == '$' : c == '/') {
                    i += dollarSlashy ? 2 : 1;
                    break;
                } else if (c == '$' && next == '{') {
                    from = template(from, i + 2);
                    i++;
                } else if (dollarSlashy ? c == '$' && (next == '$' || next == '/') : c == '\\' && next == '/') {
                    i += 2;
                } else {
                    i++;
                }
            }
            literal.set(from, Math.min(i, s.length()));
        }

        /** Groovy: a dollar in a slashy string starts a template only in front of a name or a brace. */
        boolean interpolates(int k) {
            int c = k < s.length() ? s.codePointAt(k) : 0;
            return c == '{' || c == '_' || c < 128 && Character.isLetter(c) || c >= 128 && Character.isJavaIdentifierPart(c);
        }

        char at(int k) {
            return k < s.length() ? s.charAt(k) : '\0';
        }

        int lineEnd(int k) {
            int end = s.indexOf('\n', Math.min(k, s.length()));
            return end < 0 ? s.length() : end;
        }

        void blank(int from, int to) {
            for (int k = from; k < to; k++) {
                if (code[k] != '\n') {
                    code[k] = ' ';
                }
            }
        }
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
        for (String raw : read(file, StandardCharsets.UTF_8).lines().toList()) {
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
