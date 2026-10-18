#!/usr/bin/env python3
"""Audit the resolved app, test, processor, Checkstyle and Gradle-plugin Maven graphs."""

import json
from pathlib import Path
import tempfile

from maven_audit import audit_inventory
from verify_package import ROOT, gradle


def main():
    with tempfile.TemporaryDirectory(prefix="escalated-spring-audit-") as directory:
        temporary = Path(directory).resolve()
        if temporary.parent != Path(tempfile.gettempdir()).resolve():
            raise RuntimeError("Unexpected temporary directory location")
        inventory = temporary / "dependencies.json"
        gradle("--init-script", ROOT / "scripts/dependency_inventory.gradle",
               f"-Ddependency.inventory.output={inventory}", "dependencyInventory")
        rows = json.loads(inventory.read_text(encoding="utf-8"))
        audit_inventory(rows, ROOT / "build/reports/dependency-audit.json",
                        {"runtimeClasspath", "testRuntimeClasspath", "annotationProcessor", "checkstyle", "classpath"})


if __name__ == "__main__":
    main()
