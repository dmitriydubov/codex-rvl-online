import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Test census: every test class a gate's Test task compiled must have run.
 *
 * <p>The integrity check (B2) reads the build script for test filters, and each review round found a form it does
 * not see: a variable, {@code tasks['test']}, {@code matching { }}, a closure parameter, a filter of the JUnit
 * Platform outside the script. The census judges the effect instead. After the task ran, every test class compiled
 * into its test classes directories must appear in its JUnit XML results (a testcase, passed, failed or skipped).
 * A class that never appears is finding T6, accepted only by the human in the integrity allowlist:
 * <pre>
 *   &lt;path glob relative to the project directory&gt;  T6   # reason
 *   src/test/java/**&#47;LegacyReportIT.java          T6   # runs nightly against a real Oracle, not in the gate
 * </pre>
 * The path is the class's source file when the task's source roots hold it, else the class file.
 *
 * <p>What counts as a test class follows the discovery of the test frameworks (checked on Gradle 8.14.3), and
 * errs on the side of counting less: a class counted that the framework never runs would be a red no fix can
 * clear. JUnit Jupiter: a method annotated {@code @Test}, {@code @RepeatedTest}, {@code @TestFactory},
 * {@code @TestTemplate} or {@code @ParameterizedTest}, not static, private or abstract (a factory returns a value,
 * the others void), declared in the class, in a superclass or as a default method of an interface among the
 * scanned classes; the class is top-level or static nested, or an inner class marked {@code @Nested} in such a
 * class, and is not abstract or private. JUnit 4: a public void instance method annotated {@code @org.junit.Test}
 * of the class or of a scanned superclass, in a public class (the vintage engine discovers no other). TestNG: a
 * void instance method of the class itself annotated {@code @org.testng.annotations.Test}, or a public no-argument
 * one when the class carries it (Gradle's TestNG detection passes on no inherited ones), minus what
 * {@code @Ignore} takes out - TestNG reports nothing for it, and the integrity check (T3) judges it.
 * Not recognised, so never a finding: custom meta-annotations, JUnit Platform suites, Spock, JUnit 3.
 *
 * <pre>
 * java TestCensus.java --project-dir . [--allowlist gauntlet/config/integrity-allowlist.txt]
 *     --task test --classes-file &lt;list&gt; [--sources-file &lt;list&gt;] (--results &lt;dir&gt; | --not-run &lt;outcome&gt;)
 *     [--task archTest ...]
 * </pre>
 * The list files hold absolute paths, each ended by a NUL character (gauntlet.gradle writes them): the task's test
 * classes directories and the source roots of the source sets that compile into them. {@code --results} is the
 * task's JUnit XML directory; {@code --not-run} says the task ran no test in this build (SKIPPED), so nothing on
 * disk counts. Exit codes: 0 = every test class ran or is allowlisted, 1 = findings, 2 = usage or input error.
 */
public class TestCensus {

    static final String RULE = "T6";

    // access flags (JVMS 4.1, 4.6, 4.7.6)
    static final int PUBLIC = 0x0001;
    static final int PRIVATE = 0x0002;
    static final int STATIC = 0x0008;
    static final int BRIDGE = 0x0040;
    static final int INTERFACE = 0x0200;
    static final int ABSTRACT = 0x0400;
    static final int SYNTHETIC = 0x1000;
    static final int ANNOTATION = 0x2000;
    static final int ENUM = 0x4000;
    static final int MODULE = 0x8000;

    static final Set<String> JUPITER = Set.of("org/junit/jupiter/api/Test", "org/junit/jupiter/api/RepeatedTest",
            "org/junit/jupiter/api/TestTemplate", "org/junit/jupiter/params/ParameterizedTest");
    static final String JUPITER_FACTORY = "org/junit/jupiter/api/TestFactory";
    static final String NESTED = "org/junit/jupiter/api/Nested";
    static final String JUNIT4 = "org/junit/Test";
    static final String TESTNG = "org/testng/annotations/Test";
    static final String TESTNG_IGNORE = "org/testng/annotations/Ignore";
    static final String TESTNG_ANNOTATIONS = "org/testng/annotations/";

