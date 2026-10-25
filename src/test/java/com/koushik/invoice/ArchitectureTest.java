package com.koushik.invoice;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/** Keeps the seams from ARCHITECTURE.md true: layers only call downwards, modules only invoicing → transaction. */
@AnalyzeClasses(packages = "com.koushik.invoice", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule layersOnlyCallDownwards = layeredArchitecture().consideringOnlyDependenciesInLayers()
		.layer("Api").definedBy("..api..")
		.layer("Application").definedBy("..application..")
		.layer("Domain").definedBy("..domain..")
		.layer("Infrastructure").definedBy("..infrastructure..")
		.whereLayer("Api").mayNotBeAccessedByAnyLayer()
		.whereLayer("Application").mayOnlyBeAccessedByLayers("Api")
		.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
		.whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

	@ArchTest
	static final ArchRule domainDoesNotDependOnOuterLayers = noClasses().that()
		.resideInAPackage("..domain..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("..application..", "..api..", "..infrastructure..");

	@ArchTest
	static final ArchRule transactionModuleDoesNotDependOnInvoicing = noClasses().that()
		.resideInAPackage("com.koushik.invoice.transaction..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.koushik.invoice.invoicing..");

}
