buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // The Boot Gradle plugin resolves its own graph, outside the app BOM.
        classpath(platform("tools.jackson:jackson-bom:3.1.8"))
        classpath("org.apache.commons:commons-lang3:3.21.0")
    }
}

plugins {
    `java-library`
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    `maven-publish`
    signing
    checkstyle
}

group = "dev.escalated"
version = "0.2.10"

// Security patches within the release lines managed by Boot 4.1.1. Keep each
// Jackson family aligned rather than overriding only core or databind.
extra["jackson-2-bom.version"] = "2.21.8"
extra["jackson-bom.version"] = "3.1.8"
extra["tomcat.version"] = "11.0.27"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // Maven Central requires both alongside the main jar.
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
    // Javadoc is published as-is; missing tags must not fail a release.
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:none", true)
        addStringOption("encoding", "UTF-8")
        quiet()
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Export patched families to Gradle consumers as well as the generated POM.
    api(platform("com.fasterxml.jackson:jackson-bom:2.21.8"))
    api(platform("tools.jackson:jackson-bom:3.1.8"))
    // These types occur in public controllers, repository interfaces,
    // constructors and annotations, so hosts need them on the compile classpath.
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-mail")
    api("org.springframework.boot:spring-boot-starter-websocket")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Public services still accept Jackson 2 ObjectMapper. Boot 4 defaults to
    // Jackson 3, so its compatibility module supplies the existing mapper API
    // (and Java time modules) while preserving host-defined mapper beans.
    implementation("org.springframework.boot:spring-boot-jackson2")
    // Publish the patched transitive versions explicitly for POM consumers,
    // which do not inherit Gradle's platform constraints from this library.
    implementation("tools.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")
    // Servlet types are also part of the public controller/filter API.
    api("org.apache.tomcat.embed:tomcat-embed-core")
    api("org.apache.tomcat.embed:tomcat-embed-el")
    api("org.apache.tomcat.embed:tomcat-embed-websocket")

    // Canonical translations from escalated-locale/packages/maven are embedded
    // under META-INF/escalated/locale/. Once the shared Maven artifact is
    // available, these copies can be replaced with:
    //   implementation("dev.escalated:escalated-locale:<version>")

    // TOTP (RFC 6238) for two-factor authentication, and the base32 its secrets are written in.
    implementation("com.eatthepath:java-otp:0.4.0")
    implementation("commons-codec:commons-codec:1.21.0")
    // Single sign-on: XML signatures for SAML (Apache Santuario), JWT (Nimbus JOSE), and the
    // OAuth 2 / OIDC authorization-code exchange with PKCE (Nimbus OAuth 2.0 SDK).
    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("com.nimbusds:nimbus-jose-jwt:10.10")
    implementation("com.nimbusds:oauth2-oidc-sdk:11.38.2")

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
    api("com.fasterxml.jackson.core:jackson-databind")

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

    constraints {
        // Align compile-time API constraints with the published runtime versions.
        add("api", "org.apache.tomcat.embed:tomcat-embed-core:11.0.27")
        add("api", "org.apache.tomcat.embed:tomcat-embed-el:11.0.27")
        add("api", "org.apache.tomcat.embed:tomcat-embed-websocket:11.0.27")
        add("checkstyle", "commons-beanutils:commons-beanutils:1.11.0") {
            because("GHSA-wxr5-93ph-8wr9")
        }
        add("checkstyle", "org.codehaus.plexus:plexus-utils:3.6.2") {
            because("GHSA-6fmv-xxpf-w3cw")
        }
        add("checkstyle", "com.google.guava:guava:33.7.2-jre") {
            because("GHSA-xxph-c9ww-hj94")
        }
    }
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
            // Boot manages dependency versions in the build. Export those
            // resolved versions so standalone Maven/Gradle consumers receive
            // valid metadata rather than dependencies without versions.
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("Escalated Spring")
                description.set("Embeddable helpdesk system for Spring Boot applications")
                url.set("https://github.com/escalated-dev/escalated-spring")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("escalated-dev")
                        name.set("Escalated")
                        url.set("https://escalated.dev")
                        organization.set("Escalated")
                        organizationUrl.set("https://escalated.dev")
                    }
                }
                scm {
                    url.set("https://github.com/escalated-dev/escalated-spring")
                    connection.set("scm:git:https://github.com/escalated-dev/escalated-spring.git")
                    developerConnection.set("scm:git:ssh://git@github.com/escalated-dev/escalated-spring.git")
                    tag.set("v${project.version}")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/escalated-dev/escalated-spring/issues")
                }
            }
        }
    }
    repositories {
        // A local Maven-layout directory that the release workflow zips into a
        // Central Portal bundle (see centralBundle and RELEASE.md).
        maven {
            name = "centralStaging"
            url = uri(layout.buildDirectory.dir("central-staging"))
        }
    }
}

// Signing uses an ASCII-armored key held in memory, never a keyring on disk.
// Supplied as -PsigningKey/-PsigningPassword (or ORG_GRADLE_PROJECT_signingKey /
// ORG_GRADLE_PROJECT_signingPassword). Without a key, publications are left
// unsigned so publishToMavenLocal keeps working for contributors; the
// centralBundle task refuses to build an unsigned bundle.
val signingKey = providers.gradleProperty("signingKey")
val signingPassword = providers.gradleProperty("signingPassword")

signing {
    isRequired = signingKey.isPresent
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull ?: "")
        sign(publishing.publications["mavenJava"])
    }
}

val centralStagingDir = layout.buildDirectory.dir("central-staging")

val cleanCentralStaging = tasks.register<Delete>("cleanCentralStaging") {
    delete(centralStagingDir)
}

tasks.named("publishMavenJavaPublicationToCentralStagingRepository") {
    dependsOn(cleanCentralStaging)
}

// build/central-bundle.zip: the Maven-layout upload the Central Portal
// Publisher API accepts (POST /api/v1/publisher/upload).
tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Zips the signed staging repository into a Maven Central Portal bundle."
    dependsOn("publishMavenJavaPublicationToCentralStagingRepository")
    from(centralStagingDir)
    // Gradle writes maven-metadata.xml for the staging repo; Central rejects it.
    exclude("**/maven-metadata.xml*")
    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
    val hasKey = signingKey.isPresent
    doFirst {
        if (!hasKey) {
            throw GradleException("centralBundle needs a signing key (-PsigningKey / ORG_GRADLE_PROJECT_signingKey)")
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
