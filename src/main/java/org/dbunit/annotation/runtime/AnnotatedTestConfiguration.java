/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.annotation.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitColumnComparer;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConfigPropertiesProvider;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.DataSetPathsProvider;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;

/**
 * Immutable configuration resolved from one test element's {@code org.dbunit.annotation}
 * instances, ready for {@link AnnotatedTestExecutor} to drive - no dataset has been applied to
 * a database yet, but every dataset path is normalized, every provider and catalog has been
 * consulted, and every reflective instantiation that can fail (a provider or comparer with no
 * accessible no-arg constructor, conflicting inline-value-and-provider attributes) has already
 * happened.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by a binding such as
 * {@code DbUnitExtension}. Building one is JUnit-free: {@link #from} takes the already-resolved
 * annotation instances (method-level, else class-level - a binding's job, since only it knows
 * how to search for them) rather than an {@code ExtensionContext}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public final class AnnotatedTestConfiguration
{
    private final DataFileLoader dataFileLoader;
    private final String[] prepDataFiles;
    private final boolean setupDeclared;
    private final DatabaseOperation setUpOperation;
    private final boolean expected;
    private final String[] expectedDataFiles;
    private final VerifyTableDefinition[] verifyTableDefinitions;
    private final boolean tearDownDeclared;
    private final DatabaseOperation tearDownOperation;
    private final Properties databaseConfigProperties;
    private final FailureHandler failureHandler;
    private final boolean closeConnectionAfterTest;
    private final Class<? extends DatabaseTesterFactory> databaseTesterFactory;
    private final Class<? extends PrepAndExpectedTestCase> prepAndExpectedTestCaseClass;
    private final boolean rowCountCheckDeclared;
    private final boolean rowCountCheckEnabled;
    private final String[] rowCountCheckExclude;

    private AnnotatedTestConfiguration(final DataFileLoader dataFileLoader,
            final String[] prepDataFiles, final boolean setupDeclared,
            final DatabaseOperation setUpOperation, final boolean expected,
            final String[] expectedDataFiles,
            final VerifyTableDefinition[] verifyTableDefinitions,
            final boolean tearDownDeclared, final DatabaseOperation tearDownOperation,
            final Properties databaseConfigProperties, final FailureHandler failureHandler,
            final boolean closeConnectionAfterTest,
            final Class<? extends DatabaseTesterFactory> databaseTesterFactory,
            final Class<? extends PrepAndExpectedTestCase> prepAndExpectedTestCaseClass,
            final boolean rowCountCheckDeclared, final boolean rowCountCheckEnabled,
            final String[] rowCountCheckExclude)
    {
        this.dataFileLoader = dataFileLoader;
        this.prepDataFiles = prepDataFiles;
        this.setupDeclared = setupDeclared;
        this.setUpOperation = setUpOperation;
        this.expected = expected;
        this.expectedDataFiles = expectedDataFiles;
        this.verifyTableDefinitions = verifyTableDefinitions;
        this.tearDownDeclared = tearDownDeclared;
        this.tearDownOperation = tearDownOperation;
        this.databaseConfigProperties = databaseConfigProperties;
        this.failureHandler = failureHandler;
        this.closeConnectionAfterTest = closeConnectionAfterTest;
        this.databaseTesterFactory = databaseTesterFactory;
        this.prepAndExpectedTestCaseClass = prepAndExpectedTestCaseClass;
        this.rowCountCheckDeclared = rowCountCheckDeclared;
        this.rowCountCheckEnabled = rowCountCheckEnabled;
        this.rowCountCheckExclude = rowCountCheckExclude;
    }

    /**
     * Resolves a configuration from one test element's annotation instances.
     *
     * @param testClass The test class; used to resolve dataset paths relative to its package.
     * @param config The resolved {@code @DbUnitConfig}, or {@code null} if absent.
     * @param prep The resolved {@code @DbUnitPrep}, or {@code null} if absent.
     * @param setup The resolved {@code @DbUnitSetup}, or {@code null} if absent.
     * @param expected The resolved {@code @DbUnitExpected}, or {@code null} if absent.
     * @param tearDown The resolved {@code @DbUnitTearDown}, or {@code null} if absent.
     * @param rowCountCheck The resolved {@code @DbUnitRowCountCheck}, or {@code null} if
     *            absent.
     * @return The resolved configuration.
     * @throws IllegalStateException If two mutually exclusive attributes are both set, or if
     *             a named provider, catalog, or comparer class cannot be instantiated.
     */
    public static AnnotatedTestConfiguration from(final Class<?> testClass,
            final DbUnitConfig config, final DbUnitPrep prep, final DbUnitSetup setup,
            final DbUnitExpected expected, final DbUnitTearDown tearDown,
            final DbUnitRowCountCheck rowCountCheck)
    {
        final String dataSetBaseDir = config == null ? "" : config.dataSetBaseDir();
        final DataSetResourcePathResolver pathResolver = new DataSetResourcePathResolver();

        final Class<? extends DataFileLoader> dataFileLoaderClass =
                config == null ? FileExtensionDataFileLoader.class : config.dataFileLoader();
        final DataFileLoader dataFileLoader =
                instantiate(dataFileLoaderClass, "DbUnitConfig.dataFileLoader");

        final String[] prepDataFiles = resolvePaths("DbUnitPrep", testClass, dataSetBaseDir,
                pathResolver, prep == null ? null : prep.value(),
                prep == null ? DataSetPathsProvider.class : prep.provider());
        final boolean setupDeclared = setup != null;
        final DatabaseOperation setUpOperation = setup == null ? DatabaseOperation.CLEAN_INSERT
                : setup.operation().toDatabaseOperation();
        final boolean tearDownDeclared = tearDown != null;
        final DatabaseOperation tearDownOperation =
                tearDown == null ? DatabaseOperation.NONE
                        : tearDown.operation().toDatabaseOperation();

        final boolean hasExpected = expected != null;
        final String[] expectedDataFiles = !hasExpected ? new String[0]
                : resolvePaths("DbUnitExpected", testClass, dataSetBaseDir, pathResolver,
                        expected.value(), expected.provider());
        final VerifyTableDefinition[] verifyTableDefinitions = !hasExpected
                ? new VerifyTableDefinition[0]
                : resolveVerifyTableDefinitions(config, expected, dataFileLoader,
                        expectedDataFiles);

        final Properties databaseConfigProperties = resolveProperties(config);
        final FailureHandler failureHandler = config == null
                || config.failureHandler() == FailureHandler.class ? null
                        : instantiate(config.failureHandler(), "DbUnitConfig.failureHandler");
        final boolean closeConnectionAfterTest =
                config == null || config.closeConnectionAfterTest();
        final Class<? extends DatabaseTesterFactory> databaseTesterFactory = config == null
                || config.databaseTesterFactory() == DatabaseTesterFactory.class ? null
                        : config.databaseTesterFactory();
        final Class<? extends PrepAndExpectedTestCase> prepAndExpectedTestCaseClass =
                config == null ? DefaultPrepAndExpectedTestCase.class
                        : config.prepAndExpectedTestCase();

        final boolean rowCountCheckDeclared = rowCountCheck != null;
        final boolean rowCountCheckEnabled =
                rowCountCheckDeclared && rowCountCheck.enabled();
        final String[] rowCountCheckExclude =
                rowCountCheckDeclared ? rowCountCheck.exclude() : new String[0];

        return new AnnotatedTestConfiguration(dataFileLoader, prepDataFiles, setupDeclared,
                setUpOperation, hasExpected, expectedDataFiles, verifyTableDefinitions,
                tearDownDeclared, tearDownOperation, databaseConfigProperties, failureHandler,
                closeConnectionAfterTest, databaseTesterFactory, prepAndExpectedTestCaseClass,
                rowCountCheckDeclared, rowCountCheckEnabled, rowCountCheckExclude);
    }

    private static String[] resolvePaths(final String annotationName, final Class<?> testClass,
            final String dataSetBaseDir, final DataSetResourcePathResolver pathResolver,
            final String[] value, final Class<? extends DataSetPathsProvider> provider)
    {
        if (value == null)
        {
            return new String[0];
        }
        final boolean providerSet = provider != DataSetPathsProvider.class;
        if (value.length > 0 && providerSet)
        {
            throw new IllegalStateException("@" + annotationName + " on " + testClass.getName()
                    + " sets both value() and provider(); set only one.");
        }
        final String[] paths = providerSet ? resolveProvidedPaths(annotationName, provider)
                : value;
        final String[] resolved = new String[paths.length];
        for (int i = 0; i < paths.length; i++)
        {
            resolved[i] = pathResolver.resolve(paths[i], testClass, dataSetBaseDir);
        }
        return resolved;
    }

    private static String[] resolveProvidedPaths(final String annotationName,
            final Class<? extends DataSetPathsProvider> provider)
    {
        final String[] paths =
                instantiate(provider, "@" + annotationName + ".provider").getDataSetPaths();
        if (paths == null)
        {
            throw new IllegalStateException("DataSetPathsProvider " + provider.getName()
                    + ", named by @" + annotationName + ".provider, returned null from"
                    + " getDataSetPaths().");
        }
        if (paths.length == 0)
        {
            throw new IllegalStateException("DataSetPathsProvider " + provider.getName()
                    + ", named by @" + annotationName + ".provider, returned no paths from"
                    + " getDataSetPaths(). A provider is wired to supply dataset paths; supplying"
                    + " none is a misconfiguration - drop @" + annotationName
                    + " for a test that needs no dataset, rather than pointing it at an empty"
                    + " provider.");
        }
        return paths;
    }

    private static VerifyTableDefinition[] resolveVerifyTableDefinitions(
            final DbUnitConfig config, final DbUnitExpected expected,
            final DataFileLoader dataFileLoader, final String[] expectedDataFiles)
    {
        final Class<?>[] classLevelCatalogs =
                config == null ? new Class<?>[0] : config.verifyDefinitions();
        final DbUnitVerifyTable[] verify = expected.verify();
        final String[] verifyTables = expected.verifyTables();

        if (verify.length > 0 && expected.verifyDefinitions().length > 0)
        {
            throw new IllegalStateException(
                    "@DbUnitExpected sets both verify() and verifyDefinitions(); set only"
                            + " one - see VerifyTableDefinitionsProvider.");
        }
        if (verify.length > 0 && verifyTables.length > 0)
        {
            throw new IllegalStateException(
                    "@DbUnitExpected sets both verify() and verifyTables(); verifyTables()"
                            + " only narrows a catalog, so set only one.");
        }
        requireNoDuplicateTableNames(verifyTables);
        final Class<?>[] catalogClasses;
        if (expected.verifyDefinitions().length > 0)
        {
            catalogClasses = expected.verifyDefinitions();
        } else if (verify.length > 0)
        {
            catalogClasses = new Class<?>[0];
        } else
        {
            catalogClasses = classLevelCatalogs;
        }
        if (catalogClasses.length > 0)
        {
            return VerifyTableDefinitionCatalog.forClasses(catalogClasses).select(verifyTables);
        }
        if (verify.length > 0)
        {
            final VerifyTableDefinition[] definitions =
                    new VerifyTableDefinition[verify.length];
            final Set<String> tableNames = new LinkedHashSet<>(verify.length);
            for (int i = 0; i < verify.length; i++)
            {
                if (!tableNames.add(verify[i].value()))
                {
                    throw new IllegalStateException("@DbUnitExpected declares more than one"
                            + " @DbUnitVerifyTable entry for table '" + verify[i].value()
                            + "'; declare at most one per table.");
                }
                definitions[i] = toVerifyTableDefinition(verify[i]);
            }
            return definitions;
        }
        if (verifyTables.length > 0)
        {
            final VerifyTableDefinition[] definitions =
                    new VerifyTableDefinition[verifyTables.length];
            for (int i = 0; i < verifyTables.length; i++)
            {
                definitions[i] = new VerifyTableDefinition(verifyTables[i], (String[]) null);
            }
            return definitions;
        }
        return defaultPerExpectedTable(dataFileLoader, expectedDataFiles);
    }

    private static void requireNoDuplicateTableNames(final String[] verifyTables)
    {
        final Set<String> tableNames = new LinkedHashSet<>(verifyTables.length);
        for (final String tableName : verifyTables)
        {
            if (!tableNames.add(tableName))
            {
                throw new IllegalStateException("@DbUnitExpected declares more than one"
                        + " verifyTables() entry for table '" + tableName
                        + "'; declare at most one per table.");
            }
        }
    }

    private static VerifyTableDefinition toVerifyTableDefinition(final DbUnitVerifyTable v)
    {
        final String[] include = v.include().length == 0 ? null : v.include();
        final ValueComparer defaultComparer = v.defaultComparer() == ValueComparer.class ? null
                : instantiateComparer(v.defaultComparer());
        final Map<String, ValueComparer> columnComparers =
                new LinkedHashMap<>(v.columnComparers().length);
        for (final DbUnitColumnComparer c : v.columnComparers())
        {
            if (columnComparers.containsKey(c.column()))
            {
                throw new IllegalStateException("@DbUnitVerifyTable(\"" + v.value()
                        + "\") declares more than one @DbUnitColumnComparer for column '"
                        + c.column() + "'; declare at most one per column.");
            }
            columnComparers.put(c.column(), instantiateComparer(c.comparer()));
        }
        return new VerifyTableDefinition(v.value(), v.exclude(), include, defaultComparer,
                columnComparers, v.sortOnFilteredColumnsOnly());
    }

    /**
     * Parses {@code expectedDataFiles} a second time here, just to enumerate table names -
     * {@code DefaultPrepAndExpectedTestCase#configureTest()} parses the same files again later
     * for the real verification. Left as-is: resolving a configuration is JUnit-free and runs
     * before any {@link PrepAndExpectedTestCase} exists to hand a parsed result to (or receive
     * one from), so avoiding the second parse needs a real change to that boundary, not a small
     * tweak - not worth it for what is normally a small test fixture file.
     */
    private static VerifyTableDefinition[] defaultPerExpectedTable(
            final DataFileLoader dataFileLoader, final String[] expectedDataFiles)
    {
        final Set<String> tableNames = new LinkedHashSet<>();
        for (final String path : expectedDataFiles)
        {
            final IDataSet dataSet = dataFileLoader.load(path);
            try
            {
                for (final String tableName : dataSet.getTableNames())
                {
                    tableNames.add(tableName);
                }
            } catch (final DataSetException e)
            {
                throw new IllegalStateException(
                        "Failed to read table names from expected dataset '" + path + "'.", e);
            }
        }
        final VerifyTableDefinition[] definitions =
                new VerifyTableDefinition[tableNames.size()];
        int i = 0;
        for (final String tableName : tableNames)
        {
            definitions[i++] = new VerifyTableDefinition(tableName, (String[]) null);
        }
        return definitions;
    }

    private static Properties resolveProperties(final DbUnitConfig config)
    {
        if (config == null)
        {
            return new Properties();
        }
        final DbUnitProperty[] properties = config.properties();
        final boolean providerSet =
                config.propertiesProvider() != DatabaseConfigPropertiesProvider.class;
        if (properties.length > 0 && providerSet)
        {
            throw new IllegalStateException(
                    "@DbUnitConfig sets both properties() and propertiesProvider(); set only"
                            + " one.");
        }
        if (providerSet)
        {
            final Properties provided = instantiate(config.propertiesProvider(),
                    "DbUnitConfig.propertiesProvider").getProperties();
            if (provided == null)
            {
                throw new IllegalStateException("DatabaseConfigPropertiesProvider "
                        + config.propertiesProvider().getName() + ", named by"
                        + " DbUnitConfig.propertiesProvider, returned null from"
                        + " getProperties().");
            }
            final Properties copy = new Properties();
            copy.putAll(provided);
            for (final String name : copy.stringPropertyNames())
            {
                requireKnownDatabaseConfigProperty(name, "DatabaseConfigPropertiesProvider "
                        + config.propertiesProvider().getName());
            }
            return copy;
        }
        final Properties result = new Properties();
        for (final DbUnitProperty property : properties)
        {
            if (result.containsKey(property.name()))
            {
                throw new IllegalStateException("@DbUnitConfig declares more than one"
                        + " @DbUnitProperty named '" + property.name() + "'; declare at most"
                        + " one per name.");
            }
            requireKnownDatabaseConfigProperty(property.name(), "@DbUnitConfig.properties()");
            result.setProperty(property.name(), property.value());
        }
        return result;
    }

    /**
     * Rejects a {@link org.dbunit.database.DatabaseConfig} property name - from
     * {@code @DbUnitProperty} or a {@link DatabaseConfigPropertiesProvider} - that
     * {@link DatabaseConfig} does not recognize in either its long
     * {@code http://www.dbunit.org/...} or short form. A typo would otherwise be dropped
     * silently by {@link DatabaseConfig#setPropertiesByString(Properties)}, which only logs
     * and moves on, leaving the test running with the misspelled property never applied.
     *
     * @param name The configured property name.
     * @param source A description of what configured it, for the exception message.
     */
    private static void requireKnownDatabaseConfigProperty(final String name,
            final String source)
    {
        if (DatabaseConfig.findByName(name) == null
                && DatabaseConfig.findByShortName(name) == null)
        {
            throw new IllegalStateException("'" + name + "' (configured via " + source
                    + ") is not a known dbUnit DatabaseConfig property name. Use its long"
                    + " 'http://www.dbunit.org/...' form or its short form - e.g. 'batchSize',"
                    + " 'caseSensitiveTableNames'; see DatabaseConfig.");
        }
    }

    private static final Map<Class<? extends ValueComparer>, ValueComparer> COMPARER_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Caches a comparer instance, for the JVM's entire lifetime, keyed by class - the same
     * once-and-reused-forever caching {@link VerifyTableDefinitionCatalog#forClasses} already
     * applies to a catalog combination, for the same reason: this resolves fresh on every test
     * method, and a suite naming the same comparer class from many methods would otherwise
     * reflectively construct a new, functionally-identical instance every time. See
     * {@link DbUnitColumnComparer#comparer()} for the resulting purity requirement.
     */
    private static ValueComparer instantiateComparer(
            final Class<? extends ValueComparer> comparerClass)
    {
        return COMPARER_CACHE.computeIfAbsent(comparerClass,
                AnnotatedTestConfiguration::newComparerInstance);
    }

    private static ValueComparer newComparerInstance(
            final Class<? extends ValueComparer> comparerClass)
    {
        try
        {
            return ReflectiveInstantiation.newInstance(comparerClass);
        } catch (final InvocationTargetException e)
        {
            throw new IllegalStateException("ValueComparer " + comparerClass.getName()
                    + " threw from its no-arg constructor.", e.getCause());
        } catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException("ValueComparer " + comparerClass.getName()
                    + " has no accessible no-arg constructor - it is likely a configured"
                    + " instance built with constructor arguments, which an annotation cannot"
                    + " express. Declare it as a VerifyTableDefinition constant in a catalog"
                    + " class instead; see VerifyTableDefinitionsProvider.", e);
        }
    }

    private static <T> T instantiate(final Class<? extends T> implementationClass,
            final String attributeDescription)
    {
        return ReflectiveInstantiation.instantiate(implementationClass, attributeDescription);
    }

    /**
     * Returns the {@link DataFileLoader} to use for every dataset file.
     *
     * @return The data file loader.
     */
    public DataFileLoader getDataFileLoader()
    {
        return dataFileLoader;
    }

    /**
     * Returns the resolved, absolute classpath paths of the prep dataset files.
     *
     * @return The prep dataset paths; empty when no {@code @DbUnitPrep} was declared.
     */
    public String[] getPrepDataFiles()
    {
        return prepDataFiles.clone();
    }

    /**
     * Returns whether {@code @DbUnitSetup} was declared.
     *
     * <p>A binding consults this, alongside {@link #getPrepDataFiles()} being non-empty, to
     * decide whether to call {@code setSetUpOperation} at all: with neither, the test runs with
     * the tester's own pre-configured setup (matching pre-annotation behaviour); with
     * {@code @DbUnitSetup} declared but no {@code @DbUnitPrep}, the operation is still applied
     * - most usefully {@link org.dbunit.operation.DatabaseOperation#NONE} - to whatever
     * dataset the tester already has, rather than leaving a tester with no dataset at all to
     * run its unrelated default operation against. Either way a binding driving a tester shared
     * across methods should restore the tester's dataset and operations afterward, so a
     * per-method {@code @DbUnitSetup}/{@code @DbUnitPrep} does not carry onto a later method.
     *
     * @return True when {@code @DbUnitSetup} was declared.
     */
    public boolean isSetupDeclared()
    {
        return setupDeclared;
    }

    /**
     * Returns the setup operation.
     *
     * @return The setup operation; {@link DatabaseOperation#CLEAN_INSERT} when no
     *         {@code @DbUnitSetup} was declared.
     */
    public DatabaseOperation getSetUpOperation()
    {
        return setUpOperation;
    }

    /**
     * Returns whether {@code @DbUnitExpected} was declared, switching the test onto the
     * prep/expected path.
     *
     * @return True when {@code @DbUnitExpected} was declared.
     */
    public boolean isExpected()
    {
        return expected;
    }

    /**
     * Returns the resolved, absolute classpath paths of the expected dataset files.
     *
     * @return The expected dataset paths; empty when {@link #isExpected()} is false.
     */
    public String[] getExpectedDataFiles()
    {
        return expectedDataFiles.clone();
    }

    /**
     * Returns the resolved table definitions to verify.
     *
     * @return The table definitions; empty when {@link #isExpected()} is false.
     */
    public VerifyTableDefinition[] getVerifyTableDefinitions()
    {
        return verifyTableDefinitions.clone();
    }

    /**
     * Returns whether {@code @DbUnitTearDown} was declared.
     *
     * <p>On the setup/teardown path a binding consults this to decide whether to call
     * {@code setTearDownOperation} at all: with no {@code @DbUnitTearDown}, {@code onTearDown()}
     * runs with the tester's own pre-configured teardown operation - e.g. one set in a
     * {@code @BeforeEach} method - rather than a forced {@link DatabaseOperation#NONE},
     * mirroring how an undeclared setup operation is handled (see {@link #isSetupDeclared()}).
     * A binding driving a tester shared across methods should restore the operation afterward
     * so a per-method {@code @DbUnitTearDown} does not carry onto a later method. On the
     * prep/expected path the operation is applied regardless, since {@code cleanupData()}
     * always runs there and reads it from the tester lazily.
     *
     * @return True when {@code @DbUnitTearDown} was declared.
     */
    public boolean isTearDownDeclared()
    {
        return tearDownDeclared;
    }

    /**
     * Returns the teardown operation.
     *
     * @return The teardown operation; {@link DatabaseOperation#NONE} when no
     *         {@code @DbUnitTearDown} was declared.
     */
    public DatabaseOperation getTearDownOperation()
    {
        return tearDownOperation;
    }

    /**
     * Returns the resolved {@code DatabaseConfig} properties to apply.
     *
     * @return The properties; empty when none were declared.
     */
    public Properties getDatabaseConfigProperties()
    {
        final Properties copy = new Properties();
        copy.putAll(databaseConfigProperties);
        return copy;
    }

    /**
     * Returns the failure handler to use for verification failures.
     *
     * @return The failure handler, or {@code null} when not set - leaving dbUnit's own
     *         default in effect.
     */
    public FailureHandler getFailureHandler()
    {
        return failureHandler;
    }

    /**
     * Returns whether the connection this executor resolves - for the prep/expected path, the
     * row count check, or parameter injection - is closed after each test.
     *
     * @return True to close the connection after each test.
     */
    public boolean isCloseConnectionAfterTest()
    {
        return closeConnectionAfterTest;
    }

    /**
     * Returns the {@link DatabaseTesterFactory} implementation to use when no tester field is
     * declared.
     *
     * @return The factory class, or {@code null} when not set.
     */
    public Class<? extends DatabaseTesterFactory> getDatabaseTesterFactory()
    {
        return databaseTesterFactory;
    }

    /**
     * Returns the {@link PrepAndExpectedTestCase} implementation to use for the prep/expected
     * path when no test case field is declared.
     *
     * @return The test case class; {@link DefaultPrepAndExpectedTestCase} when not set.
     */
    public Class<? extends PrepAndExpectedTestCase> getPrepAndExpectedTestCaseClass()
    {
        return prepAndExpectedTestCaseClass;
    }

    /**
     * Returns whether {@code @DbUnitRowCountCheck} was declared.
     *
     * <p>A binding must consult this before overriding a resolved {@code RowCountCheck}, to
     * leave the row count check's own {@code DatabaseConfig}/system-property-based resolution
     * untouched when nothing was declared.
     *
     * @return True when {@code @DbUnitRowCountCheck} was declared.
     */
    public boolean isRowCountCheckDeclared()
    {
        return rowCountCheckDeclared;
    }

    /**
     * Returns the resolved {@code @DbUnitRowCountCheck.enabled()} value.
     *
     * @return True to enable the check; meaningful only when {@link #isRowCountCheckDeclared()}
     *         is true.
     */
    public boolean isRowCountCheckEnabled()
    {
        return rowCountCheckEnabled;
    }

    /**
     * Returns the resolved {@code @DbUnitRowCountCheck.exclude()} value.
     *
     * @return The excluded table name patterns; meaningful only when
     *         {@link #isRowCountCheckDeclared()} is true.
     */
    public String[] getRowCountCheckExclude()
    {
        return rowCountCheckExclude.clone();
    }
}
