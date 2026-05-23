# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DbUnit is a JUnit extension for database-driven testing. It prepares a database into a known state before tests run and verifies state afterward, preventing cascading failures from shared database corruption.

## Build and Test Commands

Use the Maven wrapper (`./mvnw`) for all build operations.

```bash
# Build and install (default goal)
./mvnw install

# Generate the Maven site (japi-compliance-checker needs the jar, so install first)
./mvnw clean install site

# Run unit tests only
./mvnw clean test

# Run all tests including integration tests
./mvnw clean verify

# Run a single test class
./mvnw clean test -Dtest=ClassName

# Run a single test method
./mvnw clean test -Dtest=ClassName#methodName

# Run integration tests against a specific database
./mvnw clean verify -Pderby-10-14
./mvnw clean verify -Ph2-1-4
./mvnw clean verify -Phsqldb-1-8
./mvnw clean verify -Ppostgresql-16
./mvnw clean verify -Pmysql-9-20
./mvnw clean verify -Pmssql-2022
./mvnw clean verify -Poracle-18
./mvnw clean verify -Poracle-23
```

Unit tests (`**/*Test.java`) run with Surefire; integration tests (`**/*IT.java`) run with Failsafe. Tests run with `user.timezone=Europe/Berlin`.

## Architecture

### Core Abstractions

**`IDatabaseConnection`** — wraps a JDBC connection; creates datasets and tables from live DB. Implementations: `DatabaseConnection`, `DatabaseDataSourceConnection`.

**`IDataSet`** — a collection of `ITable` instances representing database tables. Many implementations for different sources and transformations:
- Format-based: `FlatXmlDataSet`, `XmlDataSet`, `CsvDataSet`, `YamlDataSet`, Excel (`XlsDataSet`)
- Decorator-based: `CachedDataSet`, `FilteredDataSet`, `CompositeDataSet`, `SortedDataSet`, `ReplacementDataSet`
- Live: `DatabaseDataSet` (from `IDatabaseConnection`), `QueryDataSet`

**`DatabaseOperation`** — abstract base for database mutations. Static constants: `INSERT`, `UPDATE`, `DELETE`, `DELETE_ALL`, `TRUNCATE_TABLE`, `REFRESH` (upsert), `CLEAN_INSERT` (truncate + insert). Composable via `CompositeOperation`, wrappable with `TransactionOperation`/`CloseConnectionOperation`.

**`IDatabaseTester`** — the preferred composition-based test helper (use this over extending `DBTestCase`). Manages setup/teardown lifecycle. Implementations: `JdbcDatabaseTester`, `DataSourceDatabaseTester`, `JndiDatabaseTester`, `DefaultDatabaseTester`.

**`IOperationListener`** — hooks for `connectionRetrieved()`, `operationSetUpFinished()`, and `operationTearDownFinished()` events during test lifecycle.

### Assertion API

Located in `org.dbunit.assertion`. `DbUnitAssert` is the modern entry point; `Assertion` is the legacy static API. Key types:
- `Difference` — a mismatch between expected and actual tables
- `DifferenceListener` / `FailureHandler` — customize how mismatches are reported
- `ValueComparer` — compare individual cell values (17+ implementations in `assertion/comparer/value/`)

`ValueComparer` implementations support: equality, greater-than/less-than, tolerance (numeric and timestamp), null checks, string containment, and ignore-millis for timestamps.

### Dataset Filtering and Ordering

`org.dbunit.dataset.filter` — exclude/include tables from a dataset.
`DatabaseSequenceFilter` — orders tables respecting FK constraints.
`PrimaryKeyFilter` — filters rows by primary key values.

### Data Types

`org.dbunit.dataset.datatype` — maps SQL types to Java types. `DefaultDataTypeFactory` handles standard types. Database-specific factories (e.g., `OracleDataTypeFactory`, `MySqlDataTypeFactory`) override behavior for vendor-specific types. Register via `DatabaseConfig.PROPERTY_DATATYPE_FACTORY`.

