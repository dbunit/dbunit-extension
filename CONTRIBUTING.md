# Contributing to DbUnit

Thank you for your interest in contributing to DbUnit! This file is a quick start — the
full reference lives in the
[Developing DbUnit](https://dbunit.github.io/dbunit-extension/devguide.html) section of
the documentation site.

## Building and testing

Use the Maven wrapper for all build operations:

```bash
# Compile and run unit tests
./mvnw clean test

# Run all tests including integration tests (requires Docker)
./mvnw clean verify

# Run against a specific database profile
./mvnw clean verify -Ppostgresql-16
```

See `CLAUDE.md` for the full list of available database profiles, or the
[Integration Tests](https://dbunit.github.io/dbunit-extension/integrationtests.html)
page for running them in an IDE and troubleshooting a failure.

## Submitting changes

1. Fork the repository and create a feature branch from `main`.
2. Write or update tests to cover your change.
3. Format the code and add a `src/changes/changes.xml` entry in the same commit as the
   change it describes.
4. Ensure `./mvnw clean verify` passes locally.
5. Open a pull request with a clear description of the change and its motivation.

See [Branching & Pull
Requests](https://dbunit.github.io/dbunit-extension/github/pullrequests.html) for the
full process, and [Commit
Requirements](https://dbunit.github.io/dbunit-extension/commits.html) for the commit
message format.

## Code style

See [Coding Standards](https://dbunit.github.io/dbunit-extension/codingstandards.html)
for Java style, JavaDoc conventions, and dbUnit's own test-writing conventions, plus the
Eclipse formatter and Checkstyle/Modernizer tooling that partially automates and
enforces them.

## Changelog (`src/changes/changes.xml`)

Every user-facing change needs a `changes.xml` entry in the same commit that makes the
change. See [Changelog](https://dbunit.github.io/dbunit-extension/changelog.html) for
the entry format and placement rules.

Dependency updates opened by Dependabot are recorded automatically after each such PR
merges to `main` — you do not need to hand-edit `changes.xml` for routine dependency
bumps. See the Changelog page above for how that automation works, how to opt a PR out
of it, and how to replay it manually if it fails.
