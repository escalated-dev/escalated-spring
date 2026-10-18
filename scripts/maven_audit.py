"""Strict OSV queries shared by source and installed-package verification."""

from datetime import datetime, timezone
import json
import time
import urllib.error
import urllib.request


def audit_inventory(rows, output, expected_configurations):
    configurations = {row["configuration"] for row in rows}
    if configurations != expected_configurations:
        raise RuntimeError(f"Incomplete dependency graph: expected {expected_configurations}, got {configurations}")
    packages = sorted({(row["name"], row["version"]) for row in rows})
    if not packages:
        raise RuntimeError("The resolved Maven dependency inventory is empty")
    payload = json.dumps({"queries": [
        {"package": {"ecosystem": "Maven", "name": name}, "version": version}
        for name, version in packages
    ]}).encode("utf-8")
    for attempt in range(3):
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=payload,
                                         headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                results = json.load(response)["results"]
            break
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
    if len(results) != len(packages) or any(value.get("next_page_token") for value in results):
        raise RuntimeError("The vulnerability service returned an incomplete response")
    findings = [
        {"name": name, "version": version, "vulnerabilities": result["vulns"]}
        for (name, version), result in zip(packages, results) if result.get("vulns")
    ]
    report = {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "source": "https://api.osv.dev/v1/querybatch",
        "configurations": sorted(configurations),
        "packages": [{"name": name, "version": version} for name, version in packages],
        "findings": findings,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if findings:
        raise RuntimeError("Vulnerable Maven dependencies: " + json.dumps(findings))
    print(f"MAVEN_AUDIT_OK: {len(packages)} resolved packages across {len(configurations)} configurations; no known OSV findings")
