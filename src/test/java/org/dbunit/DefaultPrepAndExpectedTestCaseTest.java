package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.util.Collections;
import java.util.Properties;

import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.RowCountCheck;
import org.dbunit.database.rowcount.RowCountDifference;
import org.dbunit.database.rowcount.RowCountSnapshot;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.database.statement.IBatchStatement;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ClearRowCountCheckSystemProperties
class DefaultPrepAndExpectedTestCaseTest
{
    @Mock
    private Connection mockConnection;

    private static final String PREP_DATA_FILE_NAME =
            "/xml/flatXmlDataSetTest.xml";
    private static final String EXP_DATA_FILE_NAME =
            "/xml/flatXmlDataSetTest.xml";

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();
    private IDatabaseTester databaseTester;
    private DefaultPrepAndExpectedTestCase tc;

    @BeforeEach
    void setUp()
    {
        // built here, not via field initializers, because @Mock injection
        // (MockitoExtension) runs after field initializers but before
        // @BeforeEach
        databaseTester = makeDatabaseTester();
        tc = new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester);
    }

    @Test
    void testConfigureTest_withTablesAndDataFiles_setsConfiguredState() throws Exception
    {
        final String[] prepDataFiles = {PREP_DATA_FILE_NAME};
        final String[] expectedDataFiles = {EXP_DATA_FILE_NAME};
        final VerifyTableDefinition[] tables = {};

        tc.configureTest(tables, prepDataFiles, expectedDataFiles);

        assertThat(tables).as("Configured tables do not match expected.")
                .isEqualTo(tc.getVerifyTableDefs());

        final IDataSet expPrepDs = dataFileLoader.load(PREP_DATA_FILE_NAME);
        Assertion.assertEquals(expPrepDs, tc.getPrepDataset());

        final IDataSet expExpDs = dataFileLoader.load(EXP_DATA_FILE_NAME);
        Assertion.assertEquals(expExpDs, tc.getExpectedDataset());
    }

    @Test
    void testConfigureTest_calledAlone_leavesConnectionOpen() throws Exception
    {
        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // configureTest() leaves the connection open for setupData()/
        // verifyData()/cleanupData() to reuse, aligning it with those other
        // lifecycle methods; called standalone here, cleanupData() never
        // runs (#800, #825)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testConfigureTest_withDatabaseConfigPropertiesSet_appliesThemToTheSharedConnection()
            throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty("batchSize", "50");
        tc.setDatabaseConfigProperties(properties);

        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        assertThat(connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("setDatabaseConfigProperties() values must reach the connection shared by"
                        + " setupData()/verifyData()/cleanupData(), the same way overriding"
                        + " setUpDatabaseConfig() would for a subclass.")
                .isEqualTo(50);
    }

    @Test
    void testSetDatabaseConfigProperties_callerMutatesPropertiesAfterward_doesNotAffectConfiguredValue()
            throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty("batchSize", "50");
        tc.setDatabaseConfigProperties(properties);

        properties.setProperty("batchSize", "999");

        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        assertThat(connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("setDatabaseConfigProperties() must copy the given Properties so a caller"
                        + " mutating its own instance afterward cannot silently change what"
                        + " was already configured.")
                .isEqualTo(50);
    }

    @Test
    void testConfigureTest_withInvalidDatabaseConfigProperty_throwsIllegalStateException()
    {
        final Properties properties = new Properties();
        properties.setProperty("datatypeFactory", "not.a.real.ClassName");
        tc.setDatabaseConfigProperties(properties);

        final Throwable thrown = catchThrowable(() -> tc.configureTest(
                new VerifyTableDefinition[] {}, new String[] {}, new String[] {}));

        assertThat(thrown).as("An invalid property name must be reported clearly, not left as"
                + " a raw DatabaseUnitException surfacing from deep inside connection"
                + " resolution.").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testPreTest_withTablesAndDataFiles_configuresDatasetAndExecutesSetUpOperation()
            throws Exception
    {
        final VerifyTableDefinition[] tables = {};
        final String[] prepDataFiles = {};
        final String[] expectedDataFiles = {};

        tc.preTest(tables, prepDataFiles, expectedDataFiles);

        assertThat(tc.getVerifyTableDefs())
                .as("Configured tables do not match expected.")
                .isEqualTo(tables);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // configureTest()'s case-sensitivity feature lookup and setupData()'s
        // CLEAN_INSERT now share the same connection instead of configureTest
        // closing its own and setupData() reacquiring a second one; it stays
        // open since cleanupData() has not run yet to close it (#800, #825)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testRunTest_withTestSteps_executesStepsAndReturnsTrueResult() throws Exception
    {
        final VerifyTableDefinition[] tables = {};
        final String[] prepDataFiles = {};
        final String[] expectedDataFiles = {};
        final PrepAndExpectedTestCaseSteps testSteps = () -> {
            System.out.println("This message represents the test steps.");
            return Boolean.TRUE;
        };

        final Boolean actual = (Boolean) tc.runTest(tables, prepDataFiles,
                expectedDataFiles, testSteps);
        assertThat(actual).as("Did not receive expected value from runTest().")
                .isTrue();
    }

    @Test
    void testRunTest_whenTestStepsAndCleanupBothFail_throwsTestFailureWithCleanupSuppressed()
            throws Exception
    {
        final RuntimeException cleanupFailure =
                new RuntimeException("cleanup boom");
        final DefaultPrepAndExpectedTestCase throwingTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader,
                        databaseTester)
                {
                    @Override
                    public void cleanupData() throws Exception
                    {
                        throw cleanupFailure;
                    }
                };

        final RuntimeException testFailure = new RuntimeException("test boom");
        final PrepAndExpectedTestCaseSteps steps = () -> {
            throw testFailure;
        };

        final Throwable thrown = catchThrowable(() -> throwingTc.runTest(
                new VerifyTableDefinition[] {}, new String[] {}, new String[] {},
                steps));

        assertThat(thrown)
                .as("runTest() must rethrow the original test failure, not the"
                        + " cleanup failure.")
                .isSameAs(testFailure);
        assertThat(thrown.getSuppressed())
                .as("The cleanup failure must be attached as suppressed, not lost.")
                .containsExactly(cleanupFailure);
    }

    @Test
    void testPostTest_withVerifyDataDefaultTrue_verifiesDataAndClosesConnectionOnce()
            throws Exception
    {
        tc.postTest();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // configureTest() was not called, so verifyData() is the first to
        // acquire the shared connection; cleanupData()'s fallback feature
        // lookup then finds it already acquired and reuses it rather than
        // opening a separate one, so cleanupData()'s own close is the only
        // close for the whole lifecycle (#801)
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testPostTest_withVerifyDataFalse_skipsVerifyAndOnlyRunsCleanup()
            throws Exception
    {
        tc.postTest(false);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // verifyData is skipped, so cleanupData()'s own fallback
        // case-sensitivity feature lookup (configureTest() was not called)
        // is the first to acquire a connection; its tearDownOperation
        // defaults to NONE so no further connection use happens, and
        // cleanupData()'s final close is the single close for the lifecycle
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testPostTest_whenVerifyFailsAndCleanupFails_throwsCleanupFailureWithVerifySuppressed()
            throws Exception
    {
        final Error verifyFailure = new AssertionError("verify boom");
        final RuntimeException cleanupFailure =
                new RuntimeException("cleanup boom");
        final DefaultPrepAndExpectedTestCase throwingTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader,
                        databaseTester)
                {
                    @Override
                    public void verifyData() throws Exception
                    {
                        throw verifyFailure;
                    }

                    @Override
                    public void cleanupData() throws Exception
                    {
                        throw cleanupFailure;
                    }
                };

        final Throwable thrown =
                catchThrowable(() -> throwingTc.postTest(true));

        assertThat(thrown)
                .as("postTest() must rethrow the cleanup failure, which"
                        + " deliberately shadows the verify failure to"
                        + " signal an unknown database state.")
                .isSameAs(cleanupFailure);
        assertThat(thrown.getSuppressed())
                .as("The shadowed verify failure must be attached as"
                        + " suppressed instead of lost.")
                .containsExactly(verifyFailure);
    }

    @Test
    void testPostTest_whenVerifyFailsAndCleanupThrowsError_throwsCleanupErrorWithVerifySuppressed()
            throws Exception
    {
        final RuntimeException verifyFailure =
                new RuntimeException("verify boom");
        final Error cleanupFailure = new AssertionError("cleanup boom");
        final DefaultPrepAndExpectedTestCase throwingTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader,
                        databaseTester)
                {
                    @Override
                    public void verifyData() throws Exception
                    {
                        throw verifyFailure;
                    }

                    @Override
                    public void cleanupData() throws Exception
                    {
                        throw cleanupFailure;
                    }
                };

        final Throwable thrown =
                catchThrowable(() -> throwingTc.postTest(true));

        assertThat(thrown)
                .as("postTest() must rethrow the cleanup Error, which"
                        + " deliberately shadows the verify failure to"
                        + " signal an unknown database state, the same as"
                        + " when cleanup throws a checked Exception.")
                .isSameAs(cleanupFailure);
        assertThat(thrown.getSuppressed())
                .as("The shadowed verify failure must be attached as"
                        + " suppressed instead of lost, even when the"
                        + " cleanup failure is an Error rather than an"
                        + " Exception.")
                .containsExactly(verifyFailure);
    }

    @Test
    void testPostTest_whenVerifyFailsAndCleanupSucceeds_throwsVerifyFailureUnchanged()
            throws Exception
    {
        final Error verifyFailure = new AssertionError("verify boom");
        final DefaultPrepAndExpectedTestCase throwingTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader,
                        databaseTester)
                {
                    @Override
                    public void verifyData() throws Exception
                    {
                        throw verifyFailure;
                    }
                };

        final Throwable thrown =
                catchThrowable(() -> throwingTc.postTest(true));

        assertThat(thrown)
                .as("postTest() must rethrow the verify failure unchanged"
                        + " when cleanup succeeds.")
                .isSameAs(verifyFailure);
        assertThat(thrown.getSuppressed())
                .as("No exception is suppressed when cleanup succeeds.")
                .isEmpty();
    }

    @Test
    void testPostTest_whenVerifyAndCleanupBothSucceed_doesNotThrow()
    {
        assertThatCode(() -> tc.postTest(true))
                .as("postTest() must not throw when both verifyData() and"
                        + " cleanupData() succeed.")
                .doesNotThrowAnyException();
    }

    @Test
    void testSetupData_withDefaultConfiguration_executesSetUpOperation()
            throws Exception
    {
        tc.setupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // setupData() leaves the connection open for verifyData()/
        // cleanupData() to reuse; only cleanupData() closes it (#800)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testSetupData_withUserDefinedOperationListener_invokesConnectionRetrievedButNotSetUpFinished()
            throws Exception
    {
        final IOperationListener mockOperationListener =
                Mockito.mock(IOperationListener.class);
        final DefaultPrepAndExpectedTestCase listenerTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader,
                        databaseTester)
                {
                    @Override
                    protected IOperationListener getOperationListener()
                    {
                        return mockOperationListener;
                    }
                };

        listenerTc.setupData();

        Mockito.verify(mockOperationListener)
                .connectionRetrieved(Mockito.any(IDatabaseConnection.class));
        Mockito.verify(mockOperationListener, Mockito.never())
                .operationSetUpFinished(Mockito.any(IDatabaseConnection.class));

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // A user-defined listener's connectionRetrieved() must still run
        // (e.g. for DatabaseConfig setup), but operationSetUpFinished() must
        // stay suppressed regardless of what the configured listener would
        // otherwise do with it (the default listener closes on it), since
        // this instance owns the shared connection's lifecycle (#801)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testVerifyData_withNoVerifyTableDefinitions_completesWithoutThrowing()
            throws Exception
    {
        tc.verifyData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // verifyData() leaves the connection open for cleanupData() to
        // close; called standalone here, cleanupData() never runs (#800)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testVerifyData_withTwoTablesAndColumnFilters_passesWhenEqual()
            throws Exception
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR),
                new Column("COL2", DataType.VARCHAR),
                new Column("COL3", DataType.VARCHAR)};

        final DefaultTable expectedTable = new DefaultTable("TEST_TABLE", columns);
        expectedTable.addRow(new Object[] {"a", "b", "expected-only"});

        final DefaultTable actualTable = new DefaultTable("TEST_TABLE", columns);
        actualTable.addRow(new Object[] {"a", "b", "actual-only"});

        final String[] excludeColumns = {"COL3"};
        final String[] includeColumns = null;

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable,
                excludeColumns, includeColumns, null, null))
                        .as("Tables differing only in an excluded column must verify as equal.")
                        .doesNotThrowAnyException();
    }

    @Test
    void testVerifyData_withSortOnFilteredColumnsOnlyFalse_generatedIdOutOfDataOrder_throwsOnMismatch()
            throws Exception
    {
        final Column[] actualColumns = {new Column("ID", DataType.INTEGER),
                new Column("COL1", DataType.VARCHAR)};
        final DefaultTable actualTable =
                new DefaultTable("TEST_TABLE", actualColumns);
        actualTable.addRow(new Object[] {2, "Apple"});
        actualTable.addRow(new Object[] {1, "Banana"});

        final Column[] expectedColumns = {new Column("COL1", DataType.VARCHAR)};
        final DefaultTable expectedTable =
                new DefaultTable("TEST_TABLE", expectedColumns);
        expectedTable.addRow(new Object[] {"Apple"});
        expectedTable.addRow(new Object[] {"Banana"});

        final String[] excludeColumns = {"ID"};

        final Throwable thrown = catchThrowable(() -> tc.verifyData(
                expectedTable, actualTable, excludeColumns, null, null, null,
                false));

        assertThat(thrown)
                .as("Default sortOnFilteredColumnsOnly=false must sort the"
                        + " actual table primarily by its excluded ID column,"
                        + " misaligning same-data rows and failing the"
                        + " comparison.")
                .isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testVerifyData_withSortOnFilteredColumnsOnlyTrue_generatedIdOutOfDataOrder_passesWhenEqual()
            throws Exception
    {
        final Column[] actualColumns = {new Column("ID", DataType.INTEGER),
                new Column("COL1", DataType.VARCHAR)};
        final DefaultTable actualTable =
                new DefaultTable("TEST_TABLE", actualColumns);
        actualTable.addRow(new Object[] {2, "Apple"});
        actualTable.addRow(new Object[] {1, "Banana"});

        final Column[] expectedColumns = {new Column("COL1", DataType.VARCHAR)};
        final DefaultTable expectedTable =
                new DefaultTable("TEST_TABLE", expectedColumns);
        expectedTable.addRow(new Object[] {"Apple"});
        expectedTable.addRow(new Object[] {"Banana"});

        final String[] excludeColumns = {"ID"};

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable,
                excludeColumns, null, null, null, true))
                        .as("sortOnFilteredColumnsOnly=true must sort both"
                                + " tables by only COL1, ignoring the excluded"
                                + " ID column, so same-data rows line up"
                                + " regardless of ID order.")
                        .doesNotThrowAnyException();
    }

    @Test
    void testVerifyData_withSortOnFilteredColumnsOnlyTrueAndAllColumnsExcluded_doesNotThrow()
            throws Exception
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR)};

        final DefaultTable expectedTable =
                new DefaultTable("TEST_TABLE", columns);
        expectedTable.addRow(new Object[] {"expected-only"});

        final DefaultTable actualTable = new DefaultTable("TEST_TABLE", columns);
        actualTable.addRow(new Object[] {"actual-only"});

        final String[] excludeColumns = {"COL1"};

        // Excluding every column leaves makeSortColumns() with nothing to
        // sort by; confirm SortedTable tolerates a zero-length sort-column
        // list (a no-op, stable sort) instead of throwing.
        assertThatCode(() -> tc.verifyData(expectedTable, actualTable,
                excludeColumns, null, null, null, true))
                        .as("Excluding every column must not throw even"
                                + " though it leaves no columns to sort by.")
                        .doesNotThrowAnyException();
    }

    @Test
    @TurkishDefaultLocale
    void testVerifyData_withTurkishDefaultLocale_matchesAsciiIColumns()
            throws Exception
    {
        final Column[] actualColumns = {new Column("ID", DataType.VARCHAR)};
        final DefaultTable actualTable =
                new DefaultTable("TEST_TABLE", actualColumns);
        actualTable.addRow(new Object[] {"1"});

        // expected column is DataType.UNKNOWN, as expected files normally
        // are, so verifyData() must merge in the actual column via
        // case-insensitive name matching; a Turkish default locale's
        // dotless-i breaks that match ("ID".toLowerCase() becomes "ıd")
        // unless the match pins Locale.ENGLISH
        final Column[] expectedColumns =
                {new Column("id", DataType.UNKNOWN)};
        final DefaultTable expectedTable =
                new DefaultTable("TEST_TABLE", expectedColumns);
        expectedTable.addRow(new Object[] {"1"});

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable,
                null, null, null, null))
                        .as("Column matching must use Locale.ENGLISH so a"
                                + " Turkish default locale does not break"
                                + " case-insensitive column matching.")
                        .doesNotThrowAnyException();
    }

    @Test
    void testGetFailureHandler_withDefaultConfiguration_returnsNull()
    {
        assertThat(tc.getFailureHandler())
                .as("Default must be null so verifyData() keeps using"
                        + " Assertion's own default FailureHandler, matching"
                        + " pre-existing behavior for callers who have not"
                        + " configured a custom FailureHandler.")
                .isNull();
    }

    @Test
    void testVerifyData_withMismatchAndNoFailureHandlerConfigured_throwsError()
            throws Exception
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR)};

        final DefaultTable expectedTable =
                new DefaultTable("TEST_TABLE", columns);
        expectedTable.addRow(new Object[] {"expected"});

        final DefaultTable actualTable =
                new DefaultTable("TEST_TABLE", columns);
        actualTable.addRow(new Object[] {"actual"});

        final Throwable thrown = catchThrowable(() -> tc.verifyData(
                expectedTable, actualTable, null, null, null, null));

        assertThat(thrown)
                .as("Without a configured FailureHandler, verifyData() must"
                        + " keep failing fast on the first mismatch, matching"
                        + " pre-existing behavior.")
                .isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testVerifyData_withMismatchAndDiffCollectingFailureHandlerConfigured_collectsDifferenceInsteadOfThrowing()
            throws Exception
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR)};

        final DefaultTable expectedTable =
                new DefaultTable("TEST_TABLE", columns);
        expectedTable.addRow(new Object[] {"expected"});

        final DefaultTable actualTable =
                new DefaultTable("TEST_TABLE", columns);
        actualTable.addRow(new Object[] {"actual"});

        final DiffCollectingFailureHandler diffCollectingFailureHandler =
                new DiffCollectingFailureHandler();
        tc.setFailureHandler(diffCollectingFailureHandler);

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable, null,
                null, null, null))
                        .as("A configured FailureHandler that collects"
                                + " differences instead of throwing must be"
                                + " used instead of the default fail-fast"
                                + " handler.")
                        .doesNotThrowAnyException();

        assertThat(diffCollectingFailureHandler.getDiffList())
                .as("The mismatch must be recorded by the configured"
                        + " DiffCollectingFailureHandler.")
                .hasSize(1);
    }

    @Test
    void testCleanupData_withDeleteAllTearDownOperation_executesTearDownOperation()
            throws Exception
    {
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // configureTest() was not called, so cleanupData() falls back to its
        // own case-sensitivity feature lookup; that connection is left open
        // for the DELETE_ALL tear down operation to reuse instead of
        // reacquiring a second one, so cleanupData()'s final close is the
        // only close for the whole call (#800, #825)
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testCleanupData_withUserDefinedOperationListener_invokesConnectionRetrievedButNotTearDownFinished()
            throws Exception
    {
        // executeOperation() only calls the listener when the tear down
        // operation is not NONE (the untouched default)
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final IOperationListener mockOperationListener =
                Mockito.mock(IOperationListener.class);
        final DefaultPrepAndExpectedTestCase listenerTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader,
                        databaseTester)
                {
                    @Override
                    protected IOperationListener getOperationListener()
                    {
                        return mockOperationListener;
                    }
                };

        listenerTc.cleanupData();

        Mockito.verify(mockOperationListener)
                .connectionRetrieved(Mockito.any(IDatabaseConnection.class));
        Mockito.verify(mockOperationListener, Mockito.never())
                .operationTearDownFinished(
                        Mockito.any(IDatabaseConnection.class));

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // cleanupData() itself closes the shared connection exactly once
        // (closeReusableConnection()); operationTearDownFinished() must stay
        // suppressed so a configured listener cannot also close it (#801)
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testVerifyData_withVerifyTableDefinitions_verifiesActualTableFromConnection()
            throws Exception
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow(new Object[] {"a"});

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setupDataSet(table);

        tc.setExpectedDs(new DefaultDataSet(table));
        tc.setVerifyTableDefs(new VerifyTableDefinition[] {
                new VerifyTableDefinition("TEST_TABLE", new String[] {})});

        assertThatCode(() -> tc.verifyData())
                .as("verifyData() must verify the actual table read from"
                        + " the connection.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRunTest_withNonDefaultTearDown_reusesOneConnectionAcrossLifecycle()
            throws Exception
    {
        final IDatabaseTester spyDatabaseTester = Mockito.spy(databaseTester);
        tc.setDatabaseTester(spyDatabaseTester);
        spyDatabaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);

        final Column[] columns = {new Column("COL1", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow(new Object[] {"a"});
        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setupDataSet(table);

        final VerifyTableDefinition[] tables = {
                new VerifyTableDefinition("TEST_TABLE", new String[] {})};
        tc.configureTest(tables, new String[] {}, new String[] {});
        // configureTest() built an empty expected dataset from the (empty)
        // expectedDataFiles array above; replace it with one that matches
        // the actual table so verifyData() passes
        tc.setExpectedDs(new DefaultDataSet(table));

        tc.preTest();
        tc.postTest();

        // configureTest() acquires the one connection reused by setupData(),
        // verifyData(), and by cleanupData()'s DELETE_ALL tear down
        // operation, instead of configureTest closing its own and each
        // later step reacquiring (#800, #825)
        Mockito.verify(spyDatabaseTester, Mockito.times(1)).getConnection();
        // cleanupData()'s final close is the only close across the whole
        // configureTest/setupData/verifyData/cleanupData lifecycle
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testIsCloseConnectionAfterTest_withDefaultConfiguration_returnsTrue()
    {
        assertThat(tc.isCloseConnectionAfterTest())
                .as("Default must close the connection, matching pre-existing"
                        + " behavior for callers who have not opted into a"
                        + " shared CachingConnectionProvider.")
                .isTrue();
    }

    @Test
    void testConfigureTest_withCloseConnectionAfterTestFalse_leavesConnectionOpen()
            throws Exception
    {
        tc.setCloseConnectionAfterTest(false);

        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // with closing disabled, configureTest()'s case-sensitivity feature
        // lookup must leave a shared CachingConnectionProvider's connection
        // open for other tests to keep reusing (#801)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testCleanupData_withCloseConnectionAfterTestFalse_leavesConnectionOpen()
            throws Exception
    {
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        tc.setCloseConnectionAfterTest(false);

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // with closing disabled, neither cleanupData's fallback feature
        // lookup nor its own closeReusableConnection() may close the
        // connection a shared CachingConnectionProvider is still using (#801)
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testCleanupData_checkDisabled_doesNotCaptureABaseline() throws Exception
    {
        // MockDatabaseConnection's dataset/getRowCount() are unconfigured here, so a real
        // capture()/verify() attempt would throw; reaching the end proves the disabled
        // default RowCountCheck no-opped instead of querying the connection (#939)
        tc.preTest();
        tc.cleanupData();

        assertThat(tc.getRowCountCheck())
                .as("preTest() must still lazily resolve a RowCountCheck even though the"
                        + " check is disabled, proving the disabled path was actually"
                        + " exercised rather than skipped outright.")
                .isNotNull();
    }

    @Test
    void testSetRowCountCheckOverride_disabled_winsOverConnectionsOwnEnabledConfig()
            throws Exception
    {
        // MockDatabaseConnection's dataset/getRowCount() are unconfigured here, so a real
        // capture() attempt would throw; reaching the end proves the override actually
        // disabled the check rather than the connection's own FEATURE_ROW_COUNT_CHECK - true
        // here - winning instead.
        databaseTester.getConnection().getConfig()
                .setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        tc.setRowCountCheckOverride(false, new String[0]);

        tc.preTest();
        tc.cleanupData();

        assertThat(tc.getRowCountCheck())
                .as("preTest() must still lazily resolve a RowCountCheck from the override,"
                        + " proving the override path was actually exercised rather than"
                        + " skipped outright.")
                .isNotNull();
    }

    @Test
    void testCleanupData_noBaselineCaptured_skipsTheCheck() throws Exception
    {
        final RowCountCheck mockRowCountCheck = Mockito.mock(RowCountCheck.class);
        tc.setRowCountCheck(mockRowCountCheck);

        // cleanupData() called directly, without preTest() first, so no baseline was captured
        tc.cleanupData();

        Mockito.verify(mockRowCountCheck, Mockito.never())
                .verify(Mockito.any(), Mockito.any());
    }

    @Test
    void testPostTest_testStepsFailed_skipsTheCheck() throws Exception
    {
        final RowCountCheck mockRowCountCheck = Mockito.mock(RowCountCheck.class);
        final RowCountSnapshot baseline =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        Mockito.when(mockRowCountCheck.capture(Mockito.any())).thenReturn(baseline);
        tc.setRowCountCheck(mockRowCountCheck);

        tc.preTest();
        tc.postTest(false);

        Mockito.verify(mockRowCountCheck, Mockito.never())
                .verify(Mockito.any(), Mockito.any());
    }

    @Test
    void testCleanupData_rowCountChanged_throwsAndStillClosesTheConnection() throws Exception
    {
        final RowCountCheck mockRowCountCheck = Mockito.mock(RowCountCheck.class);
        final RowCountSnapshot baseline =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT_AUDIT", 0));
        Mockito.when(mockRowCountCheck.capture(Mockito.any())).thenReturn(baseline);
        final UnexpectedRowCountException failure = new UnexpectedRowCountException(
                Collections.singletonList(new RowCountDifference("ACCOUNT_AUDIT", 0, 3)));
        Mockito.doThrow(failure).when(mockRowCountCheck).verify(Mockito.any(), Mockito.any());
        tc.setRowCountCheck(mockRowCountCheck);
        tc.preTest();

        assertThat(catchThrowable(() -> tc.cleanupData()))
                .as("A row count difference detected during cleanup must propagate as-is,"
                        + " the same as any other cleanupData() failure.")
                .isSameAs(failure);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // the existing catch block in cleanupData() must still close the connection even
        // though the row count check, not the tear down operation, is what failed (#939)
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testConfigureTestThenSetupData_withCloseDisabledNoProvider_doesNotReacquireConnection()
            throws Exception
    {
        final IDatabaseTester spyDatabaseTester = Mockito.spy(databaseTester);
        tc.setDatabaseTester(spyDatabaseTester);
        tc.setCloseConnectionAfterTest(false);

        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});
        tc.setupData();

        // closeReusableConnection() must keep the connection field set (not
        // null it out) when it skips closing, or setupData() would silently
        // orphan the connection configureTest() acquired and open a second
        // one - a real leak for anyone who sets this flag without also
        // pairing a CachingConnectionProvider (#801)
        Mockito.verify(spyDatabaseTester, Mockito.times(1)).getConnection();
    }

    @Test
    void testMakeCompositeDataSet_withDataFiles_returnsDataSetWithMatchingTableNames()
            throws Exception
    {
        final String[] dataFiles = {PREP_DATA_FILE_NAME};

        final IDataSet actual = tc.makeCompositeDataSet(dataFiles, "test");

        final IDataSet expected = dataFileLoader.load(PREP_DATA_FILE_NAME);
        assertThat(actual.getTableNames())
                .as("Composite dataset table names do not match expected.")
                .isEqualTo(expected.getTableNames());
    }

    @Test
    void testApplyColumnFiltersBothNull_withNoFilters_returnsAllColumnsUnfiltered()
            throws DataSetException
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR),
                new Column("COL2", DataType.VARCHAR),
                new Column("COL3", DataType.VARCHAR)};
        final ITable table = new DefaultTable("test_table", columns);
        final String[] excludeColumns = null;
        final String[] includeColumns = null;

        final ITable filtered =
                tc.applyColumnFilters(table, excludeColumns, includeColumns);

        assertThat(filtered.getTableMetaData().getColumns())
                .as("Columns should be unfiltered when both exclude and include are null.")
                .isEqualTo(columns);
    }

    @Test
    void testApplyColumnFiltersBothNotNull_withExcludeAndInclude_includeAppliesBeforeExclude()
            throws DataSetException
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR),
                new Column("COL2", DataType.VARCHAR),
                new Column("COL3", DataType.VARCHAR)};
        final ITable table = new DefaultTable("test_table", columns);
        final String[] excludeColumns = {"COL1"};
        final String[] includeColumns = {"COL2"};

        final ITable filtered =
                tc.applyColumnFilters(table, excludeColumns, includeColumns);

        assertThat(filtered.getTableMetaData().getColumns())
                .as("Include is applied before exclude, so only the included COL2 should remain.")
                .containsExactly(new Column("COL2", DataType.VARCHAR));
    }

    private IDatabaseTester makeDatabaseTester()
    {
        final IDatabaseConnection databaseConnection = makeDatabaseConnection();
        return new DefaultDatabaseTester(databaseConnection);
    }

    protected IDatabaseConnection makeDatabaseConnection()
    {
        final MockStatementFactory mockStatementFactory =
                new MockStatementFactory();
        final IBatchStatement mockBatchStatement = new MockBatchStatement();
        mockStatementFactory.setupStatement(mockBatchStatement);

        final MockDatabaseConnection mockDbConnection =
                new MockDatabaseConnection();
        mockDbConnection.setupConnection(mockConnection);
        mockDbConnection.setupStatementFactory(mockStatementFactory);

        final DatabaseConfig config = mockDbConnection.getConfig();
        config.setFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES,
                true);

        return mockDbConnection;
    }
}
