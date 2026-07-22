package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.statement.IBatchStatement;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
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
        // 1 close from configureTest's case-sensitivity feature lookup,
        // 1 close from the CLEAN_INSERT set up operation
        connection.setExpectedCloseCalls(2);
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
    void testPostTest_withVerifyDataDefaultTrue_verifiesDataAndClosesConnectionOnce()
            throws Exception
    {
        tc.postTest();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // 1 close from verifyData's own connection use,
        // 1 close from cleanupData's case-sensitivity feature lookup
        connection.setExpectedCloseCalls(2);
        connection.verify();
    }

    @Test
    void testPostTest_withVerifyDataFalse_skipsVerifyAndOnlyRunsCleanup()
            throws Exception
    {
        tc.postTest(false);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // verifyData is skipped; the single close is cleanupData's
        // case-sensitivity feature lookup (tearDownOperation defaults to NONE)
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
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testVerifyData_withNoVerifyTableDefinitions_completesWithoutThrowing()
            throws Exception
    {
        tc.verifyData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(1);
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
    void testCleanupData_withDeleteAllTearDownOperation_executesTearDownOperation()
            throws Exception
    {
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // 1 close from cleanupData's case-sensitivity feature lookup,
        // 1 close from the DELETE_ALL tear down operation
        connection.setExpectedCloseCalls(2);
        connection.verify();
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

    // TODO implement test - doesn't test anything yet
    @Test
    void testApplyColumnFiltersBothNull() throws DataSetException
    {
        final ITable table = new DefaultTable("test_table");
        final String[] excludeColumns = null;
        final String[] includeColumns = null;
        tc.applyColumnFilters(table, excludeColumns, includeColumns);
    }

    // TODO implement test - doesn't test anything yet
    @Test
    void testApplyColumnFiltersBothNotNull() throws DataSetException
    {
        final ITable table = new DefaultTable("test_table");
        final String[] excludeColumns = {"COL1"};
        final String[] includeColumns = {"COL2"};
        tc.applyColumnFilters(table, excludeColumns, includeColumns);
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