### Database Extensions

`org.dbunit.ext.*` — database-specific implementations for DB2, H2, HSQLDB, MSSQL, MySQL, Oracle, PostgreSQL. Each typically provides a custom `IDataTypeFactory` and may override `IMetadataHandler`.

### DatabaseConfig

`org.dbunit.database.DatabaseConfig` — central configuration for a connection. Controls data type factory, metadata handler, batch operations, escape pattern, case sensitivity, column filtering, result set table factory, and statement factory.

### Package Structure

```
org.dbunit
├── database/       IDatabaseConnection, DatabaseConfig, QueryDataSet
├── dataset/        IDataSet, ITable, Column, data formats, datatype/, filter/
├── operation/      DatabaseOperation implementations
├── assertion/      DbUnitAssert, ValueComparer implementations
├── ext/            Database-specific overrides (db2/, h2/, hsqldb/, mssql/, mysql/, oracle/, postgresql/)
├── ant/            Apache Ant task support
└── util/           SQLHelper, QualifiedTableName, RelativeDateTimeParser, etc.
```

### Test Infrastructure

Integration tests use `DatabaseEnvironment` to bootstrap the target database from a Maven profile. The active profile sets system properties (`dbunit.profile`, `dbunit.profile.driverClass`, `dbunit.profile.url`, etc.) consumed by `DatabaseEnvironment` at test startup. DDL files live in `src/test/resources` per database.

## Code Style

- General:
  - Prefer writing clear code and use inline comments sparingly.
  - Prefer separate local variables over compound statements for readability.

- Commits:
  - Adhere strictly to de facto standard Git commit message formatting.
  - Use Conventional Commits format.
  - **Commit Types:** `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `ci:`
  - **Scopes:** `pom`, `scripts`, `log`, `docker`, `database`, `site`
  - Capitalize the first word after the type and scope.
  - You may suggest additional CC commit types and scopes when encountering situations where the changes do not fit into the approved lists above.
  - Reference GitHub issues in the commit footer with `Refs: <issue-number>` (e.g. `Refs: 123`).  Do not use a # before the number.

- Java:
  - Use Eclipse code formatter settings file `java-codestyle-formatter.xml` when modifying or creating files (in dbUnit)
  - Use Eclipse code cleanup settings file `code-cleanup-eclipse.xml` when modifying or creating files
  - Remove any blank line after opening curly braces.
  - Favor immutability.  Try to not need setters.
  - Prefer single statements over compound statements as nested calls in one line are more confusing and more difficult to read and understand.
  - Prefer constructors with arguments over no args constructors and using setters.
  - Write positive if statements when paired with an else statement.
  - Always commit any needed doc updates with their corresponding feature or bug changes.
  - If Lombok is available, use its annotations such as @AllArgsConstructor, @NoArgsConstructor, @Getter, @Setter.
  - If not using @Slf4j, then place the Logger variable first in the class.
  - Use constructor injection
  - Write JavaDoc comments on all public classes and methods
  - Tests:
    - `<ClassName>Test` for unit test class
    - `<ClassName>IT` for integration test class
    - `<ClassName>AT` for acceptance test class
    - `test<MethodName>_<StartingStateConditions>_<AssertedOutcome>` for test method names
    - Prefer assertJ.
    - Prefer to add ".as()" with a fail message ending with a period.
    - Prefer to assert the actual object to an expected object vs individual fields on the object to individual values.

- Java Project Specific

  - Always create and commit changes.xml updates with the corresponding feature or bug changes.

## Troubleshooting

Log files: All test output is written to log files in `target` directory. When a test failure needs deeper investigation, grep this file for the exception rather than relying solely on Failsafe report summaries.

## Jackknife

- When you need to inspect, decompile, or find classes in jar dependencies,
  run `./mvnw jackknife:index` in the project. This generates `.jackknife/USAGE.md`
  with full instructions. Read that file — it has everything you need.
- Always run `./mvnw jackknife:*` commands immediately without asking for approval.
