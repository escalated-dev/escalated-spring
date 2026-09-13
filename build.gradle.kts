plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    `maven-publish`
    checkstyle
}

group = "dev.escalated"
version = "0.1.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Central translations: shipped as a Maven artifact via dev.escalated:escalated-locale
    // but currently vendored at src/main/resources/META-INF/escalated/locale/ until the
    // Maven Central publish pipeline is online (see escalated-locale's publish.yml — the
    // MAVEN_USERNAME / MAVEN_PASSWORD / MAVEN_GPG_PASSPHRASE secrets are not configured,
    // so the maven publish job no-ops and the artifact is unresolvable). Once the artifact
    // is published, replace the vendored .properties files with:
    //   implementation("dev.escalated:escalated-locale:<version>")

    implementation("org.flywaydb:flyway-core")
    // Flyway 10 moved database support out of core. Without these, core
    // answers "Unsupported Database" for both engines Escalated ships
    // migrations for.
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.flywaydb:flyway-mysql")
    // Compiled against, never shipped: Escalated orders its migrations ahead of
    // the host's own Flyway when the host has Boot's Flyway support, and does
    // nothing of the kind when it does not.
    compileOnly("org.springframework.boot:spring-boot-flyway")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Boot's Flyway support, so the upgrade test has a host Flyway that
    // validates its own history after Escalated adopts its old rows.
    testImplementation("org.springframework.boot:spring-boot-flyway")
}

// The database the suite runs against, chosen from the environment. H2 when
// nothing is set, so running the tests locally needs nothing installed; CI sets
// these for the PostgreSQL and MySQL legs.
//
// Passed through explicitly rather than relying on the Gradle daemon's
// environment -- a daemon started before the variables were exported keeps the
// environment it was started with, which is how a leg silently runs on H2 and
// reports green. Declaring them as task inputs also makes Gradle re-run the
// tests when they change, instead of calling the task up to date.
val databaseEnvironment = listOf(
    "ESCALATED_TEST_URL",
    "ESCALATED_TEST_DRIVER_CLASS",
    "ESCALATED_TEST_USERNAME",
    "ESCALATED_TEST_PASSWORD",
    "ESCALATED_TEST_PLATFORM",
)

// Which database the suite is meant to be running on. Passed as -Pdatabase=...
// rather than read from the environment on purpose: a Gradle daemon started
// before the variables were exported keeps the environment it was started with,
// so an environment-only check cannot tell "postgres leg" from "postgres leg
// that silently fell back to H2". A command-line property cannot be stale.
val expectedDatabase = providers.gradleProperty("database").getOrElse("h2")

tasks.withType<Test> {
    useJUnitPlatform()

    // DatabaseEngineTest compares this against what actually answered.
    systemProperty("escalated.test.database", expectedDatabase)
    inputs.property("database", expectedDatabase)

    databaseEnvironment.forEach { name ->
        val value = System.getenv(name)

        inputs.property(name, value).optional(true)

        if (value != null) {
            environment(name, value)
        }
    }
}

checkstyle {
    toolVersion = "10.15.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Escalated Spring")
                description.set("Embeddable helpdesk system for Spring Boot applications")
                url.set("https://github.com/escalated-dev/escalated-spring")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
