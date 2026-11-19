import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import net.ltgt.gradle.errorprone.errorprone

plugins {
	java
	checkstyle
	jacoco
	pmd
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.11.0"
	id("com.github.spotbugs") version "6.5.13"
	id("net.ltgt.errorprone") version "5.2.0"
	id("org.sonarqube") version "7.5.0.8588"
	id("org.owasp.dependencycheck") version "13.0.0"
}

group = "ru.moscow.hotspot-tracker"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jpa")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	errorprone("com.google.errorprone:error_prone_core:2.52.0")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

spotless {
	java {
		target("src/**/*.java")
		palantirJavaFormat("2.103.0")
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

checkstyle {
	toolVersion = "14.4.0"
	configFile = file("config/checkstyle/checkstyle.xml")
	maxWarnings = 0
}

pmd {
	toolVersion = "7.29.0"
	isConsoleOutput = true
	ruleSets = emptyList()
	ruleSetFiles = files("config/pmd/ruleset.xml")
}

spotbugs {
	toolVersion = "4.10.5"
	effort = Effort.MAX
	reportLevel = Confidence.MEDIUM
	excludeFilter = file("config/spotbugs/exclude.xml")
}

// Tests are checked by Checkstyle and Error Prone; PMD and SpotBugs are for production code
tasks.named("pmdTest") { enabled = false }
tasks.named("spotbugsTest") { enabled = false }

tasks.withType<JavaCompile>().configureEach {
	options.errorprone.disableWarningsInGeneratedCode = true
}

jacoco {
	toolVersion = "0.8.16"
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

// NFR-MNT-01: 70% of lines for the service layer, 85% for authorization and the load model
tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)
	violationRules {
		rule {
			element = "CLASS"
			includes = listOf("ru.moscow.hotspot_tracker.*Service*")
			limit {
				counter = "LINE"
				minimum = "0.70".toBigDecimal()
			}
		}
		rule {
			element = "PACKAGE"
			includes = listOf("ru.moscow.hotspot_tracker.auth*", "ru.moscow.hotspot_tracker.load*")
			limit {
				counter = "LINE"
				minimum = "0.85".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}

// NFR-SEC-06: fail on known vulnerabilities with CVSS 7 or higher in what the application ships
dependencyCheck {
	failBuildOnCVSS = 7.0f
	scanConfigurations = listOf("runtimeClasspath")
	formats = listOf("HTML", "JSON")
	nvd {
		apiKey = System.getenv("NVD_API_KEY")
	}
}

sonar {
	properties {
		property("sonar.projectKey", "potionsnotusedlol_hotspot-tracker")
		property("sonar.organization", "potionsnotusedlol")
		property("sonar.host.url", "https://sonarcloud.io")
		property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
	}
}
