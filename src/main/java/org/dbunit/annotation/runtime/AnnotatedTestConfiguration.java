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

import java.util.Properties;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;

/**
 * Immutable configuration resolved from one test element's {@code org.dbunit.annotation}
 * instances, ready for {@link AnnotatedTestExecutor} to drive - no dataset has been applied to
 * a database yet, but every dataset path is normalized, every catalog has been read, and every
 * reflective instantiation that can fail (a comparer or factory with no accessible no-arg
 * constructor, conflicting attributes) has already happened.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by a binding such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener}. Building one is
 * framework-free: {@link #from} takes the already-resolved annotation instances, or an
 * {@link AnnotationLookup} that finds them and the classes declaring them (method-level, else
 * class-level - a binding's job, since only it knows how to search for them), rather than a
 * framework's test context.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class AnnotatedTestConfiguration
{
    private final DataFileLoader dataFileLoader;
    private final boolean dataFileLoaderDeclared;
    private final String[] prepDataFiles;
    private final boolean setupDeclared;
    private final DatabaseOperation setUpOperation;
    private final String[] expectedDataFiles;
    // null when the annotations declare none, to be derived from the expected datasets later.
    private final VerifyTableDefinition[] verifyTableDefinitions;
    // the rules of the class-level catalogs, for the tables derived from the expected datasets.
    private final VerifyTableDefinition[] catalogDefinitions;
    private final boolean tearDownDeclared;
    private final DatabaseOperation tearDownOperation;
    private final Properties databaseConfigProperties;
    private final FailureHandler failureHandler;
    private final Class<? extends DatabaseTesterFactory> databaseTesterFactory;
    private final Class<? extends DefaultPrepAndExpectedTestCase> prepAndExpectedTestCaseClass;
    private final boolean rowCountCheckDeclared;
    private final boolean rowCountCheckEnabled;
    private final String[] rowCountCheckExclude;

    private AnnotatedTestConfiguration(final DataFileLoader dataFileLoader,
            final boolean dataFileLoaderDeclared, final String[] prepDataFiles,
            final boolean setupDeclared,
            final DatabaseOperation setUpOperation,
            final String[] expectedDataFiles,
            final VerifyTableDefinition[] verifyTableDefinitions,
            final VerifyTableDefinition[] catalogDefinitions,
            final boolean tearDownDeclared, final DatabaseOperation tearDownOperation,
            final Properties databaseConfigProperties, final FailureHandler failureHandler,
            final Class<? extends DatabaseTesterFactory> databaseTesterFactory,
            final Class<? extends DefaultPrepAndExpectedTestCase> prepAndExpectedTestCaseClass,
            final boolean rowCountCheckDeclared, final boolean rowCountCheckEnabled,
            final String[] rowCountCheckExclude)
    {
        this.dataFileLoader = dataFileLoader;
        this.dataFileLoaderDeclared = dataFileLoaderDeclared;
        this.prepDataFiles = prepDataFiles;
        this.setupDeclared = setupDeclared;
        this.setUpOperation = setUpOperation;
        this.expectedDataFiles = expectedDataFiles;
        this.verifyTableDefinitions = verifyTableDefinitions;
        this.catalogDefinitions = catalogDefinitions;
        this.tearDownDeclared = tearDownDeclared;
        this.tearDownOperation = tearDownOperation;
        this.databaseConfigProperties = databaseConfigProperties;
        this.failureHandler = failureHandler;
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
     * @param setup The resolved {@code @DbUnitSetUpOperation}, or {@code null} if absent.
     * @param expected The resolved {@code @DbUnitExpected}, or {@code null} if absent.
     * @param tearDown The resolved {@code @DbUnitTearDownOperation}, or {@code null} if absent.
     * @param rowCountCheck The resolved {@code @DbUnitRowCountCheck}, or {@code null} if
     *            absent.
     * @return The resolved configuration.
     * @throws IllegalStateException If two mutually exclusive attributes are both set, or if
     *             a named provider, catalog, or comparer class cannot be instantiated.
     */
    public static AnnotatedTestConfiguration from(final Class<?> testClass,
            final DbUnitConfig config, final DbUnitPrep prep, final DbUnitSetUpOperation setup,
            final DbUnitExpected expected, final DbUnitTearDownOperation tearDown,
            final DbUnitRowCountCheck rowCountCheck)
    {
        return from(testClass, testClass, testClass, config, prep, setup, expected, tearDown,
                rowCountCheck);
    }

    /**
     * Resolves a configuration from one test element's annotation instances, resolving a
     * dataset path written relative to a package against the package of the class that
     * declares the annotation naming it. That is the test class itself unless the annotation is
     * inherited from a base class, or sits on an inherited test method, in another package.
     *
     * @param testClass The test class; used in diagnostics.
     * @param prepDeclaringClass The class declaring {@code prep}; used to resolve its
     *            package-relative paths.
     * @param expectedDeclaringClass The class declaring {@code expected}; used to resolve its
     *            package-relative paths.
     * @param config The resolved {@code @DbUnitConfig}, or {@code null} if absent.
     * @param prep The resolved {@code @DbUnitPrep}, or {@code null} if absent.
     * @param setup The resolved {@code @DbUnitSetUpOperation}, or {@code null} if absent.
     * @param expected The resolved {@code @DbUnitExpected}, or {@code null} if absent.
     * @param tearDown The resolved {@code @DbUnitTearDownOperation}, or {@code null} if absent.
     * @param rowCountCheck The resolved {@code @DbUnitRowCountCheck}, or {@code null} if
     *            absent.
     * @return The resolved configuration.
     * @throws IllegalStateException If two mutually exclusive attributes are both set, or if
     *             a named provider, catalog, or comparer class cannot be instantiated.
     */
    public static AnnotatedTestConfiguration from(final Class<?> testClass,
            final Class<?> prepDeclaringClass, final Class<?> expectedDeclaringClass,
            final DbUnitConfig config, final DbUnitPrep prep, final DbUnitSetUpOperation setup,
            final DbUnitExpected expected, final DbUnitTearDownOperation tearDown,
            final DbUnitRowCountCheck rowCountCheck)
    {
        return from(prepDeclaringClass, expectedDeclaringClass, MergedDbUnitConfig.ofOne(config),
                prep, setup, expected, tearDown, rowCountCheck, new DataFileLoaderInstances());
    }

    private static AnnotatedTestConfiguration from(final Class<?> prepDeclaringClass,
            final Class<?> expectedDeclaringClass, final MergedDbUnitConfig config,
            final DbUnitPrep prep, final DbUnitSetUpOperation setup,
            final DbUnitExpected expected, final DbUnitTearDownOperation tearDown,
            final DbUnitRowCountCheck rowCountCheck, final DataFileLoaderInstances dataFileLoaders)
    {
        final String dataSetBaseDir = config.getDataSetBaseDir();
        final DataSetPathsResolver pathsResolver = new DataSetPathsResolver();

        final Class<? extends DataFileLoader> declaredDataFileLoaderClass =
                config.getDataFileLoader();
        final boolean dataFileLoaderDeclared = declaredDataFileLoaderClass != null;
        final Class<? extends DataFileLoader> dataFileLoaderClass = dataFileLoaderDeclared
                ? declaredDataFileLoaderClass : FileExtensionDataFileLoader.class;
        final DataFileLoader dataFileLoader = dataFileLoaders.get(dataFileLoaderClass);

        final String[] prepDataFiles = pathsResolver.resolve(prepDeclaringClass, dataSetBaseDir,
                prep == null ? null : prep.value());
        final boolean setupDeclared = setup != null;
        final DatabaseOperation setUpOperation = setup == null ? DatabaseOperation.CLEAN_INSERT
                : setup.value().toDatabaseOperation();
        final boolean tearDownDeclared = tearDown != null;
        final DatabaseOperation tearDownOperation =
                tearDown == null ? DatabaseOperation.NONE
                        : tearDown.value().toDatabaseOperation();

        final boolean hasExpected = expected != null;
        final String[] expectedDataFiles = !hasExpected ? new String[0]
                : pathsResolver.resolve(expectedDeclaringClass, dataSetBaseDir,
                        expected.value());
        final VerifyTableDefinitionResolver verifyResolver = new VerifyTableDefinitionResolver();
        final VerifyTableDefinition[] verifyTableDefinitions = !hasExpected
                ? new VerifyTableDefinition[0]
                : verifyResolver.resolveDeclared(config.getVerifyDefinitions(), expected,
                        expectedDataFiles).orElse(null);
        final VerifyTableDefinition[] catalogDefinitions = !hasExpected
                ? new VerifyTableDefinition[0]
                : verifyResolver.catalogDefinitions(config.getVerifyDefinitions());

        final Properties databaseConfigProperties =
                new DatabaseConfigPropertiesResolver().resolve(config.getProperties());
        final Class<? extends FailureHandler> failureHandlerClass = config.getFailureHandler();
        final FailureHandler failureHandler = failureHandlerClass == null ? null
                : new ReflectiveInstantiation().instantiate(failureHandlerClass,
                        "DbUnitConfig.failureHandler");
        final Class<? extends DatabaseTesterFactory> databaseTesterFactory =
                config.getDatabaseTesterFactory();
        final Class<? extends DefaultPrepAndExpectedTestCase> prepAndExpectedTestCaseClass =
                config.getPrepAndExpectedTestCase();

        final boolean rowCountCheckDeclared = rowCountCheck != null;
        final boolean rowCountCheckEnabled =
                rowCountCheckDeclared && rowCountCheck.enabled();
        final String[] rowCountCheckExclude =
                rowCountCheckDeclared ? rowCountCheck.exclude() : new String[0];

        return new AnnotatedTestConfiguration(dataFileLoader, dataFileLoaderDeclared,
                prepDataFiles, setupDeclared, setUpOperation, expectedDataFiles,
                verifyTableDefinitions, catalogDefinitions, tearDownDeclared, tearDownOperation,
                databaseConfigProperties, failureHandler, databaseTesterFactory,
                prepAndExpectedTestCaseClass, rowCountCheckDeclared, rowCountCheckEnabled,
                rowCountCheckExclude);
    }

    /**
     * Resolves a configuration from the annotations a binding's {@link AnnotationLookup} finds
     * for one test - the six {@code org.dbunit.annotation} annotations that configure a test,
     * method-level before class-level - so a binding supplies only its search strategy. The
     * nearest declaration of an annotation wins whole, except {@link DbUnitConfig}: every
     * declaration in scope is merged attribute by attribute - see {@link MergedDbUnitConfig}. A
     * dataset path written relative to a package is resolved against the package of the class
     * the lookup reports as declaring the annotation naming it - see
     * {@link AnnotationLookup#findDeclaringClass(Class)}.
     *
     * <p>The data file loader is built for this configuration alone. A binding that wants the
     * tests of a scope to share one loader of each caching class calls
     * {@link #from(Class, AnnotationLookup, DataFileLoaderInstances)} instead.
     *
     * @param testClass The test class; used in diagnostics.
     * @param lookup Finds the annotations that apply to the test, and the classes declaring them.
     * @return The resolved configuration.
     * @throws IllegalStateException If two mutually exclusive attributes are both set, or if
     *             a named provider, catalog, or comparer class cannot be instantiated.
     */
    public static AnnotatedTestConfiguration from(final Class<?> testClass,
            final AnnotationLookup lookup)
    {
        return from(testClass, lookup, new DataFileLoaderInstances());
    }

    /**
     * Resolves a configuration as {@link #from(Class, AnnotationLookup)} does, taking the data
     * file loader from the given instances: the one of the loader class the merged
     * {@code @DbUnitConfig} names, or of {@link FileExtensionDataFileLoader} when it names none.
     * Every configuration resolved with the same instances therefore uses the same loader of a
     * caching class, built on the first request, and a loader of its own of any other class.
     *
     * @param testClass The test class; used in diagnostics.
     * @param lookup Finds the annotations that apply to the test, and the classes declaring them.
     * @param dataFileLoaders The loaders of the binding's scope: one for each caching loader
     *            class, a new one for each request of any other.
     * @return The resolved configuration.
     * @throws IllegalStateException If two mutually exclusive attributes are both set, or if
     *             a named provider, catalog, or comparer class cannot be instantiated.
     */
    public static AnnotatedTestConfiguration from(final Class<?> testClass,
            final AnnotationLookup lookup, final DataFileLoaderInstances dataFileLoaders)
    {
        final MergedDbUnitConfig config =
                MergedDbUnitConfig.of(lookup.findAll(DbUnitConfig.class));
        final DbUnitPrep prep = lookup.find(DbUnitPrep.class);
        final DbUnitSetUpOperation setup = lookup.find(DbUnitSetUpOperation.class);
        final DbUnitExpected expected = lookup.find(DbUnitExpected.class);
        final DbUnitTearDownOperation tearDown = lookup.find(DbUnitTearDownOperation.class);
        final DbUnitRowCountCheck rowCountCheck = lookup.find(DbUnitRowCountCheck.class);
        final Class<?> prepDeclaringClass = lookup.findDeclaringClass(DbUnitPrep.class);
        final Class<?> expectedDeclaringClass = lookup.findDeclaringClass(DbUnitExpected.class);
        return from(prepDeclaringClass, expectedDeclaringClass, config, prep, setup, expected,
                tearDown, rowCountCheck, dataFileLoaders);
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
     * Returns whether a {@code @DbUnitConfig} declares the data file loader, rather than the
     * default {@link FileExtensionDataFileLoader} being used for want of one.
     *
     * @return True when a declaration names the loader.
     */
    public boolean isDataFileLoaderDeclared()
    {
        return dataFileLoaderDeclared;
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
     * Returns whether {@code @DbUnitSetUpOperation} was declared, which is what replaces the
     * tester's own setup operation with {@link #getSetUpOperation()} for the test case that runs
     * the test. Without it the test case uses the tester's setup operation, such as one a
     * {@code @BeforeEach} method set, which can be one {@code DbUnitOperation} cannot express:
     * {@code @DbUnitPrep} names only the dataset. The tester itself is never written.
     *
     * <p>With {@code @DbUnitSetUpOperation} declared but no {@code @DbUnitPrep}, the operation
     * runs over no data, as the dataset the tester holds is never read, so only an operation that
     * sources its own data changes anything.
     *
     * @return True when {@code @DbUnitSetUpOperation} was declared.
     */
    public boolean isSetupDeclared()
    {
        return setupDeclared;
    }

    /**
     * Returns the setup operation.
     *
     * @return The setup operation {@code @DbUnitSetUpOperation} declared;
     *         {@link DatabaseOperation#CLEAN_INSERT} when none was, which applies only when
     *         {@link #isSetupDeclared()}.
     */
    public DatabaseOperation getSetUpOperation()
    {
        return setUpOperation;
    }

    /**
     * Returns the resolved, absolute classpath paths of the expected dataset files.
     *
     * @return The expected dataset paths; empty when no {@code @DbUnitExpected} was declared.
     */
    public String[] getExpectedDataFiles()
    {
        return expectedDataFiles.clone();
    }

    /**
     * Returns the resolved table definitions to verify, reading the expected datasets with
     * {@link #getDataFileLoader()} when the annotations declare none - see
     * {@link #getVerifyTableDefinitions(DataFileLoader)}.
     *
     * @return The table definitions; empty when no {@code @DbUnitExpected} was declared.
     */
    public VerifyTableDefinition[] getVerifyTableDefinitions()
    {
        return getVerifyTableDefinitions(dataFileLoader);
    }

    /**
     * Returns whether the annotations declare no table definitions to verify, so that the
     * default of one per table in the expected datasets applies: either derived by reading the
     * expected datasets - see {@link #getVerifyTableDefinitions(DataFileLoader)} - or, by a
     * binding whose {@code PrepAndExpectedTestCase} loads those datasets itself, from the
     * dataset it has loaded, so the files are not parsed twice. In both, the rules of a table
     * are its definition in the class-level catalogs - see {@link #getCatalogDefinitions()} -
     * and the default ones for a table they do not define.
     *
     * @return True when the table definitions are to be derived from the expected datasets;
     *         false when the annotations declare them, or no {@code @DbUnitExpected} was
     *         declared.
     */
    public boolean derivesVerifyTableDefinitions()
    {
        return verifyTableDefinitions == null;
    }

    /**
     * Returns the resolved table definitions to verify. When the annotations declare none, the
     * default is one definition per table in the expected datasets, read here with
     * {@code expectedDataLoader} rather than at resolution time: the loader that matters is the
     * one the {@code PrepAndExpectedTestCase} will compare those datasets with, which for an
     * injected instance keeping a loader of its own is not {@link #getDataFileLoader()}.
     *
     * @param expectedDataLoader The loader to read the expected datasets with when the
     *            definitions must be derived from them.
     * @return The table definitions; empty when no {@code @DbUnitExpected} was declared.
     */
    public VerifyTableDefinition[] getVerifyTableDefinitions(
            final DataFileLoader expectedDataLoader)
    {
        if (verifyTableDefinitions == null)
        {
            return new VerifyTableDefinitionResolver().deriveFromExpectedDatasets(
                    expectedDataLoader, expectedDataFiles, catalogDefinitions);
        }
        return verifyTableDefinitions.clone();
    }

    /**
     * Returns the definitions of the class-level {@code @DbUnitConfig(verifyDefinitions)}
     * catalogs, the rules of the tables derived from the expected datasets when
     * {@link #derivesVerifyTableDefinitions()}: a table a catalog defines is verified with its
     * definition, any other with the default rules.
     *
     * @return The catalogs' definitions; empty when the test declares no catalog, or
     *         no {@code @DbUnitExpected} was declared.
     */
    public VerifyTableDefinition[] getCatalogDefinitions()
    {
        return catalogDefinitions.clone();
    }

    /**
     * Returns whether {@code @DbUnitTearDownOperation} was declared. Without it the test case
     * that runs the test uses the tester's own teardown operation, such as one set in a
     * {@code @BeforeEach} method, rather than a forced {@link DatabaseOperation#NONE}, as for an
     * undeclared setup operation (see {@link #isSetupDeclared()}). The tester itself is never
     * written.
     *
     * @return True when {@code @DbUnitTearDownOperation} was declared.
     */
    public boolean isTearDownDeclared()
    {
        return tearDownDeclared;
    }

    /**
     * Returns the teardown operation.
     *
     * @return The teardown operation; {@link DatabaseOperation#NONE} when no
     *         {@code @DbUnitTearDownOperation} was declared.
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
     * Returns the {@link PrepAndExpectedTestCase} implementation to build for an
     * annotation-driven test when no test case field is declared.
     *
     * @return The test case class; {@link DefaultPrepAndExpectedTestCase} when not set.
     */
    public Class<? extends DefaultPrepAndExpectedTestCase> getPrepAndExpectedTestCaseClass()
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