    enum Nesting { TOP, MEMBER, LOCAL, ANONYMOUS }

    record Method(String name, String descriptor, int access, Set<String> annotations) {
        /** Name and parameter types: a method of a subclass with the same ones hides this one. */
        String signature() {
            return name + descriptor.substring(0, descriptor.indexOf(')') + 1);
        }

        boolean returnsVoid() {
            return descriptor.endsWith(")V");
        }

        boolean is(int flag) {
            return (access & flag) != 0;
        }
    }

    /** What the census reads of a class file. For a member class, {@code innerAccess} is its declared access. */
    record ClassFile(String name, String superName, List<String> interfaces, int access, Set<String> annotations,
                     List<Method> methods, String sourceFile, Nesting nesting, String outer, int innerAccess, Path file) {
        boolean is(int flag) {
            return (access & flag) != 0;
        }

        /** Private, static, public as declared: a nested class keeps them in InnerClasses, not in its own flags. */
        boolean declared(int flag) {
            return ((nesting == Nesting.MEMBER ? innerAccess : access) & flag) != 0;
        }

        String packageName() {
            int slash = name.lastIndexOf('/');
            return slash < 0 ? "" : name.substring(0, slash);
        }

        String binaryName() {
            return name.replace('/', '.');
        }
    }

    record Allow(PathMatcher matcher, String ruleId) {
    }

    /** One Test task: its compiled classes, where their sources live, and its results (null: it ran nothing). */
    record Task(String name, List<Path> classDirs, List<Path> sourceRoots, Path results, String notRun,
                List<Path> notCompiled) {
    }

    /** A test class that did not run, or (className null) a test source the build left out of the compilation. */
    record Finding(String task, String path, String className) {
    }

