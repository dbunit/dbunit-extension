package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.util.Locale;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.MockDatabaseConnection;
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
        // 1 close from configureTest's case-sensitivity feature lookup
        // (self-contained, unchanged); setupData()'s CLEAN_INSERT acquires
        // the connection shared with verifyData()/cleanupData() but leaves
        // it open since cleanupData() has not run yet to close it (#800)
        connection.setExpectedCloseCalls(1);
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
        // verifyData is skipped, so no connection is shared/acquired for
        // cleanupData() to later close; the single close is cleanupData's
        // own fallback case-sensitivity feature lookup (configureTest() was
        // not called), and its tearDownOperation defaults to NONE so no
        // connection is acquired for tear down either
        connection.setExpectedCloseCalls(1);
        connection.verify();
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
    void testVerifyData_withTurkishDefaultLocale_matchesAsciiIColumns()
            throws Exception
    {
        final Locale original = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try
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
        } finally
        {
            Locale.setDefault(original);
        }
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
        // own case-sensitivity feature lookup (1 close); separately, it
        // closes the connection it acquired to run the DELETE_ALL tear down
        // operation (1 close) (#800)
        connection.setExpectedCloseCalls(2);
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

        // configureTest() acquires its own self-contained connection
        // (1 call); setupData() acquires a second connection, reused by
        // verifyData() and by cleanupData()'s DELETE_ALL tear down
        // operation, instead of a fresh connection at each of those steps
        // (#800)
        Mockito.verify(spyDatabaseTester, Mockito.times(2)).getConnection();
        // 1 close from configureTest's feature lookup, 1 from cleanupData()
        // closing the connection shared across setup/verify/tear down
        connection.setExpectedCloseCalls(2);
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
