import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Agent-friendly summary of a PIT mutations.xml report, and the part of the mutation gate PIT cannot do.
 *
 * <p>Lists every SURVIVED and NO_COVERAGE mutant as {@code Class:line method - description}
 * so an agent can go straight to the weak test. It also counts mutation sites per source file:
 * above {@code --max-per-file} it prints a warning - Uncle Bob's swarm-forge uses the
 * mutation-site count as a hint that a module mixes jobs (split it if it has more than one job,
 * never just to chase the number).
 *
 * <p>RUN_ERROR, MEMORY_ERROR and NON_VIABLE mutants are printed as UNVERIFIED: PIT 1.22.1 counts them
 * as detected ({@code DetectionStatus.RUN_ERROR(true)}), so test strength stays 100 % even though the
 * tests never ran against the mutant (the test JVM crashed on {@code redefineClasses}, typically on a
 * record). A RUN_ERROR mutant that is not listed in {@code --allowlist} fails this tool (exit 1), and
 * with it the mutation gate: a human verifies by reading the tests that the mutant would be killed and
 * adds {@code Class#method Mutator # which test} to the allowlist. MEMORY_ERROR and NON_VIABLE are
 * reported only: the first is PIT's own verdict on a runaway mutant, the second is bytecode the JVM
 * rejected, not a code change.
 *
 * <pre>
 * java PitSummary.java --report build/reports/pitest/mutations.xml [--max-per-file 100]
 *                      [--allowlist gauntlet/config/pit-runerror-allowlist.txt]
 * </pre>
 * Exit codes: 0 = summary printed (or no report), 1 = unverified RUN_ERROR mutants outside the allowlist,
 * 2 = usage or unreadable report / allowlist.
 */
public class PitSummary {

    static final String RUN_ERROR = "RUN_ERROR";
    static final List<String> UNVERIFIED = List.of(RUN_ERROR, "MEMORY_ERROR", "NON_VIABLE");

    record Mutant(String status, String sourceFile, String mutatedClass, String method,
                  int line, String mutator, String description) {
        String simpleMutator() {
            return mutator.substring(mutator.lastIndexOf('.') + 1);
        }

        String display() {
            return mutatedClass + ":" + line + " " + method + "() - " + description + " [" + simpleMutator() + "]";
        }
    }

    /** One allowlist line: exact class, glob method, glob mutator ({@code *} = any). */
    record Allowed(String mutatedClass, Pattern method, Pattern mutator) {
        boolean covers(Mutant m) {
            return mutatedClass.equals(m.mutatedClass()) && method.matcher(m.method()).matches()
                    && mutator.matcher(m.simpleMutator()).matches();
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
        String allowlist = null;
        int maxPerFile = 100;
        try {
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("option " + args[i] + " needs a value");
                }
                switch (args[i]) {
                    case "--report" -> report = args[i + 1];
                    case "--max-per-file" -> maxPerFile = Integer.parseInt(args[i + 1]);
                    case "--allowlist" -> allowlist = args[i + 1];
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
        List<Allowed> allowed;
        try {
            mutants = parse(xml);
            allowed = allowlist == null ? List.of() : readAllowlist(new File(allowlist));
        } catch (Exception e) {
            err.println("PitSummary: cannot read " + report + " or " + allowlist + ": " + e);
            return 2;
        }
        print(mutants, maxPerFile, out);
        return gate(mutants, allowed, allowlist, out) ? 0 : 1;
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

    /** {@code fully.qualified.Class#method  Mutator   # reason}; the mutator may be omitted ({@code *}). */
    static List<Allowed> readAllowlist(File file) throws IOException {
        List<Allowed> list = new ArrayList<>();
        if (!file.isFile()) {
            return list;
        }
        for (String raw : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String line = raw;
            int comment = commentStart(line);
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                continue;
            }
            int hash = tokens[0].indexOf('#');
            if (hash <= 0) {
                throw new IOException("allowlist line without Class#method: " + raw);
            }
            String mutator = tokens.length > 1 ? tokens[1] : "*";
            list.add(new Allowed(tokens[0].substring(0, hash), glob(tokens[0].substring(hash + 1)), glob(mutator)));
        }
        return list;
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

    static Pattern glob(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
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
        for (String status : UNVERIFIED) {
            for (Mutant m : mutants) {
                if (m.status().equals(status)) {
                    out.println(status + " ".repeat(Math.max(1, 12 - status.length())) + m.display()
                            + " - UNVERIFIED: tests did not run");
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

    /** The gate: every RUN_ERROR mutant is either human-verified (allowlist) or a red gate. */
    static boolean gate(List<Mutant> mutants, List<Allowed> allowed, String allowlist, PrintStream out) {
        List<Mutant> unverified = new ArrayList<>();
        for (Mutant m : mutants) {
            if (m.status().equals(RUN_ERROR) && allowed.stream().noneMatch(a -> a.covers(m))) {
                unverified.add(m);
            }
        }
        if (unverified.isEmpty()) {
            return true;
        }
        out.println("mutationGate: " + unverified.size() + " RUN_ERROR mutant(s) not verified by any test and not in "
                + (allowlist == null ? "an allowlist" : allowlist) + ":");
        for (Mutant m : unverified) {
            out.println("  " + m.mutatedClass() + "#" + m.method() + " " + m.simpleMutator());
        }
        out.println("  PIT counts RUN_ERROR as detected; this gate does not. The test JVM crashed before the tests ran");
        out.println("  (usually redefineClasses on a record). Read the tests and name the assertion that would fail for");
        out.println("  the mutant, then stop and ask the human to add that line (with the test as the reason) to the");
        out.println("  allowlist - or fix the crash. Agents cannot edit the allowlist.");
        return false;
    }

    static String text(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }
}