    public static void main(String[] args) {
        System.exit(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)));
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path projectDir = Path.of(".");
        Path allowlist = null;
        List<Task> tasks = new ArrayList<>();
        try {
            String name = null;
            List<Path> classDirs = null;
            List<Path> sourceRoots = List.of();
            Path results = null;
            String notRun = null;
            List<Path> notCompiled = List.of();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(args[i] + " needs a value");
                }
                String value = args[i + 1];
                switch (args[i]) {
                    case "--project-dir" -> projectDir = Path.of(value);
                    case "--allowlist" -> allowlist = Path.of(value);
                    case "--task" -> {
                        if (name != null) {
                            tasks.add(task(name, classDirs, sourceRoots, results, notRun, notCompiled));
                        }
                        name = value;
                        classDirs = null;
                        sourceRoots = List.of();
                        results = null;
                        notRun = null;
                        notCompiled = List.of();
                    }
                    case "--classes-file" -> classDirs = paths(value);
                    case "--sources-file" -> sourceRoots = paths(value);
                    case "--results" -> results = Path.of(value);
                    case "--not-run" -> notRun = value;
                    case "--not-compiled-file" -> notCompiled = paths(value);
                    default -> throw new IllegalArgumentException("unknown option " + args[i]);
                }
                if (name == null && List.of("--classes-file", "--sources-file", "--results", "--not-run",
                        "--not-compiled-file").contains(args[i])) {
                    throw new IllegalArgumentException(args[i] + " before any --task");
                }
            }
            if (name == null) {
                throw new IllegalArgumentException("no --task");
            }
            tasks.add(task(name, classDirs, sourceRoots, results, notRun, notCompiled));
        } catch (RuntimeException e) {
            err.println("TestCensus: " + e.getMessage());
            err.println("usage: java TestCensus.java --project-dir <dir> [--allowlist <file>] --task <name> --classes-file "
                    + "<list> [--sources-file <list>] [--not-compiled-file <list>] (--results <dir> | --not-run <outcome>) "
                    + "[--task ...]");
            return 2;
        }
        projectDir = projectDir.toAbsolutePath().normalize();
        try {
            List<Allow> allows = readAllowlist(allowlist);
            List<Finding> findings = new ArrayList<>();
            int allowed = 0;
            List<String> counts = new ArrayList<>();
            for (Task task : tasks) {
                Map<String, ClassFile> classes = readClasses(task.classDirs(), err);
                List<ClassFile> tests = testClasses(classes);
                Set<String> ran = task.results() == null ? Set.of() : ranClasses(task.results());
                if (task.results() == null && !tests.isEmpty()) {
                    out.println("Test census: " + task.name() + " was " + task.notRun() + " in this build, so none of its "
                            + tests.size() + " test class(es) ran");
                }
                Sources sources = new Sources(task.sourceRoots());
                int missing = 0;
                for (ClassFile test : tests) {
                    if (ran.contains(test.binaryName())) {
                        continue;
                    }
                    missing++;
                    Finding finding = new Finding(task.name(), sources.path(test, projectDir), test.binaryName());
                    if (allowed(finding, allows)) {
                        allowed++;
                    } else {
                        findings.add(finding);
                    }
                }
                counts.add(task.name() + " " + (tests.size() - missing) + "/" + tests.size());
                // a test source on disk that the compile task leaves out (a source-set or a compile-task exclude): it
                // has no class, so nothing above can miss it, and nothing at all would run it
                for (Path source : task.notCompiled()) {
                    Finding finding = new Finding(task.name(), relative(projectDir, source.toAbsolutePath().normalize()), null);
                    if (allowed(finding, allows)) {
                        allowed++;
                    } else {
                        findings.add(finding);
                    }
                }
            }
            return print(findings, allowed, counts, out);
        } catch (IOException | UncheckedIOException e) {
            err.println("TestCensus: " + e.getMessage());
            return 2;
        }
    }

    static Task task(String name, List<Path> classDirs, List<Path> sourceRoots, Path results, String notRun,
                     List<Path> notCompiled) {
        if (classDirs == null) {
            throw new IllegalArgumentException("--task " + name + " has no --classes-file");
        }
        if ((results == null) == (notRun == null)) {
            throw new IllegalArgumentException("--task " + name + " needs one of --results and --not-run");
        }
        return new Task(name, classDirs, sourceRoots, results, notRun, notCompiled);
    }

    static int print(List<Finding> findings, int allowed, List<String> counts, PrintStream out) {
        findings.sort((a, b) -> (a.task() + '\0' + a.path() + '\0' + a.className())
                .compareTo(b.task() + '\0' + b.path() + '\0' + b.className()));
        long notCompiled = findings.stream().filter(f -> f.className() == null).count();
        for (Finding f : findings) {
            out.println(f.className() == null
                    ? "INTEGRITY " + RULE + " " + f.path() + "  not compiled for " + f.task()
                            + "  |  the build leaves this test source out of the compilation"
                    : "INTEGRITY " + RULE + " " + f.path() + "  compiled but not run by " + f.task() + "  |  " + f.className());
        }
        String ran = "test classes run (ran/compiled): " + String.join(", ", counts)
                + (allowed > 0 ? " (" + allowed + " allowlisted)" : "");
        if (findings.isEmpty()) {
            out.println("Test census: OK - " + ran);
            return 0;
        }
        out.println("Test census: FAILED - " + (findings.size() - notCompiled) + " test class(es) compiled but not run"
                + (notCompiled > 0 ? ", " + notCompiled + " test source(s) not compiled" : "") + "; " + ran + ".");
        out.println("  Something keeps them out of the run: a filter of the Test task (include/exclude, filter, tags,");
        out.println("  engines), a test framework or engine the task does not have, a filter of the test platform, an");
        out.println("  exclude of the test source set or its compile task.");
        out.println("  Fix the cause. Only the human can accept a finding, in gauntlet/config/integrity-allowlist.txt "
                + "(path-glob " + RULE + ").");
        return 1;
    }

    // ------------------------------------------------------------------------------ what JUnit and TestNG run

    static List<ClassFile> testClasses(Map<String, ClassFile> classes) {
        Set<String> ignoredPackages = new HashSet<>();
        for (ClassFile c : classes.values()) {
            if (c.name().endsWith("/package-info") && c.annotations().contains(TESTNG_IGNORE)) {
                ignoredPackages.add(c.packageName());
            }
        }
        List<ClassFile> tests = new ArrayList<>();
        for (ClassFile c : classes.values()) {
            if (jupiter(c, classes) || junit4(c, classes) || testng(c, classes, ignoredPackages)) {
                tests.add(c);
            }
        }
        return tests;
    }

    /**
     * A class a framework can instantiate as a test: not an interface, an abstract class, an annotation, an enum, a
     * module descriptor or synthetic, and not private, local or anonymous - none of them runs as a test class.
     */
    static boolean concrete(ClassFile c) {
        return !c.is(INTERFACE | ABSTRACT | ANNOTATION | ENUM | MODULE | SYNTHETIC)
                && (c.nesting() == Nesting.TOP || c.nesting() == Nesting.MEMBER)
                && !c.declared(PRIVATE) && !c.name().endsWith("/package-info") && !c.name().equals("module-info");
    }

    /** Top-level, or static nested: the classes Gradle hands the framework as test classes of their own. */
    static boolean standalone(ClassFile c) {
        return c.nesting() == Nesting.TOP || c.declared(STATIC);
    }

    /**
     * Jupiter runs a top-level or static nested class, and an inner class marked @Nested when the class around it
     * runs (Gradle passes no inner class itself: Jupiter finds it through the outer one).
     */
    static boolean jupiterContainer(ClassFile c, Map<String, ClassFile> classes, int depth) {
        if (!concrete(c) || depth > 64) {
            return false;
        }
        if (standalone(c)) {
            return true;
        }
        ClassFile outer = classes.get(c.outer());
        return c.annotations().contains(NESTED) && outer != null && jupiterContainer(outer, classes, depth + 1);
    }

    static boolean jupiter(ClassFile c, Map<String, ClassFile> classes) {
        if (!jupiterContainer(c, classes, 0)) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        List<ClassFile> chain = superclasses(c, classes);
        for (ClassFile k : chain) {
            for (Method m : k.methods()) {
                if (seen.add(m.signature()) && jupiterTest(m)) {
                    return true;
                }
            }
        }
        // default methods of the test interfaces of the class and its superclasses, nearest first
        List<String> queue = new ArrayList<>();
        chain.forEach(k -> queue.addAll(k.interfaces()));
        Set<String> visited = new HashSet<>();
        for (int i = 0; i < queue.size() && i < 1024; i++) {
            ClassFile face = classes.get(queue.get(i));
            if (face == null || !visited.add(face.name())) {
                continue;
            }
            for (Method m : face.methods()) {
                if (seen.add(m.signature()) && jupiterTest(m)) {
                    return true;
                }
            }
            queue.addAll(face.interfaces());
        }
        return false;
    }

    /** JUnit Jupiter's IsTestMethod, IsTestFactoryMethod and IsTestTemplateMethod, by the annotations themselves. */
    static boolean jupiterTest(Method m) {
        if (m.is(STATIC | PRIVATE | ABSTRACT | SYNTHETIC | BRIDGE)) {
            return false;
        }
        if (m.annotations().contains(JUPITER_FACTORY)) {
            return !m.returnsVoid();
        }
        return m.returnsVoid() && m.annotations().stream().anyMatch(JUPITER::contains);
    }

    /**
     * JUnit 4: the vintage engine discovers a public class only, top-level or static nested; Gradle's own JUnit 4
     * runner fails another one with an initialization error, which is a testcase of its own.
     */
    static boolean junit4(ClassFile c, Map<String, ClassFile> classes) {
        if (!concrete(c) || !standalone(c) || !c.declared(PUBLIC)) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (ClassFile k : superclasses(c, classes)) {
            for (Method m : k.methods()) {
                if (seen.add(m.signature()) && m.annotations().contains(JUNIT4) && m.is(PUBLIC) && m.returnsVoid()
                        && !m.is(STATIC | ABSTRACT | SYNTHETIC | BRIDGE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * TestNG under Gradle: the class's own methods only (Gradle's detection does not take an abstract superclass's
     * tests for the subclass's, checked on 8.14.3), and nothing that @Ignore - on the method, the class or the
     * package - takes out. A class-level @Test makes every public method a test; @Test(enabled = false) counts as a
     * test that did not run: no integrity rule judges it.
     */
    static boolean testng(ClassFile c, Map<String, ClassFile> classes, Set<String> ignoredPackages) {
        if (!concrete(c) || !standalone(c) || ignoredPackages.contains(c.packageName())) {
            return false;
        }
        for (ClassFile k : superclasses(c, classes)) {
            if (k.annotations().contains(TESTNG_IGNORE)) {
                return false;
            }
        }
        boolean classLevel = c.annotations().contains(TESTNG);
        for (Method m : c.methods()) {
            if (m.is(STATIC | PRIVATE | ABSTRACT | SYNTHETIC | BRIDGE) || !m.returnsVoid() || m.name().startsWith("<")
                    || m.annotations().contains(TESTNG_IGNORE)) {
                continue;
            }
            if (m.annotations().contains(TESTNG)) {
                return true;
            }
            if (classLevel && m.is(PUBLIC) && m.descriptor().equals("()V")
                    && m.annotations().stream().noneMatch(a -> a.startsWith(TESTNG_ANNOTATIONS))) {
                return true;
            }
        }
        return false;
    }

    /** The class and its superclasses among the scanned classes, nearest first. */
    static List<ClassFile> superclasses(ClassFile c, Map<String, ClassFile> classes) {
        List<ClassFile> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ClassFile k = c; k != null && visited.add(k.name()); k = k.superName() == null ? null : classes.get(k.superName())) {
            chain.add(k);
        }
        return chain;
    }

    // ------------------------------------------------------------------------------ results

    /**
     * The classes the JUnit XML results name: the classname of every testcase. Only Gradle's own TEST-*.xml: Gradle
     * deletes those when the task runs again, and any other file in the directory would outlive the run it claims.
     */
    static Set<String> ranClasses(Path results) throws IOException {
        Set<String> ran = new HashSet<>();
        if (!Files.isDirectory(results)) {
            return ran;
        }
        List<Path> reports;
        try (Stream<Path> s = Files.list(results)) {
            reports = s.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("TEST-") && name.endsWith(".xml") && Files.isRegularFile(p);
            }).sorted().toList();
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            // a test result names classes; it has no business pulling in a DTD or an entity from elsewhere
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            throw new IOException("cannot configure the XML parser: " + e, e);
        }
        DefaultHandler handler = new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                if (qName.equals("testcase") && attributes.getValue("classname") != null) {
                    ran.add(attributes.getValue("classname"));
                }
            }
        };
        for (Path report : reports) {
            try {
                SAXParser parser = factory.newSAXParser();
                // JDK 24 and newer count every reference to a built-in entity (&quot; in a parameterized test's name)
                // against jdk.xml.maxGeneralEntitySizeLimit, 100,000 per document: a data-driven test class with
                // thousands of cases stopped the census. No entity of the report's own can exist (a DOCTYPE is
                // refused), so the size limits are lifted.
                for (String limit : List.of("maxGeneralEntitySizeLimit", "totalEntitySizeLimit")) {
                    try {
                        parser.setProperty("http://www.oracle.com/xml/jaxp/properties/" + limit, "0");
                    } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException olderParser) {
                    }
                }
                parser.parse(report.toFile(), handler);
            } catch (Exception e) {
                throw new IOException("cannot read the test results " + report + ": " + e, e);
            }
        }
        return ran;
    }

    // ------------------------------------------------------------------------------ where a class comes from

    /** Finds the source file of a class in the task's source roots; else the census names the class file. */
    static final class Sources {
        private final List<Path> roots;
        private Map<String, List<Path>> byName;

        Sources(List<Path> roots) {
            this.roots = roots;
        }

        String path(ClassFile c, Path projectDir) throws IOException {
            Path source = c.sourceFile() == null ? null : find(c.packageName(), c.sourceFile());
            return relative(projectDir, source != null ? source : c.file());
        }

        private Path find(String pkg, String fileName) throws IOException {
            if (fileName.contains("/") || fileName.contains("\\")) {
                return null;
            }
            for (Path root : roots) {
                Path candidate = pkg.isEmpty() ? root.resolve(fileName) : root.resolve(pkg).resolve(fileName);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            // Kotlin does not tie a file to its package directory: a file of that name, when it is the only one
            List<Path> named = index().getOrDefault(fileName, List.of());
            return named.size() == 1 ? named.get(0) : null;
        }

        private Map<String, List<Path>> index() throws IOException {
            if (byName == null) {
                byName = new HashMap<>();
                for (Path root : roots) {
                    if (!Files.isDirectory(root)) {
                        continue;
                    }
                    try (Stream<Path> s = Files.walk(root)) {
                        s.filter(Files::isRegularFile)
                                .forEach(p -> byName.computeIfAbsent(p.getFileName().toString(), k -> new ArrayList<>()).add(p));
                    }
                }
            }
            return byName;
        }
    }

    static String relative(Path projectDir, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        try {
            return projectDir.relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException otherRoot) {      // another drive (Windows): only the absolute path names it
            return absolute.toString().replace('\\', '/');
        }
    }

    // ------------------------------------------------------------------------------ class files

    static Map<String, ClassFile> readClasses(List<Path> dirs, PrintStream err) throws IOException {
        Map<String, ClassFile> classes = new LinkedHashMap<>();
        List<Path> unreadable = new ArrayList<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;       // a directory not compiled yet; a jar holds no class Gradle scans for tests
            }
            List<Path> files;
            try (Stream<Path> s = Files.walk(dir)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".class") && Files.isRegularFile(p))
                        .filter(p -> !dir.relativize(p).startsWith("META-INF")).sorted().toList();
            } catch (UncheckedIOException e) {
                throw new IOException("cannot list " + dir + ": " + e.getCause(), e.getCause());
            }
            for (Path file : files) {
                ClassFile c = parse(file);
                if (c == null) {
                    unreadable.add(file);
                } else {
                    classes.putIfAbsent(c.name(), c);       // the first on the classpath is the one that runs
                }
            }
        }
        if (!unreadable.isEmpty()) {
            err.println("TestCensus: not a class file the census can read, not judged: " + unreadable);
        }
        return classes;
    }

    /** The parts of a class file (JVMS 4) the census needs; null when it is not one. */
    static ClassFile parse(Path file) throws IOException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IOException("cannot read " + file + ": " + e, e);
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) {
                return null;
            }
            in.skipBytes(4);                                                // minor and major version
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classIndex = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 7 -> classIndex[i] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.skipBytes(2);
                    case 15 -> in.skipBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++;                                                // a long or a double takes two entries
                    }
                    default -> {
                        return null;
                    }
                }
            }
            int access = in.readUnsignedShort();
            String name = className(utf8, classIndex, in.readUnsignedShort());
            String superName = className(utf8, classIndex, in.readUnsignedShort());
            List<String> interfaces = new ArrayList<>();
            for (int n = in.readUnsignedShort(); n > 0; n--) {
                interfaces.add(className(utf8, classIndex, in.readUnsignedShort()));
            }
            for (int n = in.readUnsignedShort(); n > 0; n--) {          // fields
                in.skipBytes(6);
                skipAttributes(in);
            }
            List<Method> methods = new ArrayList<>();
            for (int n = in.readUnsignedShort(); n > 0; n--) {
                int methodAccess = in.readUnsignedShort();
                String methodName = utf8[in.readUnsignedShort()];
                String descriptor = utf8[in.readUnsignedShort()];
                Set<String> annotations = new HashSet<>();
                for (int a = in.readUnsignedShort(); a > 0; a--) {
                    String attribute = utf8[in.readUnsignedShort()];
                    byte[] body = in.readNBytes(in.readInt());
                    if ("RuntimeVisibleAnnotations".equals(attribute)) {
                        annotations.addAll(annotationTypes(body, utf8));
                    }
                }
                methods.add(new Method(methodName, descriptor, methodAccess, annotations));
            }
            Set<String> annotations = new HashSet<>();
            String sourceFile = null;
            Nesting nesting = Nesting.TOP;
            String outer = null;
            int innerAccess = 0;
            for (int a = in.readUnsignedShort(); a > 0; a--) {
                String attribute = utf8[in.readUnsignedShort()];
                byte[] body = in.readNBytes(in.readInt());
                DataInputStream data = new DataInputStream(new ByteArrayInputStream(body));
                switch (attribute == null ? "" : attribute) {
                    case "RuntimeVisibleAnnotations" -> annotations.addAll(annotationTypes(body, utf8));
                    case "SourceFile" -> sourceFile = utf8[data.readUnsignedShort()];
                    case "EnclosingMethod" -> nesting = nesting == Nesting.ANONYMOUS ? nesting : Nesting.LOCAL;
                    case "InnerClasses" -> {
                        for (int n = data.readUnsignedShort(); n > 0; n--) {
                            String inner = className(utf8, classIndex, data.readUnsignedShort());
                            int outerIndex = data.readUnsignedShort();
                            int innerName = data.readUnsignedShort();
                            int flags = data.readUnsignedShort();
                            if (!name.equals(inner)) {
                                continue;
                            }
                            if (innerName == 0) {
                                nesting = Nesting.ANONYMOUS;
                            } else if (outerIndex == 0) {
                                nesting = nesting == Nesting.ANONYMOUS ? nesting : Nesting.LOCAL;
                            } else if (nesting == Nesting.TOP) {
                                nesting = Nesting.MEMBER;
                                outer = className(utf8, classIndex, outerIndex);
                                innerAccess = flags;
                            }
                        }
                    }
                    default -> {
                        // not needed
                    }
                }
            }
            if (name == null) {
                return null;
            }
            return new ClassFile(name, superName, interfaces, access, annotations, methods, sourceFile, nesting, outer,
                    innerAccess, file);
        } catch (IOException | RuntimeException e) {
            return null;                                                    // cut off, or not a class file at all
        }
    }

    static String className(String[] utf8, int[] classIndex, int index) {
        return index == 0 || index >= classIndex.length || classIndex[index] == 0 ? null : utf8[classIndex[index]];
    }

    static void skipAttributes(DataInputStream in) throws IOException {
        for (int n = in.readUnsignedShort(); n > 0; n--) {
            in.skipBytes(2);
            int length = in.readInt();
            if (in.skipBytes(length) != length) {
                throw new IOException("attribute cut off");
            }
        }
    }

    /** The types of the annotations in a RuntimeVisibleAnnotations attribute, as internal names (JVMS 4.7.16). */
    static List<String> annotationTypes(byte[] body, String[] utf8) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        List<String> types = new ArrayList<>();
        for (int n = in.readUnsignedShort(); n > 0; n--) {
            String descriptor = utf8[in.readUnsignedShort()];
            if (descriptor != null && descriptor.startsWith("L") && descriptor.endsWith(";")) {
                types.add(descriptor.substring(1, descriptor.length() - 1));
            }
            skipPairs(in);
        }
        return types;
    }

    static void skipPairs(DataInputStream in) throws IOException {
        for (int pairs = in.readUnsignedShort(); pairs > 0; pairs--) {
            in.skipBytes(2);
            skipElementValue(in);
        }
    }

    static void skipElementValue(DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's', 'c' -> in.skipBytes(2);
            case 'e' -> in.skipBytes(4);
            case '@' -> {
                in.skipBytes(2);
                skipPairs(in);
            }
            case '[' -> {
                for (int n = in.readUnsignedShort(); n > 0; n--) {
                    skipElementValue(in);
                }
            }
            default -> throw new IOException("unknown element value tag " + tag);
        }
    }

    // ------------------------------------------------------------------------------ input lists and the allowlist

    /** The paths of a list file, each ended by a NUL character, exactly as written. */
    static List<Path> paths(String file) {
        List<Path> list = new ArrayList<>();
        try {
            for (String path : Files.readString(Path.of(file), StandardCharsets.UTF_8).split("\0")) {
                if (!path.isEmpty()) {
                    list.add(Path.of(path));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the list " + file, e);
        }
        return list;
    }

    /** The integrity allowlist, read as IntegrityCheck reads it: a path glob and a rule id; '#' starts a comment. */
    static List<Allow> readAllowlist(Path file) throws IOException {
        List<Allow> list = new ArrayList<>();
        if (file == null || !Files.isRegularFile(file)) {
            return list;
        }
        for (String raw : new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList()) {
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

    static boolean allowed(Finding f, List<Allow> allows) {
        for (Allow a : allows) {
            if (a.ruleId().equals(RULE) && a.matcher().matches(Path.of(f.path()))) {
                return true;
            }
        }
        return false;
    }
}
