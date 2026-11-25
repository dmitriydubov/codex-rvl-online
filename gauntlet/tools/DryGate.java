import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * DRY gate: reads a PMD CPD XML report and fails on duplicated code.
 *
 * <p>With {@code --only-file}, only duplications that touch at least one of the listed (changed)
 * files count, so an existing codebase can adopt the gate without fixing history first. The file lists their
 * paths relative to the project directory, each ended by a NUL character as {@code git diff --name-only -z}
 * writes them (a comma or a line break stays part of its name); an empty file = none; a listed path that names
 * no file is an error (exit 2) - the scope would otherwise match nothing and pass.
 *
 * <pre>
 * java DryGate.java --report build/reports/cpd/cpd.xml --project-dir . [--only-file changed-sources] [--max 0]
 * </pre>
 * Exit codes: 0 = OK, 1 = too many duplications, 2 = usage or input error.
 */
public class DryGate {

    record Location(String path, int line, int endLine) {
        String display() {
            return path + ":" + line + "-" + endLine;
        }
    }

    record Duplication(int lines, int tokens, List<Location> locations) {
    }

    public static void main(String[] args) {
        System.exit(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)));
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String report = null;
        String projectDir = ".";
        Set<String> only = null;
        int max = 0;
        try {
            for (int i = 0; i < args.length; i += 2) {
                String value = args[i + 1];
                switch (args[i]) {
                    case "--report" -> report = value;
                    case "--project-dir" -> projectDir = value;
                    case "--only-file" -> only = pathList(value);
                    case "--max" -> max = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("unknown option " + args[i]);
                }
            }
            if (report == null) {
                throw new IllegalArgumentException("--report is required");
            }
        } catch (RuntimeException e) {
            err.println("DryGate: " + e.getMessage());
            err.println("usage: java DryGate.java --report <cpd.xml> [--project-dir dir] [--only-file list] [--max 0]");
            return 2;
        }
        if (only != null && only.isEmpty()) {
            out.println("DRY gate: no changed Java sources in scope - nothing to check.");
            return 0;
        }
        if (only != null) {
            Path root = Path.of(projectDir);
            List<String> missing = only.stream().filter(f -> !Files.exists(root.resolve(f))).sorted().toList();
            if (!missing.isEmpty()) {
                err.println("DryGate: the scope names no file under " + projectDir + ": " + missing);
                return 2;
            }
        }
        File xml = new File(report);
        if (!xml.isFile()) {
            err.println("DryGate: CPD report not found: " + report);
            return 2;
        }
        List<Duplication> all;
        try {
            all = parse(xml, Path.of(projectDir).toAbsolutePath().normalize());
        } catch (Exception e) {
            err.println("DryGate: cannot read " + report + ": " + e);
            return 2;
        }
        List<Duplication> inScope = new ArrayList<>();
        for (Duplication d : all) {
            if (touches(d, only)) {
                inScope.add(d);
            }
        }
        out.printf("DRY gate: %d duplication(s) found by CPD, %d in scope (%s), max allowed %d%n",
                all.size(), inScope.size(), only == null ? "all sources" : only.size() + " changed file(s)", max);
        for (Duplication d : inScope) {
            StringBuilder sb = new StringBuilder("DUPLICATE ").append(d.lines()).append(" lines (")
                    .append(d.tokens()).append(" tokens): ");
            for (int i = 0; i < d.locations().size(); i++) {
                sb.append(i == 0 ? "" : "  <->  ").append(d.locations().get(i).display());
            }
            out.println(sb);
        }
        if (inScope.size() <= max) {
            out.println("DRY gate: OK");
            return 0;
        }
        out.println("DRY gate: FAILED - extract the shared behaviour into one place (a method, a class, a");
        out.println("  test fixture). Do not just rename things to fool the detector.");
        return 1;
    }

    /**
     * JDK 24 and newer count every reference to a built-in entity ({@code &lt;}, {@code &quot;}) against
     * jdk.xml.maxGeneralEntitySizeLimit, 100,000 per document: a large report full of escaped names stopped the tool.
     * The report declares no entity of its own (a DOCTYPE is refused), so only the size limits are lifted.
     */
    static void liftSizeLimits(DocumentBuilderFactory f) {
        for (String limit : List.of("maxGeneralEntitySizeLimit", "totalEntitySizeLimit")) {
            try {
                f.setAttribute("http://www.oracle.com/xml/jaxp/properties/" + limit, "0");
            } catch (IllegalArgumentException olderParser) {   // a parser without the limit needs no lifting
            }
        }
    }

    static List<Duplication> parse(File xml, Path projectDir) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        // CPD writes its XML report without a DOCTYPE. The feature above skips only an external DTD: the declarations
        // of a DOCTYPE in the report still loaded an external parameter entity, which put a file's content into an
        // entity, and the gate printed it as a path (XXE). A report with a DOCTYPE is not read.
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        liftSizeLimits(f);
        Document doc = f.newDocumentBuilder().parse(xml);
        List<Duplication> list = new ArrayList<>();
        NodeList dups = doc.getElementsByTagName("duplication");
        for (int i = 0; i < dups.getLength(); i++) {
            Element d = (Element) dups.item(i);
            List<Location> locations = new ArrayList<>();
            for (Node n = d.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getTagName().equals("file")) {
                    locations.add(new Location(relative(e.getAttribute("path"), projectDir),
                            intAttr(e, "line"), intAttr(e, "endline")));
                }
            }
            list.add(new Duplication(intAttr(d, "lines"), intAttr(d, "tokens"), locations));
        }
        return list;
    }

    static boolean touches(Duplication d, Set<String> only) {
        if (only == null) {
            return true;
        }
        for (Location l : d.locations()) {
            for (String f : only) {
                String norm = f.replace('\\', '/');
                if (l.path().equals(norm) || l.path().endsWith("/" + norm) || norm.endsWith("/" + l.path())) {
                    return true;
                }
            }
        }
        return false;
    }

    static String relative(String path, Path projectDir) {
        Path p = Path.of(path);
        if (p.isAbsolute() && p.normalize().startsWith(projectDir)) {
            return projectDir.relativize(p.normalize()).toString().replace('\\', '/');
        }
        return path.replace('\\', '/');
    }

    static int intAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v.isEmpty() ? 0 : Integer.parseInt(v);
    }

    /** The paths of a list file, each ended by a NUL character ({@code git diff --name-only -z}), exactly as written. */
    static Set<String> pathList(String file) {
        Set<String> set = new HashSet<>();
        try {
            for (String path : Files.readString(Path.of(file), StandardCharsets.UTF_8).split("\0")) {
                if (!path.isEmpty()) {
                    set.add(path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the list " + file, e);
        }
        return set;
    }
}
