import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * CRAP gate for Java, fed by a JaCoCo XML report.
 *
 * <p>CRAP = CC^2 * (1 - coverage)^3 + CC, the formula used by unclebob/crap4java.
 * CC is the JaCoCo COMPLEXITY counter of the method, coverage is its INSTRUCTION coverage.
 * A fully covered method scores exactly its cyclomatic complexity; an uncovered complex
 * method explodes. So the gate forces either simple code or tested code - ideally both.
 *
 * <pre>
 * java CrapGate.java --report build/reports/jacoco/test/jacocoTestReport.xml --threshold 8
 *      [--only-file changed-sources]                             (scope: only the files it lists, see below)
 *      [--sources src/main/java --sources src/compat/java]       (main source roots, one each, with --only-file)
 *      [--encoding UTF-8]                                        (the charset javac reads the sources with)
 *      [--allowlist gauntlet/config/crap-allowlist.txt]           (human-approved exceptions)
 *      [--exclude *Application,*MapperImpl]                        (class-name globs)
 *      [--top 15]
 * </pre>
 * {@code --only-file} lists the changed source files, each path ended by a NUL character, as
 * {@code git diff --name-only -z} writes them: a comma or a line break stays part of its name. An empty file = none.
 * Exit codes: 0 = within threshold, 1 = threshold exceeded, 2 = usage or input error.
 */
public class CrapGate {

