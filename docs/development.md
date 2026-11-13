# Development

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# Verify the wrapper checksum and boot a host from the documented Maven package
python scripts/verify_package.py

# Audit the resolved runtime, test, processor, Checkstyle and Gradle-plugin graphs
python scripts/verify_dependencies.py
```

Package verification requires Python 3, Java 17+ and Maven 3.9+. Alternatively,
`python scripts/verify_package.py --maven-docker` runs the Maven consumer with
Java 17 in Docker. It checks Gradle metadata, POM-only resolution and actual
Maven compilation, host startup and dependency audits.
