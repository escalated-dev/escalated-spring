#!/usr/bin/env python3
"""Verify wrapper integrity and boot a host using the README and a local Maven artifact."""

import hashlib
import json
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

from maven_audit import audit_inventory


ROOT = Path(__file__).resolve().parents[1]


def verification_environment():
    return {
        key: value for key, value in os.environ.items()
        if not key.upper().startswith(("SPRING_", "ESCALATED_", "SMTP_", "MAIL_"))
        and key.upper() not in {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS"}
    }


def gradle(*arguments):
    launcher = [str(ROOT / "gradlew")]
    if os.name == "nt":
        launcher = ["cmd", "/d", "/c", str(ROOT / "gradlew.bat")]
    subprocess.run(launcher + ["--no-daemon", "--console=plain", *map(str, arguments)],
                   cwd=ROOT, env=verification_environment(), check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-docker", action="store_true", help="Use Maven 3.9 with Java 17 in Docker")
    options = parser.parse_args()
    if not options.maven_docker and not shutil.which("mvn"):
        raise RuntimeError("Maven is required; install Maven or use --maven-docker")
    properties = dict(line.split("=", 1) for line in
                      (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.startswith("#"))
    if not re.fullmatch(r"[0-9a-f]{64}", properties.get("distributionSha256Sum", "")):
        raise RuntimeError("The committed Gradle distribution must have a SHA256 pin")
    readmes = [ROOT / "README.md", *sorted((ROOT / "docs/translations").glob("README.*.md"))]
    snippets = set()
    for path in readmes:
        blocks = re.findall(r"```kotlin\s*\n(.*?)```", path.read_text(encoding="utf-8"), re.S)
        installation = [block for block in blocks if 'implementation("dev.escalated:escalated-spring:' in block]
        if len(installation) != 1:
            raise RuntimeError(f"Expected one Gradle installation example in {path}")
        snippets.add(installation[0])
    if len(snippets) != 1:
        raise RuntimeError("The translated installation examples have diverged")
    snippet = snippets.pop()
    version = re.search(r'implementation\("dev.escalated:escalated-spring:([^"\n]+)"\)', snippet).group(1)

    with tempfile.TemporaryDirectory(prefix="escalated-spring-package-") as directory:
        temporary = Path(directory).resolve()
        if temporary.parent != Path(tempfile.gettempdir()).resolve():
            raise RuntimeError("Unexpected temporary directory location")

        # A corrupt local archive must be rejected before Gradle can execute it.
        wrapper = temporary / "checksum-control/gradle/wrapper"
        wrapper.mkdir(parents=True)
        shutil.copy2(ROOT / "gradle/wrapper/gradle-wrapper.jar", wrapper / "gradle-wrapper.jar")
        archive = temporary / "checksum-control/gradle-test-bin.zip"
        archive.write_bytes(b"This is not a Gradle distribution.")
        (wrapper / "gradle-wrapper.properties").write_text(
            f"distributionUrl={archive.as_uri()}\ndistributionSha256Sum={'0' * 64}\n", encoding="utf-8")
        java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
        control = subprocess.run([
            java, f"-Dgradle.user.home={temporary / 'checksum-cache'}", "-classpath", str(wrapper / "gradle-wrapper.jar"),
            "org.gradle.wrapper.GradleWrapperMain", "--version",
        ], cwd=temporary, env=verification_environment(), capture_output=True, text=True, encoding="utf-8", errors="replace")
        if control.returncode == 0 or "Verification of Gradle distribution failed" not in control.stdout + control.stderr:
            raise RuntimeError("Wrapper checksum negative control did not reject the archive: " + control.stdout + control.stderr)
        print("WRAPPER_CHECKSUM_CONTROL_OK: corrupt archive rejected", flush=True)

        repository = temporary / "maven-repository"
        repository_property = f"-Dmaven.repo.local={repository}"
        gradle(repository_property, "publishToMavenLocal")
        installed = repository / "dev/escalated/escalated-spring" / version / f"escalated-spring-{version}.jar"
        built = ROOT / "build/libs" / installed.name
        if not installed.is_file() or hashlib.sha256(installed.read_bytes()).digest() != hashlib.sha256(built.read_bytes()).digest():
            raise RuntimeError("Installed Maven JAR differs from the current build")
        with zipfile.ZipFile(installed) as package:
            major = int.from_bytes(package.read("dev/escalated/config/EscalatedProperties.class")[6:8], "big")
            if major != 61:
                raise RuntimeError(f"Expected Java 17 bytecode (61), got {major}")
            imports = package.read("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports").decode("utf-8")
            if "dev.escalated.config.EscalatedAutoConfiguration" not in imports:
                raise RuntimeError("Published JAR has no Escalated auto-configuration registration")

        consumer = temporary / "consumer"
        source = consumer / "src/main/java/consumer"
        source.mkdir(parents=True)
        shutil.copy2(ROOT / "tests/package-consumer/Smoke.java", source / "Smoke.java")
        (consumer / "settings.gradle.kts").write_text('rootProject.name = "package-consumer"\n', encoding="utf-8")
        (consumer / "build.gradle.kts").write_text(
            'plugins { application }\n' + snippet + '\napplication { mainClass.set("consumer.Smoke") }\n', encoding="utf-8")
        verify_consumer(repository_property, consumer, temporary, "gradle")
        # Maven clients use the POM rather than Gradle's richer module metadata.
        # Force that resolution path too, including compile/runtime scopes.
        pom_consumer = temporary / "pom-consumer"
        pom_source = pom_consumer / "src/main/java/consumer"
        pom_source.mkdir(parents=True)
        shutil.copy2(source / "Smoke.java", pom_source / "Smoke.java")
        (pom_consumer / "settings.gradle.kts").write_text('rootProject.name = "pom-consumer"\n', encoding="utf-8")
        pom_snippet = snippet.replace("mavenLocal()", """mavenLocal {
        metadataSources {
            mavenPom()
            ignoreGradleMetadataRedirection()
        }
    }""", 1)
        (pom_consumer / "build.gradle.kts").write_text(
            'plugins { application }\n' + pom_snippet + '\napplication { mainClass.set("consumer.Smoke") }\n', encoding="utf-8")
        verify_consumer(repository_property, pom_consumer, temporary, "pom")
        verify_maven(temporary, repository, version, options.maven_docker)
        print(f"README_PACKAGE_OK: {len(readmes)} READMEs; Java 17 JAR SHA256 matched; enabled hosts boot using Gradle metadata and POM-only resolution", flush=True)


def verify_consumer(repository_property, consumer, temporary, metadata):
    inventory = temporary / f"{metadata}-dependencies.json"
    gradle(repository_property, "--project-dir", consumer,
           "--init-script", ROOT / "scripts/dependency_inventory.gradle",
           f"-Ddependency.inventory.output={inventory}", "dependencyInventory", "run")
    rows = json.loads(inventory.read_text(encoding="utf-8"))
    audit_inventory(rows, ROOT / f"build/reports/package-{metadata}-dependency-audit.json",
                    {"runtimeClasspath", "testRuntimeClasspath"})


def verify_maven(temporary, repository, version, docker):
    consumer = temporary / "maven-consumer"
    source = consumer / "src/main/java/consumer"
    source.mkdir(parents=True)
    shutil.copy2(ROOT / "tests/package-consumer/Smoke.java", source / "Smoke.java")
    project = ET.Element("project", xmlns="http://maven.apache.org/POM/4.0.0")
    for name, value in [("modelVersion", "4.0.0"), ("groupId", "consumer"), ("artifactId", "package-consumer"), ("version", "1")]:
        ET.SubElement(project, name).text = value
    properties = ET.SubElement(project, "properties")
    ET.SubElement(properties, "maven.compiler.release").text = "17"
    ET.SubElement(properties, "project.build.sourceEncoding").text = "UTF-8"
    dependency = ET.SubElement(ET.SubElement(project, "dependencies"), "dependency")
    for name, value in [("groupId", "dev.escalated"), ("artifactId", "escalated-spring"), ("version", version)]:
        ET.SubElement(dependency, name).text = value
    plugins = ET.SubElement(ET.SubElement(project, "build"), "plugins")
    for group, artifact, plugin_version in [
        ("org.apache.maven.plugins", "maven-compiler-plugin", "3.14.1"),
        ("org.codehaus.mojo", "exec-maven-plugin", "3.6.1"),
    ]:
        plugin = ET.SubElement(plugins, "plugin")
        for name, value in [("groupId", group), ("artifactId", artifact), ("version", plugin_version)]:
            ET.SubElement(plugin, name).text = value
        if artifact == "exec-maven-plugin":
            configuration = ET.SubElement(plugin, "configuration")
            ET.SubElement(configuration, "executable").text = "${java.home}/bin/java"
            arguments = ET.SubElement(configuration, "arguments")
            ET.SubElement(arguments, "argument").text = "-classpath"
            ET.SubElement(arguments, "classpath")
            ET.SubElement(arguments, "argument").text = "consumer.Smoke"
    ET.ElementTree(project).write(consumer / "pom.xml", encoding="utf-8", xml_declaration=True)
    if docker:
        command = ["docker", "run", "--rm", "--label", "task=escalated-spring-package-check",
                   "--mount", f"type=bind,source={temporary},target=/fixture",
                   "--workdir", "/fixture/maven-consumer", "--entrypoint", "mvn",
                   "maven:3.9.11-eclipse-temurin-17"]
        local_repository = "/fixture/maven-repository"
        tree = "/fixture/maven-consumer/dependencies.json"
    else:
        command = [shutil.which("mvn")]
        local_repository = str(repository)
        tree = str(consumer / "dependencies.json")
        if os.name == "nt":
            command = ["cmd", "/d", "/c", *command]
    subprocess.run(command + ["--batch-mode", "--no-transfer-progress", f"-Dmaven.repo.local={local_repository}",
                   "compile", "exec:exec",
                   "org.apache.maven.plugins:maven-dependency-plugin:3.9.0:tree",
                   "-DoutputType=json", f"-DoutputFile={tree}"], cwd=consumer, env=verification_environment(), check=True)
    rows = []

    def collect(node):
        if node.get("groupId") != "consumer":
            rows.append({"name": node["groupId"] + ":" + node["artifactId"],
                         "version": node["version"], "configuration": "maven"})
        for child in node.get("children", []):
            collect(child)

    collect(json.loads((consumer / "dependencies.json").read_text(encoding="utf-8")))
    audit_inventory(rows, ROOT / "build/reports/package-maven-dependency-audit.json", {"maven"})
    print("MAVEN_CONSUMER_OK: actual Maven compile, enabled host startup and dependency audit", flush=True)


if __name__ == "__main__":
    main()
