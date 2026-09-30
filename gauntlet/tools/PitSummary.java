import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Agent-friendly summary of a PIT mutations.xml report.
 *
 * <p>Lists every SURVIVED and NO_COVERAGE mutant as {@code Class:line method - description}
 * so an agent can go straight to the weak test. It also counts mutation sites per source file:
 * above {@code --max-per-file} it prints a warning - Uncle Bob's swarm-forge uses the
 * mutation-site count as a hint that a module mixes jobs (split it if it has more than one job,
 * never just to chase the number). The pass/fail decision stays with PIT's own thresholds.
 *
 * <pre>
 * java PitSummary.java --report build/reports/pitest/mutations.xml [--max-per-file 100]
 * </pre>
 * Exit codes: 0 = summary printed (or no report), 2 = usage or unreadable report.
 */
public class PitSummary {

    record Mutant(String status, String sourceFile, String mutatedClass, String method,
                  int line, String mutator, String description) {
        String display() {
            String simpleMutator = mutator.substring(mutator.lastIndexOf('.') + 1);
            return mutatedClass + ":" + line + " " + method + "() - " + description + " [" + simpleMutator + "]";
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, utf8(FileDescriptor.out), utf8(FileDescriptor.err)));
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String report = null;
        int maxPerFile = 100;
        try {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "--report" -> report = args[i + 1];
                    case "--max-per-file" -> maxPerFile = Integer.parseInt(args[i + 1]);
                    default -> throw new IllegalArgumentException("unknown option " + args[i]);
                }
            }
            if (report == null) {
                throw new IllegalArgumentException("--report is required");
            }
        } catch (RuntimeException e) {
            err.println("PitSummary: " + e.getMessage());
            return 2;
        }
        File xml = new File(report);
        if (!xml.isFile()) {
            out.println("PIT summary: no report at " + report + " (PIT did not run or found nothing to mutate).");
            return 0;
        }
        List<Mutant> mutants;
        try {
            mutants = parse(xml);
        } catch (Exception e) {
            err.println("PitSummary: cannot read " + report + ": " + e);
            return 2;
        }
        print(mutants, maxPerFile, out);
        return 0;
    }

    static List<Mutant> parse(File xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document doc = f.newDocumentBuilder().parse(xml);
        List<Mutant> list = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("mutation");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element m = (Element) nodes.item(i);
            String line = text(m, "lineNumber");
            list.add(new Mutant(m.getAttribute("status"), text(m, "sourceFile"), text(m, "mutatedClass"),
                    text(m, "mutatedMethod"), line.isEmpty() ? 0 : Integer.parseInt(line),
                    text(m, "mutator"), text(m, "description")));
        }
        return list;
    }

    static void print(List<Mutant> mutants, int maxPerFile, PrintStream out) {
        Map<String, Integer> byStatus = new TreeMap<>();
        Map<String, Integer> perFile = new LinkedHashMap<>();
        for (Mutant m : mutants) {
            byStatus.merge(m.status(), 1, Integer::sum);
            String pkg = m.mutatedClass().contains(".")
                    ? m.mutatedClass().substring(0, m.mutatedClass().lastIndexOf('.')).replace('.', '/') + "/" : "";
            perFile.merge(pkg + m.sourceFile(), 1, Integer::sum);
        }
        out.println("PIT summary: " + mutants.size() + " mutant(s) " + byStatus);
        for (String status : List.of("SURVIVED", "NO_COVERAGE")) {
            for (Mutant m : mutants) {
                if (m.status().equals(status)) {
                    out.println((status.equals("SURVIVED") ? "SURVIVED    " : "NO_COVERAGE ") + m.display());
                }
            }
        }
        for (Map.Entry<String, Integer> e : perFile.entrySet()) {
            if (e.getValue() > maxPerFile) {
                out.println("WARNING " + e.getKey() + " has " + e.getValue() + " mutation sites (> " + maxPerFile
                        + "). If it has more than one job, split it; do not split a one-job module just to chase the count.");
            }
        }
        if (byStatus.getOrDefault("SURVIVED", 0) > 0) {
            out.println("  A surviving mutant is a code change no test noticed: add or sharpen an assertion that");
            out.println("  fails for it. If you believe it is equivalent (cannot be detected), stop and tell the human.");
        }
        if (byStatus.getOrDefault("NO_COVERAGE", 0) > 0) {
            out.println("  NO_COVERAGE lines are not executed by any unit test - cover them or delete dead code.");
        }
    }

    static String text(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }
}
