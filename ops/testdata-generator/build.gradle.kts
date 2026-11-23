// Synthetic CSV files in the formats of data.mos.ru and houses.csv, for import and load tests.
plugins {
	java
	application
	checkstyle
	id("com.diffplug.spotless") version "8.11.0"
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

dependencies {
	testImplementation(platform("org.junit:junit-bom:6.1.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
	mainClass = "ru.moscow.hotspot_tracker.testdata.Main"
}

tasks.test {
	useJUnitPlatform()
}

// The same formatting and rules as the server
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
	configFile = file("../../server/config/checkstyle/checkstyle.xml")
	maxWarnings = 0
}
