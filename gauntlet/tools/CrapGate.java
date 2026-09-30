import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
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
 *      [--only src/main/java/a/B.java,src/main/java/a/C.java]   (scope: only these files)
 *      [--allowlist gauntlet/config/crap-allowlist.txt]           (human-approved exceptions)
 *      [--exclude *Application,*MapperImpl]                        (class-name globs)
 *      [--top 15]
 * </pre>
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
            err.println("usage: java CrapGate.java --report <jacoco.xml> [--threshold 8] [--only f1,f2]"
                    + " [--allowlist file] [--exclude globs] [--top N]");
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
        } catch (Exception e) {
            err.println("CrapGate: cannot read " + a.report + ": " + e);
            return 2;
        }
        return report(scores, a, out);
    }

    static Document parse(File xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setNamespaceAware(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(xml);
    }

    static List<Score> score(Document doc, Args a) {
        List<Pattern> excludes = globs(a.excludes);
        Set<String> allow = a.allow;
        List<Score> result = new ArrayList<>();
        NodeList packages = doc.getElementsByTagName("package");
        for (int p = 0; p < packages.getLength(); p++) {
            Element pkg = (Element) packages.item(p);
            for (Element cls : children(pkg, "class")) {
                String internal = cls.getAttribute("name");
                String sourceFile = cls.getAttribute("sourcefilename");
                String pkgPath = pkg.getAttribute("name");
                String sourcePath = (pkgPath.isEmpty() ? "" : pkgPath + "/") + sourceFile;
                if (sourceFile.isEmpty() || !inScope(sourcePath, a.only)) {
                    continue;
                }
                String className = internal.replace('/', '.');
                if (matchesAny(outerSimpleName(className), excludes)) {
                    continue;
                }
                for (Element m : children(cls, "method")) {
                    String name = m.getAttribute("name");
                    int cc = counterTotal(m, "COMPLEXITY");
                    int[] instr = counter(m, "INSTRUCTION");
                    int total = instr[0] + instr[1];
                    double coverage = total == 0 ? 1.0 : (double) instr[1] / total;
                    double crap = crap(cc, coverage);
                    int line = m.hasAttribute("line") ? Integer.parseInt(m.getAttribute("line")) : 0;
                    String methodName = prettyMethod(name);
                    boolean allowed = allow.contains(className + "#" + methodName)
                            || allow.contains(className + "#" + name)
                            || allow.contains(className + "#*");
                    result.add(new Score(className, methodName, line, sourcePath, cc, coverage, crap, allowed));
                }
            }
        }
        result.sort(Comparator.comparingDouble(Score::crap).reversed()
                .thenComparing(Score::className).thenComparingInt(Score::line));
        return result;
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

    static boolean inScope(String sourcePath, Set<String> only) {
        if (only == null) {
            return true;
        }
        for (String f : only) {
            String norm = f.replace('\\', '/');
            if (norm.equals(sourcePath) || norm.endsWith("/" + sourcePath)) {
                return true;
            }
        }
        return false;
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
        Set<String> allow = new HashSet<>();
        List<String> excludes = new ArrayList<>();
        int top = 15;

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
                    case "--threshold" -> a.threshold = Double.parseDouble(value);
                    case "--only" -> a.only = splitToSet(value);
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
                    String entry = line.replaceAll("#.*$", "").trim();
                    if (!entry.isEmpty()) {
                        set.add(entry);
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("cannot read allowlist " + file + ": " + e.getMessage());
            }
            return set;
        }
    }
}