    record Score(String className, String method, int line, String sourcePath,
                 int complexity, double coverage, double crap, boolean allowed) {

        String display() {
            String file = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
            return className + "#" + method + " (" + file + ":" + line + ")";
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)));
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Args a;
        try {
            a = Args.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("CrapGate: " + e.getMessage());
            err.println("usage: java CrapGate.java --report <jacoco.xml> [--threshold 8] [--only-file list]"
                    + " [--sources dir]... [--encoding UTF-8] [--allowlist file] [--exclude globs] [--top N]");
            return 2;
        }
        if (a.only != null && a.only.isEmpty()) {
            out.println("CRAP gate: no changed Java sources in scope - nothing to check.");
            return 0;
        }
        File report = new File(a.report);
        if (!report.isFile()) {
            err.println("CrapGate: JaCoCo XML report not found: " + a.report
                    + " (run the tests with JaCoCo first, e.g. ./gradlew jacocoTestReport)");
            return 2;
        }
        List<Score> scores;
        try {
            scores = score(parse(report), a);
        } catch (SourceException e) {
            err.println("CrapGate: " + e.getMessage());   // a changed source, not the report, is the unreadable input
            return 2;
        } catch (Exception e) {
            err.println("CrapGate: cannot read " + a.report + ": " + e);
            return 2;
        }
        return report(scores, a, out);
    }

    /**
     * JDK 24 and newer count every reference to a built-in entity ({@code &lt;}, {@code &quot;}) against
     * jdk.xml.maxGeneralEntitySizeLimit, 100,000 per document: a large report full of escaped names stopped the tool.
     * No external entity is loaded; an internal one of a planted report can at most exhaust memory - red, never green.
     */
    static void liftSizeLimits(DocumentBuilderFactory f) {
        for (String limit : List.of("maxGeneralEntitySizeLimit", "totalEntitySizeLimit")) {
            try {
                f.setAttribute("http://www.oracle.com/xml/jaxp/properties/" + limit, "0");
            } catch (IllegalArgumentException olderParser) {   // a parser without the limit needs no lifting
            }
        }
    }

    static Document parse(File xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setNamespaceAware(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        // JaCoCo's report has a DOCTYPE (an external DTD, skipped above), so a DOCTYPE cannot be refused. The two
        // features above left an external parameter entity declared inside it: it put a file's content into an
        // entity, and the gate printed it as a method name (XXE).
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        liftSizeLimits(f);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(xml);
    }

    static List<Score> score(Document doc, Args a) throws IOException {
        List<Pattern> excludes = globs(a.excludes);
        Set<String> allow = a.allow;
        List<Source> changed = new ArrayList<>();
        for (String file : a.only == null ? Set.<String>of() : a.only) {
            changed.add(Source.read(file, a.encoding));
        }
        Map<String, Set<String>> declared = declaredTypes(changed);
        Namesakes elsewhere = new Namesakes(a);
        List<Score> result = new ArrayList<>();
        boolean missingSource = false;
        NodeList packages = doc.getElementsByTagName("package");
        for (int p = 0; p < packages.getLength(); p++) {
            Element pkg = (Element) packages.item(p);
            for (Element cls : children(pkg, "class")) {
                String internal = cls.getAttribute("name");
                String className = internal.replace('/', '.');
                String sourceFile = cls.getAttribute("sourcefilename");
                String pkgPath = pkg.getAttribute("name");
                String simple = className.substring(className.lastIndexOf('.') + 1);
                if (sourceFile.isEmpty()) {
                    missingSource = true;
                    String resolved = resolveSource(declared, pkgPath, simple);
                    if (a.only != null && resolved == null) {
                        continue;   // this report class is not declared by any changed source file
                    }
                    sourceFile = resolved != null ? resolved : outerSimpleName(className) + ".java";
                }
                String sourcePath = (pkgPath.isEmpty() ? "" : pkgPath + "/") + sourceFile;
                if (a.only != null) {
                    Set<String> names = declared.get(sourcePath);
                    if (names == null) {
                        continue;   // package + file name of no changed source
                    }
                    // With debug info package + file name normally settle it. Two source roots may hold a file of
                    // this name in this package, though: a class the changed file does not declare is left out
                    // only when an UNCHANGED namesake provably declares it - never on the parser's silence alone.
                    if (!declares(names, simple) && elsewhere.declares(pkgPath, sourceFile, simple)) {
                        continue;
                    }
                }
                if (matchesAny(outerSimpleName(className), excludes)) {
                    continue;
                }
                List<Element> methods = children(cls, "method");
                Map<String, Integer> nameCounts = new HashMap<>();
                Map<String, List<String>> nameDescs = new HashMap<>();
                Map<String, Integer> displayCounts = new HashMap<>();
                Map<String, List<String>> displaySigs = new HashMap<>();
                for (Element m : methods) {
                    String n = m.getAttribute("name");
                    String d = prettyMethod(n);
                    nameCounts.merge(n, 1, Integer::sum);
                    nameDescs.computeIfAbsent(n, k -> new ArrayList<>()).add(n + m.getAttribute("desc"));
                    displayCounts.merge(d, 1, Integer::sum);
                    displaySigs.computeIfAbsent(d, k -> new ArrayList<>()).add(n + m.getAttribute("desc"));
                }
                for (Element m : methods) {
                    String name = m.getAttribute("name");
                    String desc = m.getAttribute("desc");
                    int cc = counterTotal(m, "COMPLEXITY");
                    int[] instr = counter(m, "INSTRUCTION");
                    int total = instr[0] + instr[1];
                    double coverage = total == 0 ? 1.0 : (double) instr[1] / total;
                    double crap = crap(cc, coverage);
                    int line = m.hasAttribute("line") ? Integer.parseInt(m.getAttribute("line")) : 0;
                    String methodName = prettyMethod(name);
                    // A signature entry Class#method(desc) exempts exactly one overload. A name-only entry
                    // exempts only when the key it matched is unambiguous. Uniqueness is checked for BOTH the
                    // raw JVM name (overloads) and the display name: two lambdas lambda$rules$0/1 collapse to
                    // one display "rules::lambda", so a display-name entry must not exempt both silently.
                    boolean rawMatch = allow.contains(className + "#" + name);
                    boolean dispMatch = allow.contains(className + "#" + methodName);
                    boolean allowed = allow.contains(className + "#*")
                            || allow.contains(className + "#" + name + desc)
                            || (rawMatch && nameCounts.getOrDefault(name, 0) == 1)
                            || (dispMatch && displayCounts.getOrDefault(methodName, 0) == 1);
                    if (rawMatch && nameCounts.getOrDefault(name, 0) > 1) {
                        a.ambiguous.add("CRAP allowlist: '" + className + "#" + name + "' matches " + nameCounts.get(name)
                                + " overloads " + nameDescs.get(name) + " - it exempts nothing; specify a signature"
                                + " (e.g. " + className + "#" + nameDescs.get(name).get(0) + ").");
                    }
                    if (dispMatch && displayCounts.getOrDefault(methodName, 0) > 1) {
                        a.ambiguous.add("CRAP allowlist: '" + className + "#" + methodName + "' matches "
                                + displayCounts.get(methodName) + " methods sharing that display name "
                                + displaySigs.get(methodName) + " (e.g. sibling lambdas) - it exempts nothing;"
                                + " specify a signature (e.g. " + className + "#" + displaySigs.get(methodName).get(0) + ").");
                    }
                    result.add(new Score(className, methodName, line, sourcePath, cc, coverage, crap, allowed));
                }
            }
        }
        if (missingSource) {
            // Without source file names in the report only the derived type names bind a class to a changed source.
            for (Source source : changed) {
                if (source.names().isEmpty() && source.declares()) {
                    throw new SourceException("cannot tell which types the changed source " + source.file() + " declares:"
                            + " it holds no class, interface, enum or record declaration this gate can read, and the"
                            + " JaCoCo report carries no source file names (debug=false) to go by");
                }
            }
        }
        if (a.only != null && !a.only.isEmpty() && result.isEmpty() && missingSource) {
            a.ambiguous.add("CRAP gate: the JaCoCo report carries no source filenames (debug=false) and the changed-file"
                    + " scope analysed 0 methods. A zero-method pass is not proof the changed code is simple: rebuild the"
                    + " report with debug info, or verify acceptance.features/changed sources resolve to the right files.");
        }
        result.sort(Comparator.comparingDouble(Score::crap).reversed()
                .thenComparing(Score::className).thenComparingInt(Score::line));
        return result;
    }

    static final Pattern TYPE = Pattern.compile("(?<![\\p{javaJavaIdentifierPart}$])(?:class|interface|enum|record)\\s+"
            + "([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)");
    static final Pattern PACKAGE = Pattern.compile("(?<![\\p{javaJavaIdentifierPart}$])package\\s+"
            + "([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(?:\\s*\\.\\s*"
            + "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*)\\s*;");
    static final Pattern PACKAGE_OR_IMPORT =
            Pattern.compile("(?<![\\p{javaJavaIdentifierPart}$])(?:package|import)\\s[^;]*;");

    /**
     * What the scan reads in a source file. {@code key} is declared package + file name, as JaCoCo names a source;
     * {@code names} are the top-level types only (a nested Selection.Legacy must not bind an unrelated top-level
     * Legacy); {@code declares} tells a file with declarations from an empty one, a file of comments and imports,
     * package-info.java and module-info.java.
     */
    record Source(String file, String key, Set<String> names, boolean declares) {

        static Source read(String file, Charset encoding) throws IOException {
            String topLevel = topLevelSource(readSource(Path.of(file), encoding));
            Set<String> names = new HashSet<>();
            Matcher type = TYPE.matcher(topLevel);
            while (type.find()) {
                names.add(type.group(1));
            }
            Matcher pkg = PACKAGE.matcher(topLevel);
            String packagePath = pkg.find() ? pkg.group(1).replaceAll("\\s+", "").replace('.', '/') : "";
            String norm = file.replace('\\', '/');
            String fileName = norm.substring(norm.lastIndexOf('/') + 1);
            boolean declares = !fileName.equals("package-info.java") && !fileName.equals("module-info.java")
                    && !PACKAGE_OR_IMPORT.matcher(topLevel).replaceAll(" ").replaceAll("[\\s;]", "").isEmpty();
            return new Source(file, (packagePath.isEmpty() ? "" : packagePath + "/") + fileName, names, declares);
        }
    }

    static Map<String, Set<String>> declaredTypes(List<Source> sources) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (Source source : sources) {
            // merge, never overwrite: two changed files may share a name and a declared package
            // (domain/Rules.java and domain/variant/Rules.java, both `package ...domain`)
            map.computeIfAbsent(source.key(), k -> new HashSet<>()).addAll(source.names());
        }
        return map;
    }

    /** A changed source that cannot be read is an input error of its own - not "cannot read the report". */
    static final class SourceException extends IOException {
        private static final long serialVersionUID = 1L;

        SourceException(String message) {
            super(message);
        }
    }

    static String readSource(Path file, Charset encoding) throws IOException {
        try {
            return Files.readString(file, encoding);
        } catch (CharacterCodingException e) {
            throw new SourceException("cannot read the changed source " + file + " as " + encoding + " - pass the charset"
                    + " javac reads it with (--encoding; in Gradle: JavaCompile options.encoding)");
        } catch (IOException e) {
            throw new SourceException("cannot read the changed source " + file + ": " + e);
        }
    }

    /** A report class belongs to a source that declares its top-level type: {@code Name}, {@code Name$Inner}. */
    static boolean declares(Set<String> topLevelNames, String simpleClassName) {
        for (String name : topLevelNames) {
            if (simpleClassName.equals(name) || simpleClassName.startsWith(name + "$")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unchanged sources under the main source roots, by file name: tells whether a report class with the package
     * and file name of a changed source is in fact declared by an unchanged namesake in another root.
     */
    static final class Namesakes {
        private final Args args;
        private Map<String, List<Path>> byName;

        Namesakes(Args args) {
            this.args = args;
        }

        boolean declares(String pkgPath, String fileName, String simpleClassName) throws IOException {
            if (args.only == null || args.sources.isEmpty()) {
                return false;
            }
            if (byName == null) {
                byName = index();
            }
            String key = (pkgPath.isEmpty() ? "" : pkgPath + "/") + fileName;
            for (Path candidate : byName.getOrDefault(fileName, List.of())) {
                Source namesake;
                try {
                    namesake = Source.read(candidate.toString(), args.encoding);
                } catch (SourceException unreadable) {
                    continue;   // proves nothing: the class stays in scope
                }
                if (namesake.key().equals(key) && CrapGate.declares(namesake.names(), simpleClassName)) {
                    return true;
                }
            }
            return false;
        }

        private Map<String, List<Path>> index() throws IOException {
            Set<Path> changed = new HashSet<>();
            for (String f : args.only) {
                changed.add(Path.of(f).toAbsolutePath().normalize());
            }
            Map<String, List<Path>> map = new HashMap<>();
            for (String root : args.sources) {
                Path dir = Path.of(root);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        if (!changed.contains(p.toAbsolutePath().normalize())) {
                            map.computeIfAbsent(p.getFileName().toString(), k -> new ArrayList<>()).add(p);
                        }
                    }
                }
            }
            return map;
        }
    }

    /**
     * JLS 3.3: javac translates Unicode escapes before it reads anything else, so an escaped keyword, quote, comment
     * marker or line end is the real thing. A backslash that follows an odd number of backslashes starts no escape.
     */
    static String unicodeEscapes(String source) {
        if (!source.contains("\\u")) {
            return source;
        }
        StringBuilder text = new StringBuilder(source.length());
        int backslashes = 0;     // contiguous backslashes right before the current character
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\' && backslashes % 2 == 0) {
                int digits = i + 1;
                while (digits < source.length() && source.charAt(digits) == 'u') {
                    digits++;
                }
                if (digits > i + 1 && digits + 4 <= source.length()
                        && source.substring(digits, digits + 4).matches("[0-9a-fA-F]{4}")) {
                    text.append((char) Integer.parseInt(source.substring(digits, digits + 4), 16));
                    i = digits + 4;
                    backslashes = 0;
                    continue;
                }
            }
            backslashes = c == '\\' ? backslashes + 1 : 0;
            text.append(c);
            i++;
        }
        return text.toString();
    }

    /**
     * The compilation unit as javac reads it, with everything but its top-level code blanked: comments, literals and
     * the bodies of types. gauntlet.gradle has its own copy of this scan (javaTopLevelSource, for the targets of PIT
     * and the class backstop) - keep the two in step.
     */
    static String topLevelSource(String raw) {
        String source = unicodeEscapes(raw);
        StringBuilder code = new StringBuilder(source.length());
        String state = "code";
        int depth = 0;
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (state.equals("lineComment")) {
                appendMasked(code, c);
                if (c == '\n' || c == '\r') {       // JLS 3.4: CR, LF and CRLF end a line
                    state = "code";
                }
                i++;
                continue;
            }
            if (state.equals("blockComment")) {
                if (c == '*' && next == '/') {
                    code.append("  ");
                    i += 2;
                    state = "code";
                } else {
                    appendMasked(code, c);
                    i++;
                }
                continue;
            }
            if (state.equals("textBlock")) {
                if (c == '\\' && i + 1 < source.length()) {
                    appendMasked(code, c);
                    appendMasked(code, next);
                    i += 2;
                } else if (c == '"' && next == '"' && i + 2 < source.length() && source.charAt(i + 2) == '"') {
                    code.append("   ");
                    i += 3;
                    state = "code";
                } else {
                    appendMasked(code, c);
                    i++;
                }
                continue;
            }
            if (state.equals("string") || state.equals("character")) {
                char delimiter = state.equals("string") ? '"' : '\'';
                if (c == '\\' && i + 1 < source.length()) {
                    appendMasked(code, c);
                    appendMasked(code, next);
                    i += 2;
                } else {
                    appendMasked(code, c);
                    if (c == delimiter) {
                        state = "code";
                    }
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                code.append("  ");
                i += 2;
                state = "lineComment";
            } else if (c == '/' && next == '*') {
                code.append("  ");
                i += 2;
                state = "blockComment";
            } else if (c == '"' && next == '"' && i + 2 < source.length() && source.charAt(i + 2) == '"') {
                code.append("   ");
                i += 3;
                state = "textBlock";
            } else if (c == '"') {
                code.append(' ');
                i++;
                state = "string";
            } else if (c == '\'') {
                code.append(' ');
                i++;
                state = "character";
            } else if (Character.isIdentifierIgnorable(c)) {
                i++;    // javac drops these from keywords and names (a zero-width joiner, a NUL)
            } else {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth = Math.max(0, depth - 1);
                }
                code.append(depth == 0 ? c : (c == '\n' ? '\n' : ' '));
                i++;
            }
        }
        return code.toString();
    }

    static void appendMasked(StringBuilder target, char c) {
        target.append(c == '\n' ? '\n' : ' ');
    }

    static String resolveSource(Map<String, Set<String>> declared, String pkgPath, String simpleClassName) {
        String found = null;
        for (Map.Entry<String, Set<String>> e : declared.entrySet()) {
            String rel = e.getKey();
            int slash = rel.lastIndexOf('/');
            String parent = slash < 0 ? "" : rel.substring(0, slash);
            if (!parent.equals(pkgPath) || !declares(e.getValue(), simpleClassName)) {
                continue;
            }
            String base = rel.substring(slash + 1);
            if (found != null && !found.equals(base)) {
                return null;
            }
            found = base;
        }
        return found;
    }

    static double crap(int complexity, double coverage) {
        double uncovered = 1.0 - coverage;
        return complexity * complexity * uncovered * uncovered * uncovered + complexity;
    }

    static int report(List<Score> scores, Args a, PrintStream out) {
        List<Score> failing = new ArrayList<>();
        for (Score s : scores) {
            if (s.crap() > a.threshold && !s.allowed()) {
                failing.add(s);
            }
        }
        String scope = a.only == null ? "all sources" : a.only.size() + " changed file(s)";
        out.printf(Locale.ROOT, "CRAP gate: threshold %.1f, scope: %s, %d method(s) analysed%n",
                a.threshold, scope, scores.size());
        int shown = 0;
        if (!scores.isEmpty()) {
            out.printf(Locale.ROOT, "%-10s %6s %4s %7s  %s%n", "", "CRAP", "CC", "COVER", "METHOD");
        }
        for (Score s : scores) {
            boolean fail = s.crap() > a.threshold && !s.allowed();
            if (shown >= a.top && !fail) {
                continue;
            }
            String mark = fail ? "CRAP FAIL" : (s.allowed() && s.crap() > a.threshold ? "CRAP ALLOW" : "");
            out.printf(Locale.ROOT, "%-10s %6.1f %4d %6.1f%%  %s%n", mark, s.crap(), s.complexity(),
                    s.coverage() * 100.0, s.display());
            shown++;
        }
        for (String note : a.ambiguous) {
            out.println(note);
        }
        if (failing.isEmpty()) {
            out.println("CRAP gate: OK");
            return 0;
        }
        out.printf(Locale.ROOT, "CRAP gate: FAILED - %d method(s) above %.1f.%n", failing.size(), a.threshold);
        out.println("  CRAP = CC^2 * (1 - coverage)^3 + CC. Split methods that mix duties (an extracted");
        out.println("  method must own its inputs - no boolean flags the caller already knew) and cover");
        out.println("  the remaining branches with focused unit tests. Exceptions are human-owned:");
        out.println("  gauntlet/config/crap-allowlist.txt");
        return 1;
    }

    static String outerSimpleName(String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        int dollar = simple.indexOf('$');
        return dollar < 0 ? simple : simple.substring(0, dollar);
    }

    static String prettyMethod(String jvmName) {
        if (jvmName.startsWith("lambda$")) {
            String[] parts = jvmName.split("\\$");
            return (parts.length > 1 ? parts[1] : "lambda") + "::lambda";
        }
        return jvmName;
    }

    static List<Element> children(Element parent, String tag) {
        List<Element> list = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                list.add(e);
            }
        }
        return list;
    }

    static int[] counter(Element method, String type) {
        for (Element c : children(method, "counter")) {
            if (c.getAttribute("type").equals(type)) {
                return new int[] {Integer.parseInt(c.getAttribute("missed")), Integer.parseInt(c.getAttribute("covered"))};
            }
        }
        return new int[] {0, 0};
    }

    static int counterTotal(Element method, String type) {
        int[] c = counter(method, type);
        return Math.max(1, c[0] + c[1]);
    }

    static List<Pattern> globs(List<String> globs) {
        List<Pattern> list = new ArrayList<>();
        for (String g : globs) {
            String regex = Arrays.stream(g.trim().split("\\*", -1)).map(Pattern::quote)
                    .reduce((x, y) -> x + ".*" + y).orElse("");
            list.add(Pattern.compile(regex));
        }
        return list;
    }

    static boolean matchesAny(String name, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    static final class Args {
        String report;
        double threshold = 8.0;
        Set<String> only;
        List<String> sources = new ArrayList<>();
        Charset encoding = StandardCharsets.UTF_8;
        Set<String> allow = new HashSet<>();
        List<String> excludes = new ArrayList<>();
        int top = 15;
        Set<String> ambiguous = new LinkedHashSet<>();

        static Args parse(String[] args) {
            Args a = new Args();
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("missing value for " + key);
                }
                String value = args[++i];
                switch (key) {
                    case "--report" -> a.report = value;
                    case "--threshold" -> a.threshold = threshold(value);
                    case "--only-file" -> a.only = new HashSet<>(pathList(value));
                    case "--sources" -> a.sources.add(value);      // one root each: a path may hold a comma
                    case "--encoding" -> a.encoding = Charset.forName(value);
                    case "--allowlist" -> a.allow = readAllowlist(value);
                    case "--exclude" -> a.excludes = new ArrayList<>(splitToSet(value));
                    case "--top" -> a.top = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("unknown option " + key);
                }
            }
            if (a.report == null) {
                throw new IllegalArgumentException("--report is required");
            }
            return a;
        }

        /**
         * A finite number {@code >= 0}, as a CRAP score is. Double.parseDouble takes NaN and Infinity as well (1e999 is
         * Infinity), and no score is above NaN: the gate passed every method.
         */
        static double threshold(String value) {
            double threshold = Double.parseDouble(value);
            if (!Double.isFinite(threshold) || threshold < 0) {
                throw new IllegalArgumentException("--threshold must be a finite number >= 0, not " + value);
            }
            return threshold;
        }

        /** The paths of a list file, each ended by a NUL character ({@code git diff --name-only -z}), exactly as written. */
        static List<String> pathList(String file) {
            List<String> list = new ArrayList<>();
            try {
                for (String path : Files.readString(Path.of(file), StandardCharsets.UTF_8).split("\0")) {
                    if (!path.isEmpty()) {
                        list.add(path);
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot read the list " + file + ": " + e);
            }
            return list;
        }

        static Set<String> splitToSet(String csv) {
            Set<String> set = new HashSet<>();
            for (String s : csv.split(",")) {
                if (!s.isBlank()) {
                    set.add(s.trim());
                }
            }
            return set;
        }

        static Set<String> readAllowlist(String file) {
            Set<String> set = new HashSet<>();
            Path p = Path.of(file);
            if (!Files.isRegularFile(p)) {
                return set;
            }
            try {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    int comment = commentStart(line);
                    String entry = (comment < 0 ? line : line.substring(0, comment)).trim();
                    if (!entry.isEmpty()) {
                        set.add(entry);
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("cannot read allowlist " + file + ": " + e.getMessage());
            }
            return set;
        }

        /** A {@code #} starts a comment only after whitespace or at line start: the one inside {@code Class#method} does not. */
        static int commentStart(String line) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                    return i;
                }
            }
            return -1;
        }
    }
}
