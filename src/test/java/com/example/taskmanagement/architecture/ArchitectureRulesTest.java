package com.example.taskmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private static final String ROOT = "com.example.taskmanagement";
    private static final JavaClasses APPLICATION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);
    private static final Map<String, Set<String>> MODULE_DEPENDENCIES = Map.ofEntries(
            Map.entry("identity", Set.of()),
            Map.entry("project", Set.of("identity")),
            Map.entry("planning", Set.of()),
            Map.entry("task", Set.of("project", "identity", "planning", "resource", "integration.outbox")),
            Map.entry("resource", Set.of("identity")),
            Map.entry("scheduling", Set.of("project", "task", "planning", "resource")),
            Map.entry("workflow", Set.of("project", "task", "collaboration", "attachment", "scheduling")),
            Map.entry("collaboration", Set.of("project", "task", "identity", "integration.outbox")),
            Map.entry("attachment", Set.of("project", "task", "identity")),
            Map.entry("notification", Set.of("identity", "project", "task", "integration.outbox")),
            Map.entry("security", Set.of("identity")),
            Map.entry("integration.outbox", Set.of()),
            Map.entry("shared", Set.of()),
            Map.entry("alpha", Set.of("planning")),
            Map.entry("workspace", Set.of()));

    @Test
    void planningDependsOnlyOnItselfAndJdk() {
        classes().that().resideInAPackage(ROOT + ".planning..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".planning..")
                .check(APPLICATION);
    }

    @Test
    void lifecycleRemainsIndependentOfFrameworksAndAdapters() {
        classes().that().resideInAPackage(ROOT + ".task.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".task.domain..")
                .check(APPLICATION);
    }

    @Test
    void modulesUseOnlyAllowedPublicContracts() {
        classes().should(new ArchCondition<JavaClass>("use allowed module APIs and planning core") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                String sourceModule = moduleOf(source);
                if (source.getPackageName().equals(ROOT) || sourceModule.equals("config")
                        || sourceModule.equals("bootstrap")) {
                    return; // Root application and bootstrap/config wire the modules.
                }
                if (!MODULE_DEPENDENCIES.containsKey(sourceModule)) {
                    events.add(SimpleConditionEvent.violated(source,
                            source.getName() + " belongs to an undeclared module"));
                    return;
                }
                for (var dependency : source.getDirectDependenciesFromSelf()) {
                    var target = dependency.getTargetClass();
                    if (!target.getPackageName().startsWith(ROOT + ".") && !target.getPackageName().equals(ROOT)) {
                        continue;
                    }
                    String targetModule = moduleOf(target);
                    if (sourceModule.equals(targetModule)) {
                        continue;
                    }
                    boolean allowed = targetModule.equals("shared")
                            || MODULE_DEPENDENCIES.get(sourceModule).contains(targetModule);
                    String contract = ROOT + "." + targetModule
                            + (targetModule.equals("planning") ? ".core" : ".api");
                    boolean publicContract = targetModule.equals("shared") || target.getPackageName().equals(contract)
                            || target.getPackageName().startsWith(contract + ".");
                    if (!allowed || !publicContract) {
                        events.add(SimpleConditionEvent.violated(dependency,
                                dependency.getDescription() + " bypasses allowed module contracts"));
                    }
                }
            }
        }).check(APPLICATION);
    }

    @Test
    void packagesHaveNoCycles() {
        slices().matching(ROOT + ".(**)").should().beFreeOfCycles().check(APPLICATION);
    }

    private static String moduleOf(JavaClass type) {
        String packageName = type.getPackageName();
        if (!packageName.startsWith(ROOT + ".")) {
            return "";
        }
        String relative = packageName.substring(ROOT.length() + 1);
        if (relative.equals("integration.outbox") || relative.startsWith("integration.outbox.")) {
            return "integration.outbox";
        }
        return relative.split("\\.")[0];
    }
}
