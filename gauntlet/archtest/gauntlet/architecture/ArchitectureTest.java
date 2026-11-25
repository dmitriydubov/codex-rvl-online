package gauntlet.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.fail;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.metrics.ArchitectureMetrics;
import com.tngtech.archunit.library.metrics.ComponentDependencyMetrics;
import com.tngtech.archunit.library.metrics.MetricsComponents;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Architecture gate. The policy lives in gauntlet/config/architecture.properties (human-owned);
 * this test only enforces it. Run with ./gradlew archTest.
 */
class ArchitectureTest {

    private static final Properties POLICY = load();
    private static final String BASE = POLICY.getProperty("base.package", "").trim();
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void basePackageContainsProductionClasses() {
        if (CLASSES.isEmpty()) {
            fail("No production classes found under base.package=" + BASE
                    + " - check gauntlet/config/architecture.properties.");
        }
    }

    @Test
    void everyComponentIsDeclaredInThePolicy() {
        List<String> undeclared = new ArrayList<>();
        for (String component : componentsInCode()) {
            if (!POLICY.containsKey("allowed." + component)) {
                undeclared.add(component);
            }
        }
        if (!undeclared.isEmpty()) {
            fail("Undeclared component(s) under " + BASE + ": " + undeclared + ". The human must add "
                    + "allowed.<component>=... to gauntlet/config/architecture.properties (or move the code).");
        }
    }

    @Test
    void componentsDependOnlyOnAllowedComponents() {
        Set<String> all = allComponents();
        List<String> violations = new ArrayList<>();
        for (String component : all) {
            Set<String> allowed = csv(POLICY.getProperty("allowed." + component, ""));
            List<String> forbidden = new ArrayList<>();
            for (String other : all) {
                if (!other.equals(component) && !allowed.contains(other)) {
                    forbidden.add(BASE + "." + other + "..");
                }
            }
            if (!forbidden.isEmpty()) {
                check(noClasses().that().resideInAPackage(BASE + "." + component + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(new String[0]))
                        .because("architecture.properties allows " + component + " -> " + allowed)
                        .allowEmptyShould(true), violations);
            }
        }
        failIfAny(violations);
    }

    @Test
    void componentsStayAwayFromForbiddenPackages() {
        List<String> violations = new ArrayList<>();
        for (String key : POLICY.stringPropertyNames()) {
            if (!key.startsWith("forbidden.")) {
                continue;
            }
            String component = key.substring("forbidden.".length());
            Set<String> packages = csv(POLICY.getProperty(key));
            if (!packages.isEmpty()) {
                check(noClasses().that().resideInAPackage(BASE + "." + component + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(packages.toArray(new String[0]))
                        .because("architecture.properties forbids " + component + " -> " + packages)
                        .allowEmptyShould(true), violations);
            }
        }
        failIfAny(violations);
    }

    @Test
    void noCyclesBetweenComponents() {
        if (Boolean.parseBoolean(POLICY.getProperty("fail.on.cycles", "true"))) {
            slices().matching(BASE + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true).check(CLASSES);
        }
    }

    // Every package is its own slice ((**) captures the full sub-package path, dots included), so a cycle
    // between sub-packages of one component (service.onec <-> service.retention) fails too. Kept next to the
    // component rule: a component cycle can run through four distinct packages without a package cycle.
    @Test
    void noCyclesBetweenPackages() {
        if (Boolean.parseBoolean(POLICY.getProperty("fail.on.cycles", "true"))) {
            slices().matching(BASE + ".(**)").should().beFreeOfCycles().allowEmptyShould(true).check(CLASSES);
        }
    }

    @Test
    void componentMetrics() {
        JavaPackage base = CLASSES.getPackage(BASE);
        ComponentDependencyMetrics metrics =
                ArchitectureMetrics.componentDependencyMetrics(MetricsComponents.fromPackages(base.getSubpackages()));
        String maxDistance = POLICY.getProperty("max.distance", "").trim();
        List<String> tooFar = new ArrayList<>();
        System.out.println("ARCH component          Ca   Ce      I      A      D");
        List<JavaPackage> components = base.getSubpackages().stream()
                .sorted(Comparator.comparing(JavaPackage::getName)).collect(Collectors.toList());
        for (JavaPackage p : components) {
            String id = p.getName();
            double d = metrics.getNormalizedDistanceFromMainSequence(id);
            System.out.printf(Locale.ROOT, "ARCH %-18s %4d %4d %6.2f %6.2f %6.2f%n", p.getRelativeName(),
                    metrics.getAfferentCoupling(id), metrics.getEfferentCoupling(id), metrics.getInstability(id),
                    metrics.getAbstractness(id), d);
            if (!maxDistance.isEmpty() && d > Double.parseDouble(maxDistance)) {
                tooFar.add(p.getRelativeName() + " D=" + String.format(Locale.ROOT, "%.2f", d));
            }
        }
        if (!tooFar.isEmpty()) {
            fail("Components too far from the main sequence (max " + maxDistance + "): " + tooFar
                    + ". Stable components should be abstract, unstable ones concrete.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Set<String> componentsInCode() {
        Set<String> names = new TreeSet<>();
        for (JavaPackage p : CLASSES.getPackage(BASE).getSubpackages()) {
            names.add(p.getRelativeName());
        }
        return names;
    }

    private static Set<String> allComponents() {
        Set<String> names = componentsInCode();
        for (String key : POLICY.stringPropertyNames()) {
            if (key.startsWith("allowed.")) {
                names.add(key.substring("allowed.".length()));
            }
        }
        return names;
    }

    private static void check(ArchRule rule, List<String> violations) {
        try {
            rule.check(CLASSES);
        } catch (AssertionError e) {
            violations.add(e.getMessage());
        }
    }

    private static void failIfAny(List<String> violations) {
        if (!violations.isEmpty()) {
            fail(String.join("\n\n", violations));
        }
    }

    private static Set<String> csv(String value) {
        Set<String> set = new TreeSet<>();
        Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
        return set;
    }

    private static Properties load() {
        Path file = Paths.get(System.getProperty("gauntlet.architecture", "gauntlet/config/architecture.properties"));
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read architecture policy " + file.toAbsolutePath(), e);
        }
        if (p.getProperty("base.package", "").trim().isEmpty()) {
            throw new IllegalStateException("base.package is missing in " + file.toAbsolutePath());
        }
        return p;
    }
}
