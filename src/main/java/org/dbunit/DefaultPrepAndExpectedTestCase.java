/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
package org.dbunit;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.dbunit.assertion.DefaultFailureHandler;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConfigOverrides;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.IResultSetTableFactory;
import org.dbunit.database.connection.AutoCommitOffWarning;
import org.dbunit.database.connection.ConnectionOwnership;
import org.dbunit.database.connection.LeftOpenConnectionWarning;
import org.dbunit.database.connection.TestScopedConnection;
import org.dbunit.database.rowcount.RowCountCheck;
import org.dbunit.database.rowcount.RowCountChecker;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.ColumnFilterTable;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.FilteredTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.SortedTable;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.TableFormatter;
import org.dbunit.util.fileloader.DataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test case base class supporting prep data and expected data. Prep data is the
 * data needed for the test to run. Expected data is the data needed to compare
 * if the test ran successfully.
 * <p>
 * configureTest(), setupData(), verifyData(), and cleanupData() share one
 * {@link org.dbunit.database.IDatabaseConnection} for a test's lifecycle,
 * acquired lazily on first use and closed once by cleanupData(), instead of
 * each acquiring (and often closing) its own. Calling any of those methods
 * without an eventual cleanupData() call - e.g. testing them individually
 * rather than through preTest()/postTest() - leaves that connection open.
 * <p>
 * If databaseTester is configured with a
 * {@link org.dbunit.database.CachingConnectionProvider} shared across test
 * methods, set {@link #setCloseConnectionAfterTest(boolean)} to false so
 * cleanupData() does not close a connection other tests still expect to
 * reuse; the provider's owner is then responsible for closing it once,
 * itself, when the whole run finishes. Leaving the connection open, which a
 * no-op listener on the databaseTester does too, requires a databaseTester
 * that hands out the same connection on repeated calls, as one over a
 * CachingConnectionProvider or over one fixed connection does. With one that
 * hands out a new connection each time, such as a
 * {@link JdbcDatabaseTester} without a CachingConnectionProvider or a
 * {@link DataSourceDatabaseTester} over a connection pool, nothing closes the
 * connection left open. An instance used for one test leaves one open after
 * it. An instance reused across tests keeps one for its next test and leaves
 * another open each time a step fails, because a failing step makes this
 * class forget the connection so that the next step acquires another.
 * This class logs a warning when it detects a {@link JdbcDatabaseTester}
 * without a CachingConnectionProvider in that situation.
 * <p>
 * The databaseTester's own {@link IOperationListener} is told about the
 * connection each of setupData()'s and cleanupData()'s operations retrieves,
 * through {@link IOperationListener#connectionRetrieved}, so a listener that
 * configures every connection runs here as it does when the tester drives
 * onSetup() and onTearDown() itself. It is never told the operations finished,
 * as this class decides when the shared connection closes.
 * <p>
 * The connection must be in autocommit mode. The setup and teardown
 * operations run here (CLEAN_INSERT, DELETE_ALL, ...) do not manage a
 * transaction of their own, and {@link org.dbunit.operation.TransactionOperation}
 * refuses a connection whose autocommit is already off, so on a
 * non-autocommit connection the prep and teardown writes are never
 * committed: invisible to any other connection, and - with
 * {@link #setCloseConnectionAfterTest(boolean)} false - held as locks on a
 * connection the database's idle-in-transaction timeout may terminate
 * mid-run. This class logs a warning when it detects such a connection.
 * <p>
 * The {@code verifyData()} method hands assertion failures to
 * {@link org.dbunit.assertion.DefaultFailureHandler} by default, which throws
 * on the first mismatch found. Set {@link #setFailureHandler(FailureHandler)}
 * to, for example, a {@link org.dbunit.assertion.DiffCollectingFailureHandler}
 * to collect every {@link org.dbunit.assertion.Difference} instead; that is
 * not the default since most tests want to keep failing fast.
 * <p>
 * Not thread-safe. An instance holds the state of one test at a time: its
 * datasets, verify table definitions, shared connection and row count
 * baseline. Use one instance per concurrently running test, for example a
 * Spring prototype bean, or turn off parallel execution for tests that share
 * an instance, such as one in a static {@code @DbUnitTestCase} field.
 * Otherwise concurrent tests overwrite each other's datasets and close each
 * other's connection.
 *
 * @see "org.dbunit.DefaultPrepAndExpectedTestCaseDiIT, a composition-based (DI) usage example in the test sources"
 * @see "org.dbunit.DefaultPrepAndExpectedTestCaseExtIT, an inheritance-based usage example in the test sources"
 *
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public class DefaultPrepAndExpectedTestCase extends DBTestCase
        implements PrepAndExpectedTestCase
{
    private static final Logger log = LoggerFactory.getLogger(DefaultPrepAndExpectedTestCase.class);

    private static final String DATABASE_TESTER_IS_NULL_MSG =
            "databaseTester is null; must configure or set it first";

    /** Message prefix used for wrapped test failures. */
    public static final String TEST_ERROR_MSG = "DbUnit test error.";

    private IDatabaseTester databaseTester;
    private DataFileLoader dataFileLoader;

    /**
     * Whether lookupFeatureValue() and cleanupData() close the connection
     * they are done with; false when databaseTester shares a
     * CachingConnectionProvider across test methods and this instance must
     * not close a connection other tests still expect to reuse.
     *
     * @since 3.4.0
     */
    private boolean closeConnectionAfterTest = true;

    /**
     * The operation setupData() runs, instead of the databaseTester's own; null, the default,
     * runs the databaseTester's.
     *
     * @since 3.6.0
     */
    private DatabaseOperation setUpOperation;

    /**
     * The operation cleanupData() runs, instead of the databaseTester's own; null, the default,
     * runs the databaseTester's.
     *
     * @since 3.6.0
     */
    private DatabaseOperation tearDownOperation;

    // per test data
    private IDataSet prepDataSet = new DefaultDataSet();
    private IDataSet expectedDataSet = new DefaultDataSet();
    private VerifyTableDefinition[] verifyTableDefs = {};

    /**
     * Decides whether this instance may close the connection it shares across a test's
     * lifecycle. It reads {@link #closeConnectionAfterTest} and {@link #getOwnershipListener()}
     * each time it is asked, as both can still change after construction.
     *
     * @since 3.6.0
     */
    private final ConnectionOwnership connectionOwnership = new ConnectionOwnership(
            () -> closeConnectionAfterTest, this::getOwnershipListener);

    /**
     * Connection shared by setupData()/verifyData()/cleanupData() for one
     * test's lifecycle instead of each acquiring (and often closing) its own:
     * acquired lazily on first use from {@link #getConnection()}, closed once
     * by cleanupData() unless {@link #closeConnectionAfterTest} is false or
     * the tester's or this instance's operation listener is the no-op one,
     * and re-acquired if the pool or server closed it between reused test
     * methods.
     *
     * @since 3.6.0
     */
    private final TestScopedConnection reusableConnectionHolder = newReusableConnectionHolder();

    /**
     * Builds {@link #reusableConnectionHolder}. Its {@link ConnectionOwnership}
     * reads {@link #closeConnectionAfterTest} and {@link #getOwnershipListener()}
     * fresh at release time. Since 3.6.0 a listener that is
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER} keeps
     * cleanupData() from closing the connection even when
     * {@link #closeConnectionAfterTest} is true - the established signal that the
     * connection is owned elsewhere, now honored on this path too and not only
     * by the annotation runtime. On each connection it acquires, it logs the warnings of
     * {@link AutoCommitOffWarning} and {@link LeftOpenConnectionWarning}, the latter reading the
     * databaseTester when it runs, as the databaseTester can change between tests.
     *
     * @return The holder.
     */
    private TestScopedConnection newReusableConnectionHolder()
    {
        final LeftOpenConnectionWarning leftOpenConnectionWarning =
                new LeftOpenConnectionWarning(connectionOwnership, () -> databaseTester);
        return new TestScopedConnection(this::acquireSharedConnection, connectionOwnership,
                new AutoCommitOffWarning().andThen(leftOpenConnectionWarning));
    }

    /**
     * Acquires the connection {@link #reusableConnectionHolder} shares: the databaseTester's
     * connection with {@link #setUpDatabaseConfig(DatabaseConfig)} applied to it, as
     * {@link #getConnection()} does. That method cannot release a connection whose configuration
     * fails to apply, for example because a {@code databaseConfigProperties} value is invalid, so
     * one the databaseTester hands out afresh on each call would be referenced by nothing and
     * leak. This releases it, honoring who owns it, before the failure propagates.
     *
     * @return The connection, configured.
     * @throws Exception If the databaseTester cannot supply a connection, or applying the
     *             configuration fails.
     */
    private IDatabaseConnection acquireSharedConnection() throws Exception
    {
        requireDatabaseTester();

        final IDatabaseConnection connection = databaseTester.getConnection();
        if (connection == null)
        {
            throw new IllegalStateException("databaseTester.getConnection() returned null; a test"
                    + " lifecycle needs a connection to run its operations on.");
        }
        try
        {
            setUpDatabaseConfig(connection.getConfig());
        } catch (final Throwable t)
        {
            reusableConnectionHolder.adopt(connection);
            closeReusableConnectionSuppressing(t);
            throw t;
        }
        return connection;
    }

    /**
     * Returns the listener {@link ConnectionOwnership} consults to decide whether this
     * instance may close the shared connection. The no-op listener is the signal that a
     * connection is managed elsewhere, and a caller sets it on whichever it controls: the
     * tester it hands this instance, or - for a subclass - this instance's own
     * {@link #getOperationListener()}. Either one being the no-op listener therefore wins;
     * otherwise this instance's own listener is returned.
     *
     * @return The no-op listener, if either listener is it; otherwise this instance's own
     *         listener.
     */
    private IOperationListener getOwnershipListener()
    {
        final IOperationListener ownListener = getOperationListener();
        if (databaseTester == null)
        {
            return ownListener;
        }

        final IOperationListener testerListener = databaseTester.getOperationListener();
        if (testerListener == IOperationListener.NO_OP_OPERATION_LISTENER)
        {
            return testerListener;
        }
        return ownListener;
    }

    /**
     * isCaseSensitiveTableNames as resolved by configureTest(), cached so
     * cleanupData() does not need a second connection just to re-read this
     * same DatabaseConfig feature flag.
     *
     * @since 3.4.0
     */
    private Boolean cachedIsCaseSensitiveTableNames;

    /**
     * Manages the row count check baseline used by preTest() and cleanupData() to detect a
     * table the test left dirty - either one it should have cleaned up and did not, or a
     * reference table it wrongly cleaned.
     *
     * @since 3.6.0
     */
    private final RowCountChecker rowCountChecker = new RowCountChecker();

    private ExpectedDataSetAndVerifyTableDefinitionVerifier expectedDataSetAndVerifyTableDefinitionVerifier =
            new DefaultExpectedDataSetAndVerifyTableDefinitionVerifier();

    /**
     * FailureHandler for verifyData()'s assertion failures. Null (the
     * default) leaves compareData() using
     * {@link org.dbunit.Assertion#assertWithValueComparer(ITable, ITable, Column[], ValueComparer, Map)}'s
     * own {@link org.dbunit.assertion.DefaultFailureHandler}, configured with
     * the additionalColumnInfo computed by {@link #makeAdditionalColumnInfo};
     * that default is intentional so most tests keep failing fast on the
     * first mismatch. Set this, for example to a
     * {@link org.dbunit.assertion.DiffCollectingFailureHandler}, only when a
     * test needs to collect every {@link org.dbunit.assertion.Difference}
     * instead.
     *
     * @since 3.5.0
     */
    private FailureHandler failureHandler;

    /**
     * DatabaseConfig property name/value pairs applied to the connection shared by
     * setupData(), verifyData() and cleanupData(), every time {@link #getConnection()}
     * resolves it - see {@link #setUpDatabaseConfig(DatabaseConfig)}. Null (the default)
     * applies none.
     *
     * @since 3.6.0
     */
    private Properties databaseConfigProperties;

    /**
     * Applies {@link #databaseConfigProperties} to a connection's {@link DatabaseConfig} and
     * puts back what they replaced when this test's lifecycle ends, so a connection kept past
     * the test - {@link #closeConnectionAfterTest} false, or the no-op listener - carries none
     * of this test's values into whatever uses it next.
     *
     * @since 3.6.0
     */
    private DatabaseConfigOverrides databaseConfigOverrides =
            new DatabaseConfigOverrides(new Properties());

    final TableFormatter tableFormatter = new TableFormatter();

    /** Create new instance. */
    public DefaultPrepAndExpectedTestCase()
    {
    }

    /**
     * Create new instance with specified dataFileLoader and databaseTester.
     *
     * @param dataFileLoader
     *            Load to use for loading the data files.
     * @param databaseTester
     *            Tester to use for database manipulation.
     */
    public DefaultPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
            final IDatabaseTester databaseTester)
    {
        this.dataFileLoader = dataFileLoader;
        this.databaseTester = databaseTester;
    }

    /**
     * Create new instance with specified dataFileLoader and databaseTester.
     *
     * @param dataFileLoader
     *            Load to use for loading the data files.
     * @param databaseTester
     *            Tester to use for database manipulation.
     * @param closeConnectionAfterTest
     *            Whether or not to close the database connection after each test.
     *
     * @since 3.4.0
     */
    public DefaultPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
            final IDatabaseTester databaseTester,
            final boolean closeConnectionAfterTest)
    {
        this.dataFileLoader = dataFileLoader;
        this.databaseTester = databaseTester;
        this.closeConnectionAfterTest = closeConnectionAfterTest;
    }

    /**
     * Create new instance with specified test case name.
     *
     * @param name
     *            The test case name.
     */
    public DefaultPrepAndExpectedTestCase(final String name)
    {
        super(name);
    }

    /**
     * {@inheritDoc} This implementation returns the databaseTester set by the
     * test.
     */
    @Override
    public IDatabaseTester newDatabaseTester() throws Exception
    {
        // questionable, but there is not a "setter" for any parent...
        return databaseTester;
    }

    /**
     * {@inheritDoc} Returns the prep dataset.
     */
    @Override
    public IDataSet getDataSet() throws Exception
    {
        return prepDataSet;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Executes against the connection shared with setupData(), verifyData()
     * and cleanupData() for this test's lifecycle rather than a fresh one,
     * and leaves it open; cleanupData() closes it. See #800.
     * <p>
     * A null argument means none. When this fails, for example because a data
     * file is missing or invalid, this instance is left with no prep data,
     * expected data or verify table definitions, so a cleanupData() that
     * follows cleans nothing left over from an earlier test. The shared
     * connection it acquired is released too; one the caller had already
     * resolved, for example by injecting it into a test method parameter, is
     * left open for the caller.
     */
    @Override
    public void configureTest(
            final VerifyTableDefinition[] verifyTableDefinitions,
            final String[] prepDataFiles, final String[] expectedDataFiles)
            throws Exception
    {
        requireDatabaseTester();
        log.debug("configureTest: saving instance variables");

        configuringTest(() -> configure(verifyTableDefinitions, prepDataFiles,
                expectedDataFiles));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Honors {@link PrepAndExpectedTestData#verifyingExpectedTables(String[], String[])}: one
     * {@link VerifyTableDefinition} per table of the expected dataset this loads, the one the
     * bundle defines for the table when it does and a default one otherwise, so each expected
     * data file is parsed once. A subclass that overrides
     * {@link #configureTest(VerifyTableDefinition[], String[], String[])} may expect the
     * definitions it is given, so for it they are derived by loading the expected data files
     * first, and passed on. Fails and cleans up as {@code configureTest} does.
     */
    @Override
    public void configureTest(final PrepAndExpectedTestData testData) throws Exception
    {
        if (testData.isVerifyingEveryExpectedTable())
        {
            configureVerifyingEveryExpectedTable(testData.getPrepDataFiles(),
                    testData.getExpectedDataFiles(), testData.getVerifyTableDefinitions());
        } else
        {
            PrepAndExpectedTestCase.super.configureTest(testData);
        }
    }

    /**
     * Configures the test to verify every table of the expected data files, each with its
     * definition from {@code definitions} when that has one and a default one otherwise.
     */
    private void configureVerifyingEveryExpectedTable(final String[] prepDataFiles,
            final String[] expectedDataFiles, final VerifyTableDefinition[] definitions)
            throws Exception
    {
        if (isOverriddenBySubclass("configureTest", VerifyTableDefinition[].class,
                String[].class, String[].class))
        {
            configureTest(deriveVerifyTableDefinitions(expectedDataFiles, definitions),
                    prepDataFiles, expectedDataFiles);
            return;
        }

        configuringTest(() ->
        {
            configureTest(new VerifyTableDefinition[0], prepDataFiles, expectedDataFiles);
            this.verifyTableDefs =
                    makeVerifyTableDefinitions(expectedDataSet.getTableNames(), definitions);
        });
    }

    /**
     * Loads the prep and expected datasets, combined by the shared connection's case
     * sensitivity, and keeps them with the verify table definitions for this test.
     */
    private void configure(final VerifyTableDefinition[] verifyTableDefinitions,
            final String[] prepDataFiles, final String[] expectedDataFiles) throws Exception
    {
        final boolean isCaseSensitiveTableNames = lookupFeatureValue(
                DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES);
        log.debug("configureTest: using case sensitive table names={}",
                isCaseSensitiveTableNames);

        final IDataSet newPrepDataSet = makeCompositeDataSet(prepDataFiles,
                "prep", isCaseSensitiveTableNames);
        final IDataSet newExpectedDataSet = makeCompositeDataSet(
                expectedDataFiles, "expected", isCaseSensitiveTableNames);

        this.cachedIsCaseSensitiveTableNames = isCaseSensitiveTableNames;
        this.prepDataSet = newPrepDataSet;
        this.expectedDataSet = newExpectedDataSet;
        this.verifyTableDefs = verifyTableDefinitions == null
                ? new VerifyTableDefinition[0]
                : verifyTableDefinitions;
    }

    /**
     * Runs a step that configures the test, emptying the per-test data when it fails - see
     * {@link #clearPerTestData()} - and releasing the shared connection as
     * {@link #releasingIfAcquiredOnFailure(LifecycleStep)} does. Both are idempotent, so a step
     * that calls configureTest(), which runs through here too, cleans up the same way.
     */
    private void configuringTest(final LifecycleStep step) throws Exception
    {
        try
        {
            releasingIfAcquiredOnFailure(step);
        } catch (final Throwable t)
        {
            clearPerTestData();
            throw t;
        }
    }

    /**
     * Runs a lifecycle step, and when it fails, an {@link Error} included, releases the shared
     * connection if the step is what acquired it, attaching a close failure to the step's
     * failure: no cleanupData() follows a step that failed before setupData(), so nothing else
     * would release it. A connection already acquired before the step, for example by injecting
     * it into a test method parameter, is left open for the caller.
     */
    private void releasingIfAcquiredOnFailure(final LifecycleStep step) throws Exception
    {
        final boolean acquiredConnectionHere = reusableConnectionHolder.peekConnection() == null;
        try
        {
            step.run();
        } catch (final Throwable t)
        {
            if (acquiredConnectionHere)
            {
                closeReusableConnectionSuppressing(t);
            }
            throw t;
        }
    }

    /**
     * One step of this test's lifecycle that may acquire the shared connection.
     */
    @FunctionalInterface
    private interface LifecycleStep
    {
        void run() throws Exception;
    }

    /**
     * Makes one {@link VerifyTableDefinition} per table of the expected data files, read with
     * the dataFileLoader, in first-seen order. Reads the files only to list their tables, for a
     * subclass that expects the definitions
     * {@link #configureTest(VerifyTableDefinition[], String[], String[])} is given.
     */
    private VerifyTableDefinition[] deriveVerifyTableDefinitions(
            final String[] expectedDataFiles, final VerifyTableDefinition[] definitions)
            throws DataSetException
    {
        final Set<String> tableNames = new LinkedHashSet<>();
        for (final String path : expectedDataFiles)
        {
            tableNames.addAll(Arrays.asList(dataFileLoader.load(path).getTableNames()));
        }
        return makeVerifyTableDefinitions(tableNames.toArray(new String[0]), definitions);
    }

    /**
     * Makes one {@link VerifyTableDefinition} per table: the one {@code definitions} holds for
     * the table, matched by name ignoring case, and a default one for the others.
     */
    private VerifyTableDefinition[] makeVerifyTableDefinitions(final String[] tableNames,
            final VerifyTableDefinition[] definitions)
    {
        final VerifyTableDefinition[] made = new VerifyTableDefinition[tableNames.length];
        for (int i = 0; i < tableNames.length; i++)
        {
            made[i] = definitionFor(tableNames[i], definitions);
        }
        return made;
    }

    private static VerifyTableDefinition definitionFor(final String tableName,
            final VerifyTableDefinition[] definitions)
    {
        for (final VerifyTableDefinition definition : definitions)
        {
            if (definition.getTableName().equalsIgnoreCase(tableName))
            {
                return definition;
            }
        }
        return new VerifyTableDefinition(tableName, (String[]) null);
    }

    /**
     * Empties the per-test data, so that a cleanupData() following a failed
     * configureTest() runs against nothing rather than against a mix of the
     * new prep data and the previous test's expected data.
     */
    private void clearPerTestData()
    {
        this.prepDataSet = new DefaultDataSet();
        this.expectedDataSet = new DefaultDataSet();
        this.verifyTableDefs = new VerifyTableDefinition[0];
    }

    /**
     * {@inheritDoc} Applies {@link #databaseConfigProperties}, when set - the composition-based
     * equivalent of overriding this method, for a caller that cannot subclass to do so directly
     * (e.g. {@code org.dbunit.annotation}'s {@code @DbUnitProperty}). The values replaced are
     * put back when this test's lifecycle ends - see {@link #cleanupData()}.
     *
     * <p>The lifecycle retrieves its connection several times, and each retrieval calls this
     * method. The values are applied once per {@link DatabaseConfig} until they are put back, and
     * a later call does nothing, so a property set on the {@link DatabaseConfig} directly in
     * between is not overwritten. The exception is a database tester with an operation listener
     * of its own, which may change any of the values at each set up and tear down operation: the
     * values are applied again directly after it, so they still win.
     *
     * <p><strong>Note:</strong> a subclass overriding this method must call
     * {@code super.setUpDatabaseConfig(config)} to keep
     * {@link #setDatabaseConfigProperties(java.util.Properties)} - and therefore
     * {@code @DbUnitProperty} on the annotation-driven path - working.
     *
     * @throws IllegalStateException If a property value is invalid for its target
     *             {@link DatabaseConfig} entry.
     */
    @Override
    protected void setUpDatabaseConfig(final DatabaseConfig config)
    {
        if (databaseConfigOverrides.hasAppliedTo(config))
        {
            return;
        }

        applyDatabaseConfigProperties(config);
    }

    /**
     * Applies {@link #databaseConfigProperties} to a connection's {@link DatabaseConfig},
     * whether or not they are already in force, remembering what they replace.
     *
     * @param config The configuration to apply them to.
     * @throws IllegalStateException If a property value is invalid for its target
     *             {@link DatabaseConfig} entry.
     */
    private void applyDatabaseConfigProperties(final DatabaseConfig config)
    {
        try
        {
            databaseConfigOverrides.applyTo(config);
        } catch (final DatabaseUnitException e)
        {
            throw new IllegalStateException("Failed to apply a databaseConfigProperties value.",
                    e);
        }
    }

    /**
     * Reads a feature of the shared connection's {@link DatabaseConfig}, acquiring the
     * connection on first use. Its callers, configureTest() and cleanupData(), release that
     * connection when they fail.
     */
    private boolean lookupFeatureValue(final String featureName)
            throws Exception
    {
        final IDatabaseConnection reusableConnection = getReusableConnection();
        final DatabaseConfig config = reusableConnection.getConfig();
        return config.getFeature(featureName);
    }

    /**
     * Return the connection shared by lookupFeatureValue(), setupData(),
     * verifyData() and cleanupData() for the current test's lifecycle,
     * acquiring it on first use instead of a fresh connection at each step.
     * <p>
     * When {@link #closeConnectionAfterTest} is false this connection is kept
     * across test methods, where the connection pool or the database server can
     * close it between tests - a pool max-lifetime or reap, a bounced
     * application context, a
     * {@link org.dbunit.database.CachingConnectionProvider#close()}. A closed
     * one is discarded before it is handed back, so the next call re-acquires
     * from databaseTester - letting a
     * {@link org.dbunit.database.CachingConnectionProvider} behind it supply a
     * live replacement - rather than this instance reusing a connection every
     * later lifecycle step would only fail on.
     *
     * @return The shared connection.
     * @throws Exception On dbUnit errors.
     * @since 3.4.0
     */
    public IDatabaseConnection getReusableConnection() throws Exception
    {
        final IDatabaseConnection connection = reusableConnectionHolder.getConnection();
        applyDatabaseConfigPropertiesToKeptConnection(connection);
        return connection;
    }

    /**
     * Applies {@link #databaseConfigProperties} to a connection kept from an earlier test, whose
     * values {@link #restoreDatabaseConfig()} put back at the end of it. A newly acquired
     * connection already has them, from {@link #setUpDatabaseConfig(DatabaseConfig)}; a kept one
     * is handed back without that running again, and the first reads of its
     * {@link DatabaseConfig} - the case sensitivity {@link #configureTest} resolves, the row
     * count check baseline - come before {@link #setupData()} retrieves it through the listener.
     *
     * @param connection The connection about to be handed out; may be null.
     */
    private void applyDatabaseConfigPropertiesToKeptConnection(
            final IDatabaseConnection connection)
    {
        if (connection == null || databaseConfigOverrides.isEmpty())
        {
            return;
        }

        final DatabaseConfig config = connection.getConfig();
        if (databaseConfigOverrides.hasAppliedTo(config))
        {
            return;
        }
        setUpDatabaseConfig(config);
    }

    /**
     * Puts back the {@link DatabaseConfig} values {@link #databaseConfigProperties} replaced, at
     * the end of this test's lifecycle or when the properties change.
     */
    private void restoreDatabaseConfig()
    {
        databaseConfigOverrides.restore();
    }

    /**
     * Release the connection shared by lookupFeatureValue(), setupData(),
     * verifyData() and cleanupData(), if one was acquired: closes it and
     * forgets it when {@link #closeConnectionAfterTest} is true (the default)
     * and neither the tester's nor this instance's operation listener leaves
     * every connection open, as the no-op listener does; otherwise
     * leaves it open for its real owner - a
     * {@link org.dbunit.database.CachingConnectionProvider}, an
     * externally-supplied fixed connection - and keeps it memoized so a later
     * lifecycle step reuses it rather than orphaning another. A kept connection
     * the pool or server has since closed is dropped and re-acquired on the
     * next {@link #getReusableConnection()}.
     *
     * @throws Exception On close errors.
     * @since 3.4.0
     */
    private void closeReusableConnection() throws Exception
    {
        try
        {
            reusableConnectionHolder.release();
        } finally
        {
            restoreDatabaseConfig();
        }
    }

    /**
     * Close the reusable connection, attaching any close failure to the given
     * primary throwable via {@link Throwable#addSuppressed(Throwable)} rather
     * than letting it replace and hide the primary. Mirrors the exception
     * safety of {@link #runTest} and {@code DatabaseTestCase.tearDown(Throwable)}.
     * <p>
     * Only ever called from a lifecycle step that has already failed, so it also
     * forgets the shared connection even when {@link #closeConnectionAfterTest}
     * is false and {@link #closeReusableConnection()} therefore left it open: a
     * step that just threw may have broken it, so the next
     * {@link #getReusableConnection()} re-acquires rather than reusing it. Any
     * {@link org.dbunit.database.CachingConnectionProvider} behind
     * {@code databaseTester} still owns closing it.
     *
     * @param primary
     *            The exception already in flight to attach a close failure
     *            to.
     * @since 3.4.0
     */
    private void closeReusableConnectionSuppressing(final Throwable primary)
    {
        reusableConnectionHolder.releaseSuppressing(primary);
        restoreDatabaseConfig();
    }

    /**
     * Runs a set up or tear down operation on the shared connection
     * {@link #getReusableConnection()} hands out, after telling the listeners about it with
     * {@link #tellListenersAboutConnection(IDatabaseConnection)}. A NONE operation retrieves no
     * connection and tells no listener, as when a databaseTester runs it. No listener is ever
     * told the operation finished, as this instance, not a listener, decides when the shared
     * connection closes.
     *
     * @param operation
     *            The operation to run.
     * @param dataSet
     *            The dataset to run it against.
     * @throws Exception
     *             On dbUnit errors.
     * @since 3.6.0
     */
    private void runOperation(final DatabaseOperation operation, final IDataSet dataSet)
            throws Exception
    {
        if (operation == DatabaseOperation.NONE)
        {
            return;
        }

        final IDatabaseConnection connection = getReusableConnection();
        tellListenersAboutConnection(connection);
        operation.execute(connection, dataSet);
    }

    /**
     * Tells the listeners about a connection a set up or tear down operation is about to run
     * on. When the databaseTester has a listener of its own, a caller's way to configure every
     * connection, it is told first, as it is when the tester drives onSetup()/onTearDown()
     * itself, and this instance's own listener, which applies
     * {@link #setUpDatabaseConfig(DatabaseConfig)}, second, so the values this instance applies
     * win. As that call does nothing for a connection the values are already applied to, they
     * are applied again directly after the databaseTester's listener, which may have changed
     * any of them. A databaseTester listener that is this instance's own, or the no-op
     * listener, is not told again.
     *
     * @param connection
     *            The connection the operation runs on.
     */
    private void tellListenersAboutConnection(final IDatabaseConnection connection)
    {
        final IOperationListener ownListener = getOperationListener();
        final IOperationListener testersListener = getTestersOwnListener();
        if (testersListener != null && testersListener != ownListener
                && testersListener != IOperationListener.NO_OP_OPERATION_LISTENER)
        {
            testersListener.connectionRetrieved(connection);
            applyDatabaseConfigProperties(connection.getConfig());
        }
        ownListener.connectionRetrieved(connection);
    }

    /**
     * Returns the databaseTester's own listener. The plain {@link DefaultOperationListener}
     * every {@link AbstractDatabaseTester} starts with counts as none, as it does nothing when a
     * connection is retrieved; a subclass of it does not.
     *
     * @return The databaseTester's own listener, or null if it has none.
     */
    private IOperationListener getTestersOwnListener()
    {
        if (databaseTester == null)
        {
            return null;
        }

        final IOperationListener testersListener = databaseTester.getOperationListener();
        if (testersListener != null && testersListener.getClass() == DefaultOperationListener.class)
        {
            return null;
        }
        return testersListener;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Captures the row count check baseline, if enabled, before setting up the prep data - see
     * {@link RowCountCheck#capture(IDatabaseConnection)}.
     */
    @Override
    public void preTest() throws Exception
    {
        captureRowCountBaseline();
        setupData();
    }

    /**
     * Capture the row count check baseline, using the connection shared with the rest of this
     * test's lifecycle. A no-op that leaves no baseline captured when the check is disabled.
     *
     * @throws Exception On dbUnit errors.
     * @since 3.6.0
     */
    private void captureRowCountBaseline() throws Exception
    {
        releasingIfAcquiredOnFailure(() -> rowCountChecker.capture(getReusableConnection()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preTest(final VerifyTableDefinition[] tables,
            final String[] prepDataFiles, final String[] expectedDataFiles)
            throws Exception
    {
        configureTest(tables, prepDataFiles, expectedDataFiles);
        preTest();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preTest(final PrepAndExpectedTestData testData) throws Exception
    {
        if (testData.isVerifyingEveryExpectedTable())
        {
            configureTest(testData);
            preTest();
        } else
        {
            PrepAndExpectedTestCase.super.preTest(testData);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object runTest(final VerifyTableDefinition[] verifyTables,
            final String[] prepDataFiles, final String[] expectedDataFiles,
            final PrepAndExpectedTestCaseSteps testSteps) throws Exception
    {
        return runPreparedTest(() -> preTest(verifyTables, prepDataFiles, expectedDataFiles),
                testSteps);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object runTest(final PrepAndExpectedTestData testData,
            final PrepAndExpectedTestCaseSteps testSteps) throws Exception
    {
        if (testData.isVerifyingEveryExpectedTable())
        {
            return runPreparedTest(() -> preTest(testData), testSteps);
        }
        return PrepAndExpectedTestCase.super.runTest(testData, testSteps);
    }

    private Object runPreparedTest(final LifecycleStep preTestStep,
            final PrepAndExpectedTestCaseSteps testSteps) throws Exception
    {
        final Object result;

        try
        {
            preTestStep.run();
            log.info("runTest: running test steps");
            result = runTestSteps(testSteps);
        } catch (final Throwable e)
        {
            log.error(TEST_ERROR_MSG, e);
            // don't verify table data when test execution has errors as:
            // * a verify data failure masks the test error exception
            // * tables in unknown state and therefore probably not accurate
            try
            {
                postTest(false);
            } catch (final Throwable cleanupFailure)
            {
                // never let a cleanup failure replace and hide the real test
                // failure; keep e as the thrown exception, cleanupFailure alongside
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }

        postTest();

        return result;
    }

    /**
     * Run the provided test steps. Override as necessary for custom logic.
     *
     * @param testSteps
     *            The test steps to run.
     * @return the user-defined object returned by the test steps.
     * @throws Exception if the test steps fail.
     */
    protected Object runTestSteps(final PrepAndExpectedTestCaseSteps testSteps)
            throws Exception
    {
        return testSteps.run();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postTest() throws Exception
    {
        postTest(true);
    }

    /**
     * {@inheritDoc}
     * <p>
     * When {@code verifyData} is false - the test steps already failed - discards any
     * captured row count check baseline, so cleanupData() skips that check too: the database
     * is in an unknown state, so a count difference would be noise around the real failure,
     * not a finding worth its own report. A verification failure discards it the same way,
     * so cleanupData() does not throw a row count difference in its place: the comparison
     * failure names what differs and is the one to fix first, while a cleanup failure still
     * shadows it as before.
     */
    @Override
    public void postTest(final boolean verifyData) throws Exception
    {
        Throwable verifyFailure = null;
        try
        {
            if (verifyData)
            {
                verifyData();
            } else
            {
                rowCountChecker.discardBaseline();
            }
        } catch (final Throwable t)
        {
            verifyFailure = t;
            rowCountChecker.discardBaseline();
            throw t;
        } finally
        {
            // it is deliberate to have cleanup exceptions shadow verify
            // failures so user knows db is probably in unknown state (for
            // those not using an in-memory db or transaction rollback),
            // otherwise would mask probable cause of subsequent test
            // failures; the verify failure rides along as suppressed on the
            // cleanup exception so it is not lost entirely
            try
            {
                cleanupData();
            } catch (final Throwable cleanupFailure)
            {
                if (verifyFailure != null)
                {
                    cleanupFailure.addSuppressed(verifyFailure);
                }
                throw cleanupFailure;
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the tear down operation against the connection shared with
     * setupData() and verifyData() for this test's lifecycle, then closes it.
     * See #800. A failure of the tear down operation or of the row count
     * check, an {@link Error} such as an {@link AssertionError} from the
     * tester's listener included, releases the connection the same way and
     * propagates unchanged.
     */
    @Override
    public void cleanupData() throws Exception
    {
        requireDatabaseTester();
        try
        {
            final boolean isCaseSensitiveTableNames;
            if (cachedIsCaseSensitiveTableNames == null)
            {
                isCaseSensitiveTableNames = lookupFeatureValue(
                        DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES);
            } else
            {
                isCaseSensitiveTableNames = cachedIsCaseSensitiveTableNames;
            }
            log.debug("cleanupData: using case sensitive table names={}",
                    isCaseSensitiveTableNames);

            final IDataSet[] dataSets = {prepDataSet, expectedDataSet};
            final IDataSet dataset = new CompositeDataSet(dataSets, true,
                    isCaseSensitiveTableNames);
            final String[] tableNames = dataset.getTableNames();
            final int count = tableNames.length;
            log.info("cleanupData: about to clean up {} tables={}", count,
                    tableNames);

            runOperation(getTearDownOperation(), dataset);
            log.debug("cleanupData: Clean up done");
            verifyRowCountUnchanged();
            closeReusableConnection();
        } catch (final Throwable t)
        {
            log.error("cleanupData: Exception:", t);
            closeReusableConnectionSuppressing(t);
            throw t;
        }
    }

    /**
     * Verify the row count check baseline, if one was captured, using the connection shared
     * with the rest of this test's lifecycle. A no-op when no baseline was captured - the
     * check is disabled, capture never ran, or {@link #postTest(boolean)} discarded it
     * because the test steps already failed - which does not acquire a connection just to
     * find that out.
     *
     * @throws Exception On dbUnit errors, including {@link org.dbunit.database.rowcount.UnexpectedRowCountException}
     *             when a table's row count no longer matches the baseline.
     * @since 3.6.0
     */
    private void verifyRowCountUnchanged() throws Exception
    {
        if (rowCountChecker.hasBaseline())
        {
            rowCountChecker.verify(getReusableConnection());
        }
    }

    /**
     * Legacy JUnit-3-era set-up hook. Not invoked automatically under JUnit 5;
     * kept for subclasses that drive the lifecycle manually. Runs
     * {@link #setupData()} on the connection shared with the rest of this
     * test's lifecycle, leaving the databaseTester's dataset and operation
     * listener as the caller set them.
     */
    @Override
    protected void setUp() throws Exception
    {
        setupData();
    }

    /**
     * Legacy JUnit-3-era tear-down hook. Not invoked automatically under JUnit 5;
     * kept for subclasses that drive the lifecycle manually. Runs
     * {@link #cleanupData()}, which tears down the prep and expected tables once
     * and releases the shared connection. Calling it after a full
     * {@link #runTest} or {@link #postTest} cycle cleans up a second time, as
     * both already ran cleanupData(), without verifying the row count check
     * baseline again, which that cleanupData() already used.
     */
    @Override
    protected void tearDown() throws Exception
    {
        cleanupData();
    }

    /**
     * Legacy JUnit-3-era tear-down hook for a test whose failure is already in
     * flight. Runs {@link #cleanupData()} the same way as {@link #tearDown()},
     * and throws {@code testFailure} afterward, with any clean up failure
     * attached to it as suppressed rather than replacing it.
     *
     * @param testFailure
     *            The throwable already in flight from the test body, or
     *            <code>null</code> if there is none, in which case this
     *            behaves the same as {@link #tearDown()}.
     * @throws Throwable
     *             The testFailure, if not <code>null</code>; otherwise a clean up
     *             failure, if one occurred.
     */
    @Override
    protected void tearDown(final Throwable testFailure) throws Throwable
    {
        if (testFailure == null)
        {
            cleanupData();
            return;
        }

        try
        {
            cleanupData();
        } catch (final Throwable cleanupFailure)
        {
            if (cleanupFailure != testFailure)
            {
                testFailure.addSuppressed(cleanupFailure);
            }
        }
        throw testFailure;
    }

    /**
     * Use the provided databaseTester to prep the database with the provided
     * prep dataset. See {@link org.dbunit.IDatabaseTester#onSetup()}.
     * <p>
     * Executes against the connection shared with verifyData() and
     * cleanupData() for this test's lifecycle rather than a fresh one, and
     * leaves it open; cleanupData() closes it. See #800.
     *
     * @throws Exception if preparing the data fails.
     */
    public void setupData() throws Exception
    {
        requireDatabaseTester();
        log.info("setupData: setting prep dataset and inserting rows");

        try
        {
            runOperation(getSetUpOperation(), getDataSet());
        } catch (final Exception e)
        {
            log.error("setupData: Exception with setting up data:", e);
            throw e;
        }
    }

    /**
     * Fails fast, the same way for every lifecycle step, when no databaseTester was set.
     *
     * @throws IllegalStateException If databaseTester is null.
     */
    private void requireDatabaseTester()
    {
        if (databaseTester == null)
        {
            throw new IllegalStateException(DATABASE_TESTER_IS_NULL_MSG);
        }
    }

    /**
     * {@inheritDoc} Returns the operation set with {@link #setSetUpOperation(DatabaseOperation)},
     * or the databaseTester's own when none is set.
     */
    @Override
    protected DatabaseOperation getSetUpOperation() throws Exception
    {
        if (setUpOperation != null)
        {
            return setUpOperation;
        }

        requireDatabaseTester();
        return databaseTester.getSetUpOperation();
    }

    /**
     * {@inheritDoc} Returns the operation set with
     * {@link #setTearDownOperation(DatabaseOperation)}, or the databaseTester's own when none is
     * set.
     */
    @Override
    protected DatabaseOperation getTearDownOperation() throws Exception
    {
        if (tearDownOperation != null)
        {
            return tearDownOperation;
        }

        requireDatabaseTester();
        return databaseTester.getTearDownOperation();
    }

    /**
     * {@inheritDoc} Uses the connection from the provided databaseTester,
     * shared with setupData() and cleanupData() for this test's lifecycle.
     * Left open on return; cleanupData() closes it. See #800.
     */
    @Override
    public void verifyData() throws Exception
    {
        requireDatabaseTester();

        final IDatabaseConnection reusableConnection = getReusableConnection();

        final DatabaseConfig config = reusableConnection.getConfig();
        expectedDataSetAndVerifyTableDefinitionVerifier.verify(verifyTableDefs,
                expectedDataSet, config);

        try
        {
            final int tableDefsCount = verifyTableDefs.length;
            if (tableDefsCount == 0)
            {
                log.info("verifyData: No tables to verify as"
                        + " no VerifyTableDefinitions specified");
            } else
            {
                log.info(
                        "verifyData: about to verify {} tables"
                                + " using verifyTableDefinitions={}",
                        tableDefsCount, verifyTableDefs);
            }

            for (int i = 0; i < tableDefsCount; i++)
            {
                final VerifyTableDefinition td = verifyTableDefs[i];
                verifyData(reusableConnection, td);
            }
        } catch (final Exception e)
        {
            log.error("verifyData: Exception:", e);
            throw e;
        }
    }

    /**
     * Verifies a single table's actual data against its expected data.
     *
     * @param connection the database connection to load the actual data from.
     * @param verifyTableDefinition the table definition to verify.
     * @throws Exception if verifying the table fails.
     */
    protected void verifyData(final IDatabaseConnection connection,
            final VerifyTableDefinition verifyTableDefinition) throws Exception
    {
        final String tableName = verifyTableDefinition.getTableName();
        log.debug("verifyData: Verifying table '{}'", tableName);

        final String[] excludeColumns =
                verifyTableDefinition.getColumnExclusionFilters();
        final String[] includeColumns =
                verifyTableDefinition.getColumnInclusionFilters();
        final Map<String, ValueComparer> columnValueComparers =
                verifyTableDefinition.getColumnValueComparers();
        final ValueComparer defaultValueComparer =
                verifyTableDefinition.getDefaultValueComparer();
        final boolean sortOnFilteredColumnsOnly =
                verifyTableDefinition.isSortOnFilteredColumnsOnly();

        final ITable expectedTable = loadTableDataFromDataSet(tableName);
        final ITable actualTable;
        if (sortOnFilteredColumnsOnly
                && !isLoadTableDataFromDatabaseOverridden())
        {
            actualTable = loadComparedColumnsFromDatabase(tableName,
                    connection, excludeColumns, includeColumns);
        } else
        {
            actualTable = loadTableDataFromDatabase(tableName, connection);
        }

        if (sortOnFilteredColumnsOnly)
        {
            verifyData(expectedTable, actualTable, excludeColumns,
                    includeColumns, defaultValueComparer, columnValueComparers,
                    true);
        } else
        {
            verifyData(expectedTable, actualTable, excludeColumns,
                    includeColumns, defaultValueComparer,
                    columnValueComparers);
        }
    }

    /**
     * Loads the given table's expected data from the expected dataset.
     *
     * @param tableName the name of the table to load.
     * @return the table's expected data.
     * @throws DataSetException if loading the table fails.
     */
    public ITable loadTableDataFromDataSet(final String tableName)
            throws DataSetException
    {
        ITable table = null;

        final String methodName = "loadTableDataFromDataSet";

        log.debug("{}: Loading table {} from expected dataset", methodName,
                tableName);
        try
        {
            table = expectedDataSet.getTable(tableName);
        } catch (final Exception e)
        {
            final String msg = methodName + ": Problem obtaining table '"
                    + tableName + "' from expected dataset";
            log.error(msg, e);
            throw new DataSetException(msg, e);
        }
        return table;
    }

    /**
     * Loads the given table's actual data from the database.
     *
     * @param tableName the name of the table to load.
     * @param connection the database connection to load the data from.
     * @return the table's actual data.
     * @throws Exception if loading the table fails.
     */
    public ITable loadTableDataFromDatabase(final String tableName,
            final IDatabaseConnection connection) throws Exception
    {
        ITable table = null;

        final String methodName = "loadTableDataFromDatabase";

        log.debug("{}: Loading table {} from database", methodName, tableName);
        try
        {
            table = connection.createTable(tableName);
        } catch (final Exception e)
        {
            final String msg = methodName + ": Problem obtaining table '"
                    + tableName + "' from database";
            log.error(msg, e);
            throw new DataSetException(msg, e);
        }
        return table;
    }

    /**
     * Loads the given table's actual data from the database selecting only
     * the columns that survive the exclude and include column filters, which
     * are the only ones that play a part when sorting only on the filtered
     * columns and comparing. An excluded column, often a large one such as a
     * BLOB or CLOB, is not fetched. The columns come from the connection's
     * cached {@link org.dbunit.database.DatabaseDataSet}, which the set up
     * operation has already initialized, and the table is made by the
     * connection's configured
     * {@link DatabaseConfig#PROPERTY_RESULTSET_TABLE_FACTORY}. When the
     * table's columns cannot be determined that way, or all of them are
     * filtered out, which would leave nothing to select, it loads every column
     * as {@link #loadTableDataFromDatabase(String, IDatabaseConnection)} does.
     *
     * @param tableName
     *            The name of the table to load.
     * @param connection
     *            The database connection to load the data from.
     * @param excludeColumns
     *            The column names to exclude; null or empty to exclude none.
     * @param includeColumns
     *            The column names to only include; null or empty to include
     *            all.
     * @return The table's actual data for the compared columns.
     * @throws Exception
     *             If loading the table fails.
     */
    private ITable loadComparedColumnsFromDatabase(final String tableName,
            final IDatabaseConnection connection, final String[] excludeColumns,
            final String[] includeColumns) throws Exception
    {
        final String methodName = "loadComparedColumnsFromDatabase";

        final ITableMetaData comparedMetaData = findComparedColumns(tableName,
                connection, excludeColumns, includeColumns);
        if (comparedMetaData == null)
        {
            return loadTableDataFromDatabase(tableName, connection);
        }

        log.debug("{}: Loading table {} from database, selecting only the"
                + " compared columns", methodName, tableName);
        try
        {
            final IResultSetTableFactory tableFactory =
                    (IResultSetTableFactory) connection.getConfig().getProperty(
                            DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY);
            return tableFactory.createTable(comparedMetaData, connection);
        } catch (final Exception e)
        {
            final String msg = methodName + ": Problem obtaining table '"
                    + tableName + "' from database";
            log.error(msg, e);
            throw new DataSetException(msg, e);
        }
    }

    /**
     * Finds the given table's columns that survive the exclude and include
     * column filters.
     *
     * @param tableName
     *            The name of the table.
     * @param connection
     *            The database connection whose dataset describes the table.
     * @param excludeColumns
     *            The column names to exclude; null or empty to exclude none.
     * @param includeColumns
     *            The column names to only include; null or empty to include
     *            all.
     * @return The metadata of just those columns, or null when it cannot be
     *         determined from the connection's dataset or no column is left.
     */
    private ITableMetaData findComparedColumns(final String tableName,
            final IDatabaseConnection connection, final String[] excludeColumns,
            final String[] includeColumns)
    {
        try
        {
            final IDataSet databaseDataSet = connection.createDataSet();
            if (databaseDataSet == null)
            {
                return null;
            }

            final ITableMetaData databaseMetaData =
                    databaseDataSet.getTableMetaData(tableName);
            final ITableMetaData comparedMetaData = new FilteredTableMetaData(
                    databaseMetaData,
                    makeColumnFilter(excludeColumns, includeColumns));
            if (comparedMetaData.getColumns().length == 0)
            {
                log.debug("findComparedColumns: No column of table {} is"
                        + " compared, selecting every column", tableName);
                return null;
            }
            return comparedMetaData;
        } catch (final SQLException | DataSetException e)
        {
            log.debug("findComparedColumns: Cannot determine the columns of"
                    + " table {}, selecting every column", tableName, e);
            return null;
        }
    }

    /**
     * Returns whether a subclass overrides
     * {@link #loadTableDataFromDatabase(String, IDatabaseConnection)}, for
     * example to add a where clause, in which case it, not this class,
     * decides what is loaded.
     *
     * @return True if a subclass overrides it.
     */
    private boolean isLoadTableDataFromDatabaseOverridden()
    {
        return isOverriddenBySubclass("loadTableDataFromDatabase", String.class,
                IDatabaseConnection.class);
    }

    /**
     * Returns whether a subclass overrides the public method of this class with the given name
     * and parameter types, in which case it, not this class, decides what that method does.
     *
     * @param methodName
     *            The name of the method.
     * @param parameterTypes
     *            The parameter types of the method.
     * @return True if a subclass overrides it.
     */
    private boolean isOverriddenBySubclass(final String methodName,
            final Class<?>... parameterTypes)
    {
        try
        {
            final Class<?> declaringClass =
                    getClass().getMethod(methodName, parameterTypes).getDeclaringClass();
            return declaringClass != DefaultPrepAndExpectedTestCase.class;
        } catch (final NoSuchMethodException e)
        {
            return true;
        }
    }

    /**
     * For the specified expected and actual tables (and excluding and including
     * the specified columns), verify the actual data is as expected.
     *
     * @param expectedTable
     *            The expected table to compare the actual table to.
     * @param actualTable
     *            The actual table to compare to the expected table.
     * @param excludeColumns
     *            The column names to exclude from comparison. See
     *            {@link org.dbunit.dataset.filter.DefaultColumnFilter#excludeColumn(String)}
     *            .
     * @param includeColumns
     *            The column names to only include in comparison. See
     *            {@link org.dbunit.dataset.filter.DefaultColumnFilter#includeColumn(String)}
     *            .
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is the
     *            {@link ValueComparer}. Can be <code>null</code> and will
     *            default to defaultValueComparer for all columns in all tables.
     * @throws DatabaseUnitException if the tables' row counts, columns, or data do not match.
     * @see #verifyData(ITable, ITable, String[], String[], ValueComparer, Map, boolean)
     *      to also control whether sorting considers only the filtered
     *      columns; this overload always sorts by all native columns.
     */
    protected void verifyData(final ITable expectedTable,
            final ITable actualTable, final String[] excludeColumns,
            final String[] includeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        verifyData(expectedTable, actualTable, excludeColumns, includeColumns,
                defaultValueComparer, columnValueComparers, false);
    }

    /**
     * For the specified expected and actual tables (and excluding and including
     * the specified columns), verify the actual data is as expected.
     *
     * @param expectedTable
     *            The expected table to compare the actual table to.
     * @param actualTable
     *            The actual table to compare to the expected table.
     * @param excludeColumns
     *            The column names to exclude from comparison. See
     *            {@link org.dbunit.dataset.filter.DefaultColumnFilter#excludeColumn(String)}
     *            .
     * @param includeColumns
     *            The column names to only include in comparison. See
     *            {@link org.dbunit.dataset.filter.DefaultColumnFilter#includeColumn(String)}
     *            .
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is the
     *            {@link ValueComparer}. Can be <code>null</code> and will
     *            default to defaultValueComparer for all columns in all tables.
     * @param sortOnFilteredColumnsOnly
     *            True to sort the expected and actual tables by only the
     *            columns that survive excludeColumns/includeColumns, instead
     *            of by all of the actual table's native columns; see
     *            {@link VerifyTableDefinition#isSortOnFilteredColumnsOnly()}.
     *            When false and the expected table omits columns of the actual
     *            table, so the two sort by different columns, a failed
     *            comparison that passes when sorted by only the filtered
     *            columns carries a suppressed exception naming this as the
     *            remedy.
     * @throws DatabaseUnitException if the tables' row counts, columns, or data do not match.
     * @since 3.5.0
     */
    protected void verifyData(final ITable expectedTable,
            final ITable actualTable, final String[] excludeColumns,
            final String[] includeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers,
            final boolean sortOnFilteredColumnsOnly)
            throws DatabaseUnitException
    {
        final String methodName = "verifyData";

        final ITableMetaData actualTableMetaData =
                actualTable.getTableMetaData();
        final ITableMetaData expectedTableMetaData =
                expectedTable.getTableMetaData();

        final Column[] actualTableColumns = actualTableMetaData.getColumns();
        final Column[] expectedTableColumns = columnsNamedIn(actualTableColumns,
                expectedTableMetaData.getColumns());
        final String tableName = actualTableMetaData.getTableName();
        final boolean sortKeysDiffer = !sortOnFilteredColumnsOnly
                && expectedTableColumns.length < actualTableColumns.length;

        final Column[] actualSortColumns;
        final Column[] expectedSortColumns;
        if (sortOnFilteredColumnsOnly)
        {
            log.debug("{}: Sorting using only filtered columns", methodName);
            actualSortColumns = filterColumns(actualTableColumns,
                    excludeColumns, includeColumns, tableName);
            expectedSortColumns = filterColumns(expectedTableColumns,
                    excludeColumns, includeColumns, tableName);
        } else
        {
            log.debug("{}: Sorting using all columns", methodName);
            actualSortColumns = actualTableColumns;
            expectedSortColumns = expectedTableColumns;
        }

        final SortedTable expectedSortedTable =
                sortByColumns(expectedTable, expectedSortColumns, true);
        log.trace("{}: Sorted expected table={}", methodName,
                expectedSortedTable);

        final SortedTable actualSortedTable =
                sortByColumns(actualTable, actualSortColumns, false);
        log.trace("{}: Sorted actual table={}", methodName, actualSortedTable);

        // Filter out the columns from the expected and actual results
        log.debug(
                "{}: Applying column exclude and include filters to sorted expected table",
                methodName);
        final ITable expectedFilteredTable = applyColumnFilters(
                expectedSortedTable, excludeColumns, includeColumns);
        log.debug(
                "{}: Applying column exclude and include filters to sorted actual table",
                methodName);
        final ITable actualFilteredTable = applyColumnFilters(actualSortedTable,
                excludeColumns, includeColumns);

        log.debug("{}: Creating additionalColumnInfo for expected table",
                methodName);
        final Column[] allAdditionalColumnInfo =
                makeAdditionalColumnInfo(expectedTable, excludeColumns);
        final Column[] additionalColumnInfo =
                columnsNamedIn(allAdditionalColumnInfo, actualTableColumns);
        log.trace("{}: additionalColumnInfo={}", methodName,
                additionalColumnInfo);

        logSortedTables(expectedSortedTable, actualSortedTable);

        log.debug("{}: Comparing expected table to actual table", methodName);
        try
        {
            compareData(expectedFilteredTable, actualFilteredTable,
                    additionalColumnInfo, defaultValueComparer,
                    columnValueComparers);
        } catch (final AssertionError e)
        {
            if (sortKeysDiffer)
            {
                final boolean passesWhenSortedOnFilteredColumnsOnly =
                        passesWhenSortedOnFilteredColumnsOnly(expectedTable,
                                actualTable, excludeColumns, includeColumns,
                                defaultValueComparer, columnValueComparers);
                if (passesWhenSortedOnFilteredColumnsOnly)
                {
                    final DatabaseUnitRuntimeException hint = makeSortHint(
                            tableName, actualTableColumns.length,
                            expectedTableColumns.length);
                    e.addSuppressed(hint);
                    // also logged: a build that trims stack traces, for
                    // example Surefire with trimStackTrace, drops suppressed
                    log.warn(hint.getMessage());
                }
            }
            throw e;
        }
    }

    /**
     * Sorts the table by the given columns, comparing the values as the
     * columns' data types do instead of as text.
     *
     * @param table
     *            The table to sort.
     * @param sortColumns
     *            The columns to sort by.
     * @param useSpecifiedColumns
     *            True to use the column definitions of sortColumns in the
     *            sorted table, as the expected table does to follow the actual
     *            table's; false to use the table's own.
     * @return The sorted view of the table.
     * @throws DataSetException
     *             If a sort column does not exist in the table.
     */
    private SortedTable sortByColumns(final ITable table,
            final Column[] sortColumns, final boolean useSpecifiedColumns)
            throws DataSetException
    {
        final SortedTable sortedTable =
                new SortedTable(table, sortColumns, useSpecifiedColumns);
        sortedTable.setUseComparable(true);
        return sortedTable;
    }

    /**
     * Returns whether comparing the tables passes when both are sorted by only
     * the columns that survive the exclude and include column filters, as
     * {@code sortOnFilteredColumnsOnly} sorts them. It compares with a fresh
     * {@link DefaultFailureHandler} and no additional column info, instead of
     * through {@link #compareData} and the {@link #failureHandler}, so that it
     * neither runs a subclass's comparison nor records differences in a
     * handler that collects them. It does not compare tables of different
     * columns or row counts, which cannot pass. Whatever it throws means that
     * it does not pass: this check explains a failure and must never replace
     * it.
     *
     * @param expectedTable
     *            The expected table.
     * @param actualTable
     *            The actual table.
     * @param excludeColumns
     *            The column names to exclude from comparison.
     * @param includeColumns
     *            The column names to only include in comparison.
     * @param defaultValueComparer
     *            The value comparer for columns without one in
     *            columnValueComparers; can be null.
     * @param columnValueComparers
     *            The value comparers for specific columns; can be null.
     * @return True if the comparison passes with that sort.
     */
    private boolean passesWhenSortedOnFilteredColumnsOnly(
            final ITable expectedTable, final ITable actualTable,
            final String[] excludeColumns, final String[] includeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
    {
        final String methodName = "passesWhenSortedOnFilteredColumnsOnly";
        try
        {
            final ITableMetaData actualTableMetaData =
                    actualTable.getTableMetaData();
            final ITableMetaData expectedTableMetaData =
                    expectedTable.getTableMetaData();
            final Column[] actualTableColumns =
                    actualTableMetaData.getColumns();
            final Column[] expectedTableColumns = columnsNamedIn(actualTableColumns,
                    expectedTableMetaData.getColumns());
            final String tableName = actualTableMetaData.getTableName();

            final Column[] actualSortColumns = filterColumns(
                    actualTableColumns, excludeColumns, includeColumns,
                    tableName);
            final Column[] expectedSortColumns = filterColumns(
                    expectedTableColumns, excludeColumns, includeColumns,
                    tableName);
            final SortedTable expectedSortedTable =
                    sortByColumns(expectedTable, expectedSortColumns, true);
            final SortedTable actualSortedTable =
                    sortByColumns(actualTable, actualSortColumns, false);
            final ITable expectedFilteredTable = applyColumnFilters(
                    expectedSortedTable, excludeColumns, includeColumns);
            final ITable actualFilteredTable = applyColumnFilters(
                    actualSortedTable, excludeColumns, includeColumns);

            if (!haveSameColumnsAndRowCount(expectedFilteredTable,
                    actualFilteredTable))
            {
                return false;
            }

            Assertion.assertWithValueComparer(expectedFilteredTable,
                    actualFilteredTable, new DefaultFailureHandler(),
                    defaultValueComparer, columnValueComparers);
            return true;
        } catch (final AssertionError | Exception e)
        {
            log.debug("{}: The comparison does not pass sorted by only the"
                    + " filtered columns", methodName, e);
            return false;
        }
    }

    /**
     * Returns whether the tables have the same columns, without regard to case,
     * and the same number of rows. Comparing tables that differ in either fails
     * before any value is compared, and logs an error.
     *
     * @param expectedTable
     *            The expected table.
     * @param actualTable
     *            The actual table.
     * @return True if both have the same columns and row count.
     * @throws DataSetException
     *             If the columns cannot be retrieved.
     */
    private boolean haveSameColumnsAndRowCount(final ITable expectedTable,
            final ITable actualTable) throws DataSetException
    {
        final ITableMetaData expectedMetaData =
                expectedTable.getTableMetaData();
        final ITableMetaData actualMetaData = actualTable.getTableMetaData();
        final Columns.ColumnDiff columnDiff =
                Columns.getColumnDiff(expectedMetaData, actualMetaData);
        final boolean sameColumns = !columnDiff.hasDifference();
        final int expectedRowCount = expectedTable.getRowCount();
        final int actualRowCount = actualTable.getRowCount();
        final boolean sameRowCount = expectedRowCount == actualRowCount;
        return sameColumns && sameRowCount;
    }

    /**
     * Makes the hint for a failed comparison that passes when sorted by only
     * the filtered columns: the actual table was sorted by all of its columns
     * and the expected table by only the columns it declares, which can compare
     * rows that are equal against different counterparts.
     *
     * @param tableName
     *            The name of the table that failed the comparison.
     * @param actualColumnCount
     *            The number of columns the actual table was sorted by.
     * @param expectedColumnCount
     *            The number of columns the expected table was sorted by.
     * @return The hint, to attach to the failure as suppressed.
     */
    private DatabaseUnitRuntimeException makeSortHint(final String tableName,
            final int actualColumnCount, final int expectedColumnCount)
    {
        final String message = "verifyData: The comparison of table '"
                + tableName + "' failed, but it passes when both tables are"
                + " sorted by only the compared columns. The actual table was"
                + " sorted by all " + actualColumnCount
                + " of its columns and the expected table by only the "
                + expectedColumnCount + " it declares, so rows that are equal"
                + " on the compared columns can be matched against different"
                + " counterparts, for example when the columns the expected"
                + " table omits are generated. Sort by only the compared"
                + " columns with setSortOnFilteredColumnsOnly(true) on the"
                + " table's VerifyTableDefinition, or sortOnFilteredColumnsOnly"
                + " = true on its @DbUnitVerifyTable.";
        return new DatabaseUnitRuntimeException(message);
    }

    /**
     * Reduces the given columns to those that survive the given exclude and
     * include column filters, using the same matching semantics - including
     * {@link DefaultColumnFilter}'s wildcard pattern support - as
     * {@link #applyColumnFilters(ITable, String[], String[])}, so the sort
     * key always matches the columns that end up compared.
     *
     * @param columns
     *            The columns to filter.
     * @param excludeColumns
     *            The column names to exclude; null or empty to exclude none.
     * @param includeColumns
     *            The column names to only include; null to include all.
     * @param tableName
     *            The table name, which may be null; passed only to
     *            {@link DefaultColumnFilter#accept(String, Column)} for its
     *            debug logging.
     * @return The filtered columns, in columns' original order.
     */
    private Column[] filterColumns(final Column[] columns,
            final String[] excludeColumns, final String[] includeColumns,
            final String tableName)
    {
        final DefaultColumnFilter columnFilter =
                makeColumnFilter(excludeColumns, includeColumns);
        return FilteredTableMetaData.getFilteredColumns(tableName, columns,
                columnFilter);
    }

    /**
     * Makes the column filter for the given exclude and include column
     * patterns, which {@link DefaultColumnFilter} matches without regard to
     * case and with its wildcard support.
     *
     * @param excludeColumns
     *            The column names to exclude; null or empty to exclude none.
     * @param includeColumns
     *            The column names to only include; null or empty to include
     *            all.
     * @return The filter accepting the columns that are included and not
     *         excluded.
     */
    private DefaultColumnFilter makeColumnFilter(final String[] excludeColumns,
            final String[] includeColumns)
    {
        final DefaultColumnFilter columnFilter = new DefaultColumnFilter();
        if (includeColumns != null)
        {
            for (final String includeColumn : includeColumns)
            {
                columnFilter.includeColumn(includeColumn);
            }
        }
        if (excludeColumns != null)
        {
            for (final String excludeColumn : excludeColumns)
            {
                columnFilter.excludeColumn(excludeColumn);
            }
        }
        return columnFilter;
    }

    /**
     * Keeps the columns whose name, without regard to case, is also the name of
     * one of the given other columns.
     *
     * <p>
     * Used to make the expected table's columns from the actual table's column
     * definitions, so the expected data is sorted and compared the same way as
     * the actual data - with the data types and column order of the database -
     * whichever types and order its source declared. Columns of the actual table
     * that the expected table does not have are left out, and with no expected
     * columns none are kept.
     *
     * @param columns
     *            The columns to keep from.
     * @param namedColumns
     *            The columns whose names decide which of columns to keep.
     * @return The kept columns, in columns' original order.
     */
    private Column[] columnsNamedIn(final Column[] columns,
            final Column[] namedColumns)
    {
        final Set<String> names =
                Arrays.stream(namedColumns).map(Column::getColumnName)
                        .map(name -> name.toLowerCase(Locale.ENGLISH))
                        .collect(Collectors.toSet());

        final List<Column> keptColumns = Arrays.stream(columns)
                .filter(col -> names.contains(
                        col.getColumnName().toLowerCase(Locale.ENGLISH)))
                .collect(Collectors.toList());
        return keptColumns.toArray(new Column[keptColumns.size()]);
    }

    private void logSortedTables(final SortedTable expectedSortedTable,
            final SortedTable actualSortedTable)
    {
        if (log.isTraceEnabled())
        {
            logSortedTable("expectedSortedTable", expectedSortedTable);
            logSortedTable("actualSortedTable", actualSortedTable);
        }
    }

    private void logSortedTable(final String tableTypeName,
            final SortedTable table)
    {
        final String methodName = "logSortedTable:";
        final Column[] sortColumns = table.getSortColumns();
        log.trace("{} {} sortColumns={}", methodName, tableTypeName,
                sortColumns);
        try
        {
            final String tableContents = tableFormatter.format(table);
            log.trace("{} {} tableContents={}", methodName, tableTypeName,
                    tableContents);
        } catch (final DataSetException e)
        {
            log.error("{} Error trying to log table={}", methodName,
                    tableTypeName, e);
        }
    }

    /**
     * Compare the tables, enables easy overriding.
     * <p>
     * Uses {@link #failureHandler} when set; otherwise defers to
     * {@link Assertion#assertWithValueComparer(ITable, ITable, Column[], ValueComparer, Map)}'s
     * own {@link org.dbunit.assertion.DefaultFailureHandler}, configured with
     * additionalColumnInfo.
     *
     * @param expectedTable the table containing all expected results.
     * @param actualTable the table containing all actual results.
     * @param additionalColumnInfo the additional columns to include in failure messages.
     * @param defaultValueComparer the value comparer used when no more specific comparer is configured.
     * @param columnValueComparers the per-column value comparers to use.
     * @throws DatabaseUnitException if the tables' row counts, columns, or data do not match.
     */
    protected void compareData(final ITable expectedTable,
            final ITable actualTable, final Column[] additionalColumnInfo,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        if (failureHandler == null)
        {
            Assertion.assertWithValueComparer(expectedTable, actualTable,
                    additionalColumnInfo, defaultValueComparer,
                    columnValueComparers);
        } else
        {
            Assertion.assertWithValueComparer(expectedTable, actualTable,
                    failureHandler, defaultValueComparer,
                    columnValueComparers);
        }
    }

    /**
     * Don't add excluded columns to additionalColumnInfo as they are not
     * compared. The verification then drops, from what this returns, the
     * columns the actual table does not have, for example one that was not
     * loaded from the database, as the fail message would show a not found
     * message for them.
     *
     * @param expectedTable
     *            Not null.
     * @param excludeColumns
     *            Nullable.
     * @return the additional column info, excluding excludeColumns.
     * @throws DataSetException if the expected table's columns cannot be retrieved.
     */
    protected Column[] makeAdditionalColumnInfo(final ITable expectedTable,
            final String[] excludeColumns) throws DataSetException
    {
        final Column[] allColumns =
                expectedTable.getTableMetaData().getColumns();

        return excludeColumns == null ? allColumns
                : makeAdditionalColumnInfo(excludeColumns, allColumns);
    }

    /**
     * Don't add excluded columns to additionalColumnInfo as they are not
     * compared. The exclude patterns match the way the column filters applied
     * to the compared tables do: without regard to case, and with
     * {@link DefaultColumnFilter}'s wildcard support. The verification then
     * drops, from what this returns, the columns the actual table does not
     * have, for example one that was not loaded from the database, as the fail
     * message would show a not found message for them.
     *
     * @param excludeColumns
     *            Not null.
     * @param allColumns
     *            Not null.
     * @return the additional column info, excluding excludeColumns.
     */
    protected Column[] makeAdditionalColumnInfo(final String[] excludeColumns,
            final Column[] allColumns)
    {
        return filterColumns(allColumns, excludeColumns, null, null);
    }

    /**
     * Make a <code>IDataSet</code> from the specified files with case sensitive
     * table names as false.
     *
     * @param dataFiles
     *            Represents the array of dbUnit data files.
     * @param dataFilesName
     *            Concept name of the data files, e.g. prep, expected.
     * @return The composite dataset.
     * @throws DataSetException
     *             On dbUnit errors.
     */
    public IDataSet makeCompositeDataSet(final String[] dataFiles,
            final String dataFilesName) throws DataSetException
    {
        return makeCompositeDataSet(dataFiles, dataFilesName, false);
    }

    /**
     * Make a <code>IDataSet</code> from the specified files.
     *
     * @param dataFiles
     *            Represents the array of dbUnit data files; null is treated as
     *            none.
     * @param dataFilesName
     *            Concept name of the data files, e.g. prep, expected.
     * @param isCaseSensitiveTableNames
     *            true if case sensitive table names is on.
     * @return The composite dataset.
     * @throws DataSetException
     *             On dbUnit errors.
     */
    public IDataSet makeCompositeDataSet(final String[] dataFiles,
            final String dataFilesName, final boolean isCaseSensitiveTableNames)
            throws DataSetException
    {
        if (dataFileLoader == null)
        {
            throw new IllegalStateException(
                    "dataFileLoader is null; must configure or set it first");
        }

        final String[] files;
        if (dataFiles == null)
        {
            log.debug("makeCompositeDataSet: {} dataFiles is null, using none",
                    dataFilesName);
            files = new String[0];
        } else
        {
            files = dataFiles;
        }

        final int count = files.length;
        log.debug("makeCompositeDataSet: {} dataFiles count={}", dataFilesName,
                count);
        if (count == 0)
        {
            log.info("makeCompositeDataSet: Specified zero {} data files",
                    dataFilesName);
        }

        final IDataSet[] dataSets = new IDataSet[count];
        for (int i = 0; i < count; i++)
        {
            dataSets[i] = dataFileLoader.load(files[i]);
        }

        return new CompositeDataSet(dataSets, true, isCaseSensitiveTableNames);
    }

    /**
     * Apply the specified exclude and include column filters to the specified
     * table.
     *
     * @param table
     *            The table to apply the filters to.
     * @param excludeColumns
     *            The exclude filters; use null or empty array to mean exclude
     *            none.
     * @param includeColumns
     *            The include filters; use null or empty array to mean include
     *            all.
     * @return The filtered table.
     * @throws DataSetException if applying the filters fails.
     */
    public ITable applyColumnFilters(final ITable table,
            final String[] excludeColumns, final String[] includeColumns)
            throws DataSetException
    {
        if (table == null)
        {
            throw new IllegalArgumentException("table is null");
        }

        ITable filteredTable = table;

        // note: a filter with no include patterns accepts every column, so an
        // empty inclusion array includes all, the same as null
        if (includeColumns == null)
        {
            log.debug("applyColumnFilters: including columns=(all)");
        } else
        {
            log.debug("applyColumnFilters: including columns='{}'",
                    Arrays.toString(includeColumns));
        }

        if (excludeColumns == null || excludeColumns.length == 0)
        {
            log.debug("applyColumnFilters: excluding columns=(none)");
        } else
        {
            log.debug("applyColumnFilters: excluding columns='{}'",
                    Arrays.toString(excludeColumns));
        }

        // one filter over the unfiltered table, not one wrapper per filter: a
        // failure message looks a dropped column up only one wrapper deep
        if (includeColumns != null
                || (excludeColumns != null && excludeColumns.length > 0))
        {
            final DefaultColumnFilter columnFilter =
                    makeColumnFilter(excludeColumns, includeColumns);
            filteredTable = new ColumnFilterTable(table, columnFilter);
        }

        return filteredTable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDataSet getPrepDataset()
    {
        return prepDataSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDataSet getExpectedDataset()
    {
        return expectedDataSet;
    }

    /**
     * Get the databaseTester.
     *
     * @see #databaseTester
     *
     * @return The databaseTester.
     */
    public IDatabaseTester getDatabaseTester()
    {
        return databaseTester;
    }

    /**
     * Set the databaseTester. Replacing a different one first releases the
     * shared connection acquired from it, closing it only when
     * {@link #cleanupData()} would, and forgets what was resolved from it, so
     * the next step acquires its connection and case sensitivity setting from
     * the new one.
     *
     * @see #databaseTester
     *
     * @param databaseTester
     *            The databaseTester to set.
     */
    public void setDatabaseTester(final IDatabaseTester databaseTester)
    {
        if (databaseTester == this.databaseTester)
        {
            return;
        }

        releaseReplacedTestersConnection();
        this.databaseTester = databaseTester;
        this.cachedIsCaseSensitiveTableNames = null;
    }

    /**
     * Release the shared connection acquired from the databaseTester about to
     * be replaced, closing it only when this instance owns it, and forget it
     * so the next step acquires one from the new databaseTester. Also discards
     * the row count check baseline captured from that connection, which says
     * nothing about the new databaseTester's database. Called before
     * the field is reassigned because {@link ConnectionOwnership} reads the
     * outgoing databaseTester's listener to decide whether to close. A close
     * failure is logged, not thrown, as the connection is being discarded
     * either way.
     */
    private void releaseReplacedTestersConnection()
    {
        rowCountChecker.discardBaseline();
        if (!reusableConnectionHolder.isResolved())
        {
            return;
        }

        try
        {
            closeReusableConnection();
        } catch (final Exception e)
        {
            log.warn("setDatabaseTester: releasing the replaced tester's"
                    + " connection failed", e);
        }
        reusableConnectionHolder.discardOnFailure();
    }

    /**
     * Get whether the connection lookupFeatureValue() and cleanupData() are
     * done with is closed.
     *
     * @see #closeConnectionAfterTest
     *
     * @return True if it is closed, false if not.
     * @since 3.4.0
     */
    public boolean isCloseConnectionAfterTest()
    {
        return closeConnectionAfterTest;
    }

    /**
     * Gets whether cleanupData() leaves the connection this instance shares across a test's
     * lifecycle open for another owner to close. That is the case when
     * {@link #isCloseConnectionAfterTest()} is false, and when the databaseTester's operation
     * listener or this instance's own {@link #getOperationListener()} is
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER}. Whoever else holds that connection,
     * such as the executor of the annotation runtime that resolved it for a test method
     * parameter, must leave it open too.
     *
     * @return {@code true} when cleanupData() leaves the connection open, {@code false} when it
     *         may close it.
     * @since 3.6.0
     */
    public boolean isConnectionLeftOpen()
    {
        return connectionOwnership.isLeftOpenByConfiguration();
    }

    /**
     * Set whether the connection lookupFeatureValue() and cleanupData() are
     * done with is closed. Default is true. Set to false when databaseTester
     * shares a {@link org.dbunit.database.CachingConnectionProvider} across
     * test methods, so this instance does not close a connection other tests
     * still expect to reuse. False requires a databaseTester that hands out
     * the same connection on repeated calls: with one that hands out a new
     * connection each time, nothing closes the connection left open after a
     * test, and a step that fails leaves open the connection this instance
     * forgets. A warning is logged when databaseTester is a
     * {@link JdbcDatabaseTester} without a CachingConnectionProvider.
     *
     * @see #closeConnectionAfterTest
     *
     * @param closeConnectionAfterTest
     *            True to close it, false to leave it open.
     * @since 3.4.0
     */
    public void setCloseConnectionAfterTest(
            final boolean closeConnectionAfterTest)
    {
        this.closeConnectionAfterTest = closeConnectionAfterTest;
    }

    /**
     * Set the operation setupData() runs on the prep dataset, instead of the databaseTester's
     * own set up operation, which the databaseTester keeps unchanged. A caller that configures
     * one test case without touching the databaseTester other test cases share, such as the
     * annotation runtime for {@code @DbUnitSetUpOperation}, sets it here. A subclass overriding
     * {@link #getSetUpOperation()} decides for itself and ignores it.
     *
     * @see #setupData()
     *
     * @param setUpOperation
     *            The operation to run; null, the default, runs the databaseTester's own.
     * @since 3.6.0
     */
    public void setSetUpOperation(final DatabaseOperation setUpOperation)
    {
        this.setUpOperation = setUpOperation;
    }

    /**
     * Set the operation cleanupData() runs over the prep and expected datasets, instead of the
     * databaseTester's own tear down operation, which the databaseTester keeps unchanged. A
     * caller that configures one test case without touching the databaseTester other test cases
     * share, such as the annotation runtime for {@code @DbUnitTearDownOperation}, sets it
     * here. A subclass overriding {@link #getTearDownOperation()} decides for itself and
     * ignores it.
     *
     * @see #cleanupData()
     *
     * @param tearDownOperation
     *            The operation to run; null, the default, runs the databaseTester's own.
     * @since 3.6.0
     */
    public void setTearDownOperation(final DatabaseOperation tearDownOperation)
    {
        this.tearDownOperation = tearDownOperation;
    }

    /**
     * Get the dataFileLoader.
     *
     * @see #dataFileLoader
     *
     * @return The dataFileLoader.
     */
    public DataFileLoader getDataFileLoader()
    {
        return dataFileLoader;
    }

    /**
     * Set the dataFileLoader.
     *
     * @see #dataFileLoader
     *
     * @param dataFileLoader
     *            The dataFileLoader to set.
     */
    public void setDataFileLoader(final DataFileLoader dataFileLoader)
    {
        this.dataFileLoader = dataFileLoader;
    }

    /**
     * Set the prepDs.
     *
     * @see #prepDataSet
     *
     * @param prepDataSet
     *            The prepDs to set.
     */
    public void setPrepDs(final IDataSet prepDataSet)
    {
        this.prepDataSet = prepDataSet;
    }

    /**
     * Set the expectedDs.
     *
     * @see #expectedDataSet
     *
     * @param expectedDataSet
     *            The expectedDs to set.
     */
    public void setExpectedDs(final IDataSet expectedDataSet)
    {
        this.expectedDataSet = expectedDataSet;
    }

    /**
     * Get the tableDefs.
     *
     * @see #verifyTableDefs
     *
     * @return The tableDefs.
     */
    public VerifyTableDefinition[] getVerifyTableDefs()
    {
        return verifyTableDefs;
    }

    /**
     * Set the tableDefs.
     *
     * @see #verifyTableDefs
     *
     * @param verifyTableDefs
     *            The tableDefs to set.
     */
    public void setVerifyTableDefs(
            final VerifyTableDefinition[] verifyTableDefs)
    {
        this.verifyTableDefs = verifyTableDefs;
    }

    /**
     * Returns the verifier used to check that verify table definitions and the expected dataset agree.
     *
     * @return the verifier used to check that verify table definitions and the expected dataset agree.
     */
    public ExpectedDataSetAndVerifyTableDefinitionVerifier getExpectedDataSetAndVerifyTableDefinitionVerifier()
    {
        return expectedDataSetAndVerifyTableDefinitionVerifier;
    }

    /**
     * Sets the verifier used to check that verify table definitions and the expected dataset agree.
     *
     * @param expectedDataSetAndVerifyTableDefinitionVerifier the verifier to use.
     */
    public void setExpectedDataSetAndVerifyTableDefinitionVerifier(
            final ExpectedDataSetAndVerifyTableDefinitionVerifier expectedDataSetAndVerifyTableDefinitionVerifier)
    {
        this.expectedDataSetAndVerifyTableDefinitionVerifier =
                expectedDataSetAndVerifyTableDefinitionVerifier;
    }

    /**
     * Get the failureHandler.
     *
     * @see #failureHandler
     *
     * @return The failureHandler.
     * @since 3.5.0
     */
    public FailureHandler getFailureHandler()
    {
        return failureHandler;
    }

    /**
     * Set the failureHandler.
     *
     * @see #failureHandler
     *
     * @param failureHandler
     *            The failureHandler to set.
     * @since 3.5.0
     */
    public void setFailureHandler(final FailureHandler failureHandler)
    {
        this.failureHandler = failureHandler;
    }

    /**
     * Get the RowCountCheck in use.
     *
     * @see #rowCountChecker
     *
     * @return The RowCountCheck, or null if none has been resolved or set yet.
     * @since 3.6.0
     */
    public RowCountCheck getRowCountCheck()
    {
        return rowCountChecker.getRowCountCheck();
    }

    /**
     * Set the RowCountCheck, overriding the one otherwise lazily built from the shared
     * connection's DatabaseConfig.
     *
     * @see #rowCountChecker
     *
     * @param rowCountCheck
     *            The RowCountCheck to use.
     * @since 3.6.0
     */
    public void setRowCountCheck(final RowCountCheck rowCountCheck)
    {
        rowCountChecker.setRowCountCheck(rowCountCheck);
    }

    /**
     * Set the enabled flag and excluded table patterns to resolve a RowCountCheck from, instead
     * of the shared connection's DatabaseConfig - the values an annotation such as
     * {@code @DbUnitRowCountCheck} declares.
     *
     * @see #rowCountChecker
     *
     * @param enabled
     *            Whether the check is enabled.
     * @param exclude
     *            The excluded table patterns; null is treated as empty (excludes none).
     * @since 3.6.0
     */
    public void setRowCountCheckOverride(final boolean enabled, final String[] exclude)
    {
        rowCountChecker.setEnabledOverride(enabled, exclude);
    }

    /**
     * Clear a previously set enabled flag and excluded table patterns override, returning to
     * resolving a RowCountCheck from the shared connection's DatabaseConfig.
     *
     * <p>A caller reusing one instance across several tests - e.g. one held by a
     * {@code @DbUnitTestCase} static field - must call this once a test that set an override is
     * over, so that test's override does not silently carry over onto the next one.
     *
     * @see #rowCountChecker
     * @since 3.6.0
     */
    public void clearRowCountCheckOverride()
    {
        rowCountChecker.clearEnabledOverride();
    }

    /**
     * Get the DatabaseConfig property name/value pairs applied to the connection shared by
     * setupData(), verifyData() and cleanupData().
     *
     * @see #setDatabaseConfigProperties(Properties)
     *
     * @return A copy of the properties, or null if none are set.
     * @since 3.6.0
     */
    public Properties getDatabaseConfigProperties()
    {
        if (databaseConfigProperties == null)
        {
            return null;
        }

        final Properties copy = new Properties();
        copy.putAll(databaseConfigProperties);
        return copy;
    }

    /**
     * Set DatabaseConfig property name/value pairs to apply to the connection shared by
     * setupData(), verifyData() and cleanupData(), every time {@link #getConnection()}
     * resolves it.
     *
     * <p>
     * Puts back, first, whatever the previous properties replaced, so that changing them - as
     * the annotation runtime does to restore an injected instance's own properties after a
     * test - leaves nothing of the old values on a connection kept open.
     *
     * @see #databaseConfigProperties
     *
     * @param databaseConfigProperties
     *            The properties to apply; null or empty applies none.
     * @since 3.6.0
     */
    public void setDatabaseConfigProperties(final Properties databaseConfigProperties)
    {
        restoreDatabaseConfig();
        if (databaseConfigProperties == null)
        {
            this.databaseConfigProperties = null;
            this.databaseConfigOverrides = new DatabaseConfigOverrides(new Properties());
        } else
        {
            final Properties copy = new Properties();
            copy.putAll(databaseConfigProperties);
            this.databaseConfigProperties = copy;
            this.databaseConfigOverrides = new DatabaseConfigOverrides(copy);
        }
    }
}
