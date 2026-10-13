package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.dbunit.dataset.ColumnFilterTable;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
    private static final String MISSING_DATA_FILE_NAME =
            "/xml/noSuchDataSetFile.xml";

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();
    private IDatabaseTester databaseTester;
    private DefaultPrepAndExpectedTestCase tc;

    @BeforeEach
    void setUp() throws Exception
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
    void testConfigureTest_prepAndExpectedTestDataOverload_configuresSameStateAsArrayOverload()
            throws Exception
    {
        final VerifyTableDefinition[] tables = {};
        final PrepAndExpectedTestData testData = new PrepAndExpectedTestData(
                tables, new String[] {PREP_DATA_FILE_NAME},
                new String[] {EXP_DATA_FILE_NAME});

        tc.configureTest(testData);

        assertThat(tc.getVerifyTableDefs())
                .as("The bundle overload must configure the same verify"
                        + " definitions as the array overload.")
                .isEqualTo(tables);

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
    void testConfigureTest_expectedFileMissing_releasesTheConnectionItAcquired() throws Exception
    {
        final VerifyTableDefinition[] tables = {};
        final String[] prepDataFiles = {PREP_DATA_FILE_NAME};
        final String[] missingExpectedDataFiles = {MISSING_DATA_FILE_NAME};

        assertThatThrownBy(
                () -> tc.configureTest(tables, prepDataFiles, missingExpectedDataFiles))
                        .as("A missing expected data file must fail configureTest().")
                        .isInstanceOf(DatabaseUnitRuntimeException.class);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // the case sensitivity lookup acquired the shared connection, and no cleanupData() follows
        // a failed configureTest() on the annotation path, so nothing else would release it
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testConfigureTest_expectedFileMissingAfterEarlierSuccess_clearsPerTestState()
            throws Exception
    {
        final VerifyTableDefinition[] firstTestTables =
                {new VerifyTableDefinition("TEST_TABLE", new String[] {})};
        tc.configureTest(firstTestTables, new String[] {PREP_DATA_FILE_NAME},
                new String[] {EXP_DATA_FILE_NAME});

        assertThatThrownBy(() -> tc.configureTest(new VerifyTableDefinition[] {},
                new String[] {PREP_DATA_FILE_NAME}, new String[] {MISSING_DATA_FILE_NAME}))
                        .as("A missing expected data file must fail configureTest().")
                        .isInstanceOf(DatabaseUnitRuntimeException.class);

        Assertion.assertEquals(new DefaultDataSet(), tc.getPrepDataset());
        Assertion.assertEquals(new DefaultDataSet(), tc.getExpectedDataset());
        assertThat(tc.getVerifyTableDefs())
                .as("A failed configureTest() must not leave the new prep data paired with the"
                        + " previous test's expected data and verify definitions, because a"
                        + " cleanupData() that follows would clean the wrong tables.")
                .isEmpty();
    }

    @Test
    void testConfigureTest_connectionAlreadyResolvedAndExpectedFileMissing_leavesItOpen()
            throws Exception
    {
        tc.getReusableConnection();

        assertThatThrownBy(() -> tc.configureTest(new VerifyTableDefinition[] {},
                new String[] {PREP_DATA_FILE_NAME}, new String[] {MISSING_DATA_FILE_NAME}))
                        .as("A missing expected data file must fail configureTest().")
                        .isInstanceOf(DatabaseUnitRuntimeException.class);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // the caller resolved the connection before configureTest() ran, for example by injecting
        // it into a @BeforeEach parameter, so this call did not acquire it and must not close it
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testConfigureTest_nullArguments_configuresEmptyState() throws Exception
    {
        tc.configureTest(null, null, null);

        Assertion.assertEquals(new DefaultDataSet(), tc.getPrepDataset());
        Assertion.assertEquals(new DefaultDataSet(), tc.getExpectedDataset());
        assertThat(tc.getVerifyTableDefs())
                .as("Null verify table definitions mean none to verify, not a null that fails"
                        + " later with a NullPointerException.")
                .isEmpty();
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
    void testCleanupData_connectionKeptAndPropertiesSet_putsReplacedValuesBack() throws Exception
    {
        final DefaultPrepAndExpectedTestCase keepingTestCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester, false);
        keepingTestCase.setDatabaseConfigProperties(batchSizeProperties("50"));
        keepingTestCase.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});
        final DatabaseConfig config = databaseTester.getConnection().getConfig();
        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The property must be in force while the test runs.").isEqualTo(50);

        keepingTestCase.cleanupData();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A connection kept open past the test must not carry the test's property"
                        + " values into whatever uses it next.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
    }

    @Test
    void testCleanupData_tearDownFailsAndConnectionKept_stillPutsReplacedValuesBack()
            throws Exception
    {
        final DefaultPrepAndExpectedTestCase keepingTestCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester, false);
        keepingTestCase.setDatabaseConfigProperties(batchSizeProperties("50"));
        keepingTestCase.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});
        databaseTester.setTearDownOperation(new FailingDatabaseOperation());
        final DatabaseConfig config = databaseTester.getConnection().getConfig();

        assertThatThrownBy(keepingTestCase::cleanupData)
                .as("The tear down failure must still surface.")
                .isInstanceOf(DatabaseUnitException.class);

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A failed cleanup must not leave the test's property values on a connection"
                        + " kept open past the test.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
    }

    @Test
    void testConfigureTest_connectionKeptFromAnEarlierTestWhosePropertiesWereRestored_appliesThemAgain()
            throws Exception
    {
        final DefaultPrepAndExpectedTestCase keepingTestCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester, false);
        keepingTestCase.setDatabaseConfigProperties(batchSizeProperties("50"));
        keepingTestCase.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});
        keepingTestCase.cleanupData();

        keepingTestCase.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});

        final DatabaseConfig config = databaseTester.getConnection().getConfig();
        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("configureTest() and the row count baseline read the connection's"
                        + " DatabaseConfig before setupData() retrieves it again, so the next"
                        + " test's properties must already be in force on the kept connection.")
                .isEqualTo(50);
    }

    @Test
    void testSetupDataThenCleanupData_withDatabaseConfigProperties_appliesThemOncePerConnection()
            throws Exception
    {
        CountingDataTypeFactory.resetInstanceCount();
        // executeOperation() only retrieves the connection through the listener when the
        // operation is not NONE
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final Properties properties = new Properties();
        properties.setProperty("datatypeFactory", CountingDataTypeFactory.class.getName());
        tc.setDatabaseConfigProperties(properties);

        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
        tc.setupData();
        tc.cleanupData();

        assertThat(CountingDataTypeFactory.getInstanceCount())
                .as("The properties must not be applied again, instantiating every class-valued"
                        + " property again, each time the lifecycle retrieves the connection it"
                        + " already applied them to.")
                .isEqualTo(1);
    }

    @Test
    void testSetupDataThenCleanupData_testersListenerSetsTheSamePropertyEachTime_propertiesWinBothTimes()
            throws Exception
    {
        final RecordingBatchSizeOperation setUpOperation = new RecordingBatchSizeOperation();
        final RecordingBatchSizeOperation tearDownOperation = new RecordingBatchSizeOperation();
        databaseTester.setSetUpOperation(setUpOperation);
        databaseTester.setTearDownOperation(tearDownOperation);
        databaseTester.setOperationListener(new BatchSizeSettingListener(99));
        tc.setDatabaseConfigProperties(batchSizeProperties("7"));

        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
        tc.setupData();
        tc.cleanupData();

        assertThat(setUpOperation.getBatchSize())
                .as("The values this instance applies win over the tester's listener when set up.")
                .isEqualTo(7);
        assertThat(tearDownOperation.getBatchSize())
                .as("They must win again when tearing down, as the tester's listener sets its own"
                        + " value on every connection it is told about.")
                .isEqualTo(7);
    }

    @Test
    void testSetDatabaseConfigProperties_valuesAlreadyApplied_putsReplacedValuesBack()
            throws Exception
    {
        tc.setDatabaseConfigProperties(batchSizeProperties("50"));
        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
        final DatabaseConfig config = databaseTester.getConnection().getConfig();

        tc.setDatabaseConfigProperties(null);

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("Replacing the properties must put back what the old ones had changed, so"
                        + " restoring an injected instance's own properties after a test also"
                        + " clears the old values off its connection.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
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
    void testSetDatabaseTester_afterConfigureTestWithDifferentTester_runsOnTheNewTestersConnection()
            throws Exception
    {
        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
        final MockDatabaseConnection firstConnection =
                (MockDatabaseConnection) databaseTester.getConnection();
        final MockDatabaseConnection secondConnection =
                (MockDatabaseConnection) makeDatabaseConnection();

        tc.setDatabaseTester(new DefaultDatabaseTester(secondConnection));

        // configureTest() acquired the first connection for this instance, so replacing its
        // tester releases it
        firstConnection.setExpectedCloseCalls(1);
        firstConnection.verify();
        assertThat(tc.getReusableConnection())
                .as("After the tester is replaced, the shared connection must come from the new"
                        + " tester, not stay the one acquired from the old tester.")
                .isSameAs(secondConnection);

        tc.cleanupData();

        secondConnection.setExpectedCloseCalls(1);
        secondConnection.verify();
        firstConnection.setExpectedCloseCalls(1);
        firstConnection.verify();
    }

    @Test
    void testSetDatabaseTester_sameTesterInstance_keepsTheSharedConnection() throws Exception
    {
        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});

        tc.setDatabaseTester(databaseTester);

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(0);
        connection.verify();
        assertThat(tc.getReusableConnection())
                .as("Setting the tester it already has must not discard the shared connection.")
                .isSameAs(connection);
    }

    @Test
    void testSetDatabaseTester_closeConnectionAfterTestFalse_forgetsButDoesNotCloseTheOldConnection()
            throws Exception
    {
        final DefaultPrepAndExpectedTestCase keepingTestCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester, false);
        keepingTestCase.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                new String[] {});
        final MockDatabaseConnection firstConnection =
                (MockDatabaseConnection) databaseTester.getConnection();
        final MockDatabaseConnection secondConnection =
                (MockDatabaseConnection) makeDatabaseConnection();

        keepingTestCase.setDatabaseTester(new DefaultDatabaseTester(secondConnection));

        // closeConnectionAfterTest=false says the first connection belongs to its real owner
        firstConnection.setExpectedCloseCalls(0);
        firstConnection.verify();
        assertThat(keepingTestCase.getReusableConnection())
                .as("The replaced tester's connection must be forgotten even when it is left"
                        + " open for its owner.")
                .isSameAs(secondConnection);
    }

    @Test
    void testSetDatabaseTester_replacedTesterListenerIsNoOp_doesNotCloseTheOldConnection()
            throws Exception
    {
        databaseTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
        final MockDatabaseConnection firstConnection =
                (MockDatabaseConnection) databaseTester.getConnection();
        final MockDatabaseConnection secondConnection =
                (MockDatabaseConnection) makeDatabaseConnection();

        tc.setDatabaseTester(new DefaultDatabaseTester(secondConnection));

        // the replaced tester said its connection is managed elsewhere, and the decision must
        // read that tester's listener rather than the new tester's
        firstConnection.setExpectedCloseCalls(0);
        firstConnection.verify();
        assertThat(tc.getReusableConnection())
                .as("The replaced tester's connection must be forgotten even when its no-op"
                        + " listener leaves it open.")
                .isSameAs(secondConnection);
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
    void testPreTest_prepAndExpectedTestDataOverload_configuresDatasetAndExecutesSetUpOperation()
            throws Exception
    {
        final PrepAndExpectedTestData testData = new PrepAndExpectedTestData(
                new VerifyTableDefinition[] {}, new String[] {}, new String[] {});

        tc.preTest(testData);

        assertThat(tc.getVerifyTableDefs())
                .as("The bundle overload must configure the same verify"
                        + " definitions as the array overload.")
                .isEmpty();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // same connection sharing as the array preTest() overload: the
        // case-sensitivity feature lookup and setupData()'s CLEAN_INSERT share
        // one connection, left open until cleanupData() closes it (#800, #825)
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
    void testRunTest_prepAndExpectedTestDataOverload_executesStepsAndReturnsResult()
            throws Exception
    {
        final PrepAndExpectedTestCaseSteps testSteps = () -> Boolean.TRUE;

        final Boolean actual =
                (Boolean) tc.runTest(PrepAndExpectedTestData.NONE, testSteps);

        assertThat(actual)
                .as("Did not receive expected value from"
                        + " runTest(PrepAndExpectedTestData, steps).")
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
    void testSetupData_databaseTesterHasItsOwnListener_tellsItAboutTheConnectionButNeverFinishes()
            throws Exception
    {
        final IOperationListener testersListener = Mockito.mock(IOperationListener.class);
        databaseTester.setOperationListener(testersListener);

        tc.setupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        Mockito.verify(testersListener).connectionRetrieved(connection);
        Mockito.verify(testersListener, Mockito.never())
                .operationSetUpFinished(Mockito.any(IDatabaseConnection.class));
        // the listener may configure the connection, but this instance owns closing it
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testCleanupData_databaseTesterHasItsOwnListener_tellsItAboutTheConnectionButNeverFinishes()
            throws Exception
    {
        final IOperationListener testersListener = Mockito.mock(IOperationListener.class);
        databaseTester.setOperationListener(testersListener);
        // executeOperation() only calls the listener when the tear down operation is not NONE
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        Mockito.verify(testersListener).connectionRetrieved(connection);
        Mockito.verify(testersListener, Mockito.never())
                .operationTearDownFinished(Mockito.any(IDatabaseConnection.class));
    }

    @Test
    void testSetupData_databaseTesterListenerIsWrappedByASubclassOfConnectionPreserving_tellsOnlyTheWrappedListener()
            throws Exception
    {
        // the annotation runtime installs a ConnectionPreservingOperationListener subclass on the
        // tester, which would apply the @DbUnitProperty values and capture the row count baseline
        // again if it were told about the connection; only the caller's listener beneath it is
        final IOperationListener callersListener = Mockito.mock(IOperationListener.class);
        final IOperationListener wrapperListener = Mockito.mock(IOperationListener.class);
        databaseTester.setOperationListener(
                new ConnectionPreservingOperationListener(callersListener)
                {
                    @Override
                    public void connectionRetrieved(final IDatabaseConnection connection)
                    {
                        wrapperListener.connectionRetrieved(connection);
                        super.connectionRetrieved(connection);
                    }
                });

        tc.setupData();

        Mockito.verify(callersListener).connectionRetrieved(Mockito.any(IDatabaseConnection.class));
        Mockito.verifyNoInteractions(wrapperListener);
    }

    @Test
    void testSetupData_databaseTesterListenerIsTheTestCasesOwnListener_tellsItOnce()
            throws Exception
    {
        final IOperationListener sharedListener = Mockito.mock(IOperationListener.class);
        databaseTester.setOperationListener(sharedListener);
        final DefaultPrepAndExpectedTestCase sharingTestCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester)
                {
                    @Override
                    protected IOperationListener getOperationListener()
                    {
                        return sharedListener;
                    }
                };

        sharingTestCase.setupData();

        Mockito.verify(sharedListener, Mockito.times(1))
                .connectionRetrieved(Mockito.any(IDatabaseConnection.class));
    }

    @Test
    void testSetupData_bothListenersSet_tellsTheTestersBeforeTheTestCasesOwn() throws Exception
    {
        // the values this instance applies, such as databaseConfigProperties, must win over
        // those the caller's listener sets, so its own listener runs second
        final IOperationListener testersListener = Mockito.mock(IOperationListener.class);
        final IOperationListener ownListener = Mockito.mock(IOperationListener.class);
        databaseTester.setOperationListener(testersListener);
        final DefaultPrepAndExpectedTestCase ownListenerTestCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester)
                {
                    @Override
                    protected IOperationListener getOperationListener()
                    {
                        return ownListener;
                    }
                };

        ownListenerTestCase.setupData();

        final InOrder inOrder = Mockito.inOrder(testersListener, ownListener);
        inOrder.verify(testersListener).connectionRetrieved(Mockito.any(IDatabaseConnection.class));
        inOrder.verify(ownListener).connectionRetrieved(Mockito.any(IDatabaseConnection.class));
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
    void testVerifyData_withSortOnFilteredColumnsOnlyTrueAndWildcardExclude_sortsWithoutTheMatchedColumn()
            throws Exception
    {
        final Column[] actualColumns = {new Column("GEN_ID", DataType.INTEGER),
                new Column("COL1", DataType.VARCHAR)};
        final DefaultTable actualTable = new DefaultTable("TEST_TABLE", actualColumns);
        actualTable.addRow(new Object[] {2, "Apple"});
        actualTable.addRow(new Object[] {1, "Banana"});

        final Column[] expectedColumns = {new Column("COL1", DataType.VARCHAR)};
        final DefaultTable expectedTable = new DefaultTable("TEST_TABLE", expectedColumns);
        expectedTable.addRow(new Object[] {"Apple"});
        expectedTable.addRow(new Object[] {"Banana"});

        final String[] excludeColumns = {"gen_*"};

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable, excludeColumns, null, null,
                null, true))
                        .as("The sort key must drop a column the exclude pattern matches, with"
                                + " the same wildcard and case-insensitive matching as the"
                                + " comparison, so same-data rows line up.")
                        .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVerifyData_typedExpectedColumnsInDifferentOrder_passesWhenEqual(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final Column[] actualColumns = {new Column("ID", DataType.INTEGER),
                new Column("NAME", DataType.VARCHAR)};
        final DefaultTable actualTable = new DefaultTable("TEST_TABLE", actualColumns);
        actualTable.addRow(new Object[] {1, "b"});
        actualTable.addRow(new Object[] {2, "a"});

        final Column[] expectedColumns = {new Column("NAME", DataType.VARCHAR),
                new Column("ID", DataType.INTEGER)};
        final DefaultTable expectedTable = new DefaultTable("TEST_TABLE", expectedColumns);
        expectedTable.addRow(new Object[] {"b", 1});
        expectedTable.addRow(new Object[] {"a", 2});

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable, null, null, null, null,
                sortOnFilteredColumnsOnly))
                        .as("Typed expected columns declared in another order than the database's"
                                + " must sort in the database's order, the same as the actual"
                                + " table, so the same rows line up.")
                        .doesNotThrowAnyException();
    }

    @Test
    void testVerifyData_typedIntegerColumnWithTwoAndTen_sortsNumericallyOnBothSides()
            throws Exception
    {
        final Column[] columns = {new Column("ID", DataType.INTEGER)};
        final DefaultTable actualTable = new DefaultTable("TEST_TABLE", columns);
        actualTable.addRow(new Object[] {10});
        actualTable.addRow(new Object[] {2});

        final DefaultTable expectedTable = new DefaultTable("TEST_TABLE", columns);
        expectedTable.addRow(new Object[] {2});
        expectedTable.addRow(new Object[] {10});

        assertThatCode(() -> tc.verifyData(expectedTable, actualTable, null, null, null, null,
                false))
                        .as("Integer values must sort numerically, so 2 sorts before 10 on both"
                                + " sides, not as text.")
                        .doesNotThrowAnyException();
    }

    @Test
    void testVerifyData_mismatchWithWildcardExclude_failureMessageOmitsTheExcludedColumn()
            throws Exception
    {
        final ITable expectedTable = makeAdditionalInfoTable("expectedName", "expectedCreated");
        final ITable actualTable = makeAdditionalInfoTable("actualName", "actualCreated");

        final Throwable thrown = catchThrowable(() -> tc.verifyData(expectedTable, actualTable,
                new String[] {"CREATED_*"}, null, null, null, false));

        assertThat(thrown).as("The NAME mismatch must fail the verification.")
                .isInstanceOf(DbComparisonFailure.class);
        assertThat(thrown.getMessage())
                .as("A column the exclude pattern matches is not compared, so its values must not"
                        + " be listed as additional row info.")
                .doesNotContain("CREATED_AT");
    }

    @Test
    void testVerifyData_mismatchWithIncludeAndExcludeColumns_failureMessageListsTheOtherColumnsWithoutNoise()
            throws Exception
    {
        final ITable expectedTable = makeAdditionalInfoTable("expectedName", "expectedCreated");
        final ITable actualTable = makeAdditionalInfoTable("actualName", "actualCreated");

        final Throwable thrown = catchThrowable(() -> tc.verifyData(expectedTable, actualTable,
                new String[] {"CODE"}, new String[] {"ID", "NAME", "CODE"}, null, null, false));

        assertThat(thrown).as("The NAME mismatch must fail the verification.")
                .isInstanceOf(DbComparisonFailure.class);
        assertThat(thrown.getMessage())
                .as("CREATED_AT, which only the include filter drops, must be found and listed with"
                        + " its values even though an exclude filter is also set, and the excluded"
                        + " CODE must not be listed.")
                .contains("('CREATED_AT': expected=<expectedCreated>, actual=<actualCreated>)")
                .doesNotContain("'CODE'").doesNotContain("!!!!!");
    }

    @Test
    void testVerifyData_mismatchWithIncludeColumns_failureMessageAlsoListsTheNotIncludedColumns()
            throws Exception
    {
        final ITable expectedTable = makeAdditionalInfoTable("expectedName", "expectedCreated");
        final ITable actualTable = makeAdditionalInfoTable("actualName", "actualCreated");

        final Throwable thrown = catchThrowable(() -> tc.verifyData(expectedTable, actualTable,
                null, new String[] {"ID", "NAME"}, null, null, false));

        assertThat(thrown).as("The NAME mismatch must fail the verification.")
                .isInstanceOf(DbComparisonFailure.class);
        assertThat(thrown.getMessage())
                .as("A column the include filter drops is not compared, but its values still help"
                        + " to identify the mismatching row, so they are listed as additional row"
                        + " info.")
                .contains("('CREATED_AT': expected=<expectedCreated>, actual=<actualCreated>)")
                .contains("('CODE': expected=<c>, actual=<c>)").doesNotContain("!!!!!");
    }

    @Test
    void testVerifyData_actualTableHasOnlyTheComparedColumns_failureMessageListsOnlyColumnsBothTablesHave()
            throws Exception
    {
        final ITable expectedTable = makeAdditionalInfoTable("expectedName", "expectedCreated");
        final Column[] comparedColumns = {new Column("ID", DataType.VARCHAR),
                new Column("NAME", DataType.VARCHAR)};
        final DefaultTable actualTable = new DefaultTable("TEST_TABLE", comparedColumns);
        actualTable.addRow(new Object[] {"1", "actualName"});

        final Throwable thrown = catchThrowable(() -> tc.verifyData(expectedTable, actualTable,
                null, new String[] {"ID", "NAME"}, null, null, true));

        assertThat(thrown).as("The NAME mismatch must fail the verification.")
                .isInstanceOf(DbComparisonFailure.class);
        assertThat(thrown.getMessage())
                .as("The actual table was loaded without the columns the include filter drops, so"
                        + " they cannot be listed, and trying to would add a not found message.")
                .contains("('ID': expected=<1>, actual=<1>)")
                .contains("('NAME': expected=<expectedName>, actual=<actualName>)")
                .doesNotContain("CREATED_AT").doesNotContain("'CODE'").doesNotContain("!!!!!");
    }

    @Test
    void testMakeAdditionalColumnInfo_lowerCaseExclude_removesUpperCaseColumn() throws Exception
    {
        final Column createdAt = new Column("CREATED_AT", DataType.VARCHAR);
        final Column name = new Column("NAME", DataType.VARCHAR);

        final Column[] additionalColumnInfo =
                tc.makeAdditionalColumnInfo(new String[] {"created_at"},
                        new Column[] {name, createdAt});

        assertThat(additionalColumnInfo)
                .as("An exclude pattern matches without regard to case, as the column filters"
                        + " do.")
                .containsExactly(name);
    }

    @Test
    void testApplyColumnFilters_withExcludeAndInclude_filtersWithOneWrapperOverTheUnfilteredTable()
            throws DataSetException
    {
        final Column[] columns = {new Column("COL1", DataType.VARCHAR),
                new Column("COL2", DataType.VARCHAR), new Column("COL3", DataType.VARCHAR)};
        final ITable table = new DefaultTable("test_table", columns);

        final ITable filtered = tc.applyColumnFilters(table, new String[] {"COL1"},
                new String[] {"COL1", "COL2"});

        assertThat(filtered).as("Filtering must wrap the table.")
                .isInstanceOf(ColumnFilterTable.class);
        assertThat(((ColumnFilterTable) filtered).getOriginalMetaData().getColumns())
                .as("The wrapper must sit directly on the unfiltered table, as a failure message"
                        + " finds a column a wrapper drops only one wrapper deep.")
                .isEqualTo(columns);
        assertThat(filtered.getTableMetaData().getColumns())
                .as("Both filters must apply: COL1 is excluded and COL3 is not included.")
                .containsExactly(new Column("COL2", DataType.VARCHAR));
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
    void testCleanupData_testCaseListenerIsNoOp_leavesTheSharedConnectionOpen() throws Exception
    {
        // A NO_OP operation listener is the established signal that the connection is managed
        // elsewhere and must not be closed - honored now on this path too, not only by the
        // annotation runtime.
        final DefaultPrepAndExpectedTestCase noOpListenerTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester)
                {
                    @Override
                    protected IOperationListener getOperationListener()
                    {
                        return IOperationListener.NO_OP_OPERATION_LISTENER;
                    }
                };

        noOpListenerTc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testCleanupData_databaseTesterListenerIsNoOp_leavesTheSharedConnectionOpen()
            throws Exception
    {
        // The tester, not the test case, is what a caller configures with the NO_OP listener to
        // say its connection is managed elsewhere; the test case must honor that signal too.
        databaseTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testCleanupData_databaseTesterListenerWrapsNoOp_leavesTheSharedConnectionOpen()
            throws Exception
    {
        databaseTester.setOperationListener(new ConnectionPreservingOperationListener(
                IOperationListener.NO_OP_OPERATION_LISTENER));

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testCleanupData_databaseTesterListenerIsDefault_closesTheSharedConnection()
            throws Exception
    {
        databaseTester.setOperationListener(new DefaultOperationListener());

        tc.cleanupData();

        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
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
    void testTearDown_withRecordingTearDownOperation_runsTearDownOperationOnce() throws Exception
    {
        final RecordingDatabaseOperation tearDownOperation = new RecordingDatabaseOperation();
        databaseTester.setTearDownOperation(tearDownOperation);
        tc.setPrepDs(new DefaultDataSet(new DefaultTable("PREP_ONLY_TABLE")));
        tc.setExpectedDs(new DefaultDataSet(new DefaultTable("EXPECTED_ONLY_TABLE")));

        tc.tearDown();

        assertThat(tearDownOperation.getExecutedTableNames())
                .as("The legacy tearDown() must clean up the prep and expected tables once,"
                        + " through cleanupData(), not again through the inherited"
                        + " DatabaseTestCase.tearDown().")
                .containsExactly(Arrays.asList("PREP_ONLY_TABLE", "EXPECTED_ONLY_TABLE"));
    }

    @Test
    void testTearDown_databaseTesterListenerIsNoOpAndDataSetSet_leavesBothUnchanged()
            throws Exception
    {
        final IDataSet testersDataSet = new DefaultDataSet();
        databaseTester.setDataSet(testersDataSet);
        databaseTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});

        tc.tearDown();

        assertThat(databaseTester.getOperationListener())
                .as("The caller's no-op listener says the connection is managed elsewhere, so"
                        + " tearDown() must not replace it with the default closing listener.")
                .isSameAs(IOperationListener.NO_OP_OPERATION_LISTENER);
        assertThat(((AbstractDatabaseTester) databaseTester).getDataSet())
                .as("tearDown() must not overwrite the dataset of the tester the caller handed in.")
                .isSameAs(testersDataSet);
        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(0);
        connection.verify();
    }

    @Test
    void testTearDownWithThrowable_testFailureInFlight_cleansPrepAndExpectedTablesOnce()
            throws Exception
    {
        final RecordingDatabaseOperation tearDownOperation = new RecordingDatabaseOperation();
        databaseTester.setTearDownOperation(tearDownOperation);
        tc.setPrepDs(new DefaultDataSet(new DefaultTable("PREP_ONLY_TABLE")));
        tc.setExpectedDs(new DefaultDataSet(new DefaultTable("EXPECTED_ONLY_TABLE")));
        final AssertionError testFailure = new AssertionError("The test failed.");

        final Throwable thrown = catchThrowable(() -> tc.tearDown(testFailure));

        assertThat(thrown).as("The test failure in flight must be the one thrown.")
                .isSameAs(testFailure);
        assertThat(tearDownOperation.getExecutedTableNames())
                .as("tearDown(Throwable) must clean the prep and expected tables once, through"
                        + " cleanupData(), not only the prep tables through the inherited"
                        + " DatabaseTestCase.tearDown(Throwable).")
                .containsExactly(Arrays.asList("PREP_ONLY_TABLE", "EXPECTED_ONLY_TABLE"));
    }

    @Test
    void testTearDownWithThrowable_cleanupFails_throwsTestFailureWithCleanupSuppressed()
            throws Exception
    {
        databaseTester.setTearDownOperation(new FailingDatabaseOperation());
        final AssertionError testFailure = new AssertionError("The test failed.");

        final Throwable thrown = catchThrowable(() -> tc.tearDown(testFailure));

        assertThat(thrown).as("A clean up failure must not replace the test failure in flight.")
                .isSameAs(testFailure);
        assertThat(thrown.getSuppressed())
                .as("The clean up failure must ride along as suppressed.")
                .singleElement().isInstanceOf(DatabaseUnitException.class);
    }

    @Test
    void testTearDownWithThrowable_cleanupFailsWithTheTestFailureItself_throwsItUnchanged()
            throws Exception
    {
        final DatabaseUnitException failure =
                new DatabaseUnitException("The test and its clean up failed the same way.");
        databaseTester.setTearDownOperation(new FailingDatabaseOperation(failure));

        final Throwable thrown = catchThrowable(() -> tc.tearDown(failure));

        assertThat(thrown)
                .as("A throwable cannot suppress itself, so it must be thrown unchanged rather"
                        + " than as the IllegalArgumentException that attempt raises.")
                .isSameAs(failure);
        assertThat(thrown.getSuppressed()).as("Nothing may be attached to itself.").isEmpty();
    }

    @Test
    void testSetUp_withPrepDataSet_runsSetUpOperationOnTheSharedConnection() throws Exception
    {
        final RecordingDatabaseOperation setUpOperation = new RecordingDatabaseOperation();
        databaseTester.setSetUpOperation(setUpOperation);
        tc.setPrepDs(new DefaultDataSet(new DefaultTable("PREP_ONLY_TABLE")));

        tc.setUp();
        tc.cleanupData();

        assertThat(setUpOperation.getExecutedTableNames())
                .as("setUp() must run the set up operation once over the prep tables.")
                .containsExactly(Arrays.asList("PREP_ONLY_TABLE"));
        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        // setUp() leaves the shared connection open for the rest of the lifecycle, so the one
        // close in the whole sequence is cleanupData()'s
        connection.setExpectedCloseCalls(1);
        connection.verify();
    }

    @Test
    void testSetUp_databaseTesterListenerIsNoOpAndDataSetSet_leavesBothUnchanged()
            throws Exception
    {
        final IDataSet testersDataSet = new DefaultDataSet();
        databaseTester.setDataSet(testersDataSet);
        databaseTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

        tc.setUp();

        assertThat(databaseTester.getOperationListener())
                .as("setUp() must not replace the caller's no-op listener with the default"
                        + " closing listener.")
                .isSameAs(IOperationListener.NO_OP_OPERATION_LISTENER);
        assertThat(((AbstractDatabaseTester) databaseTester).getDataSet())
                .as("setUp() must not overwrite the dataset of the tester the caller handed in.")
                .isSameAs(testersDataSet);
        final MockDatabaseConnection connection =
                (MockDatabaseConnection) databaseTester.getConnection();
        connection.setExpectedCloseCalls(0);
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
    void testGetReusableConnection_connectionAutoCommitDisabled_logsExactlyOneWarning()
            throws Exception
    {
        Mockito.when(mockConnection.getAutoCommit()).thenReturn(false);

        final Logger testCaseLogger = (Logger) LoggerFactory
                .getLogger(org.dbunit.database.connection.AutoCommitOffWarning.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        testCaseLogger.addAppender(appender);
        try
        {
            tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
            tc.preTest();
            tc.postTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("autocommit disabled"))
                    .as("A non-autocommit connection must be warned about exactly once for the"
                            + " whole configureTest/preTest/postTest lifecycle, not once per"
                            + " getReusableConnection() call.")
                    .hasSize(1);
        } finally
        {
            testCaseLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testGetReusableConnection_connectionInAutoCommitMode_logsNoWarning() throws Exception
    {
        final Logger testCaseLogger = (Logger) LoggerFactory
                .getLogger(org.dbunit.database.connection.AutoCommitOffWarning.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        testCaseLogger.addAppender(appender);
        try
        {
            tc.configureTest(new VerifyTableDefinition[] {}, new String[] {}, new String[] {});
            tc.preTest();
            tc.postTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("autocommit disabled"))
                    .as("An autocommit connection is the supported case and must not warn.")
                    .isEmpty();
        } finally
        {
            testCaseLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testGetReusableConnection_testDoubleWithNullJdbcConnection_doesNotThrow()
            throws Exception
    {
        final IDatabaseConnection connectionWithNullJdbc =
                Mockito.mock(IDatabaseConnection.class);
        Mockito.when(connectionWithNullJdbc.getConfig()).thenReturn(new DatabaseConfig());
        // getConnection() left unstubbed - a test double returning null
        final DefaultPrepAndExpectedTestCase tcNullJdbc = new DefaultPrepAndExpectedTestCase(
                dataFileLoader, new DefaultDatabaseTester(connectionWithNullJdbc));

        assertThatCode(() -> tcNullJdbc.configureTest(new VerifyTableDefinition[] {},
                new String[] {}, new String[] {}))
                .as("A test double whose IDatabaseConnection.getConnection() returns null"
                        + " must not NullPointerException the autocommit / liveness checks.")
                .doesNotThrowAnyException();
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
    void testPostTest_verifyDataFailed_skipsTheRowCountCheckSoTheVerifyFailureIsThrown()
            throws Exception
    {
        final RowCountCheck mockRowCountCheck = Mockito.mock(RowCountCheck.class);
        final RowCountSnapshot baseline =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        Mockito.when(mockRowCountCheck.capture(Mockito.any())).thenReturn(baseline);
        final AssertionError verifyFailure = new AssertionError("verify boom");
        final DefaultPrepAndExpectedTestCase failingVerifyTc =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester)
                {
                    @Override
                    public void verifyData() throws Exception
                    {
                        throw verifyFailure;
                    }
                };
        failingVerifyTc.setRowCountCheck(mockRowCountCheck);
        failingVerifyTc.preTest();

        final Throwable thrown = catchThrowable(() -> failingVerifyTc.postTest());

        assertThat(thrown)
                .as("The comparison failure names what differs and is the finding to fix first, so"
                        + " a row count difference must not replace it.")
                .isSameAs(verifyFailure);
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

    private static DefaultTable makeAdditionalInfoTable(final String name, final String createdAt)
            throws DataSetException
    {
        final Column[] columns = {new Column("ID", DataType.VARCHAR),
                new Column("NAME", DataType.VARCHAR), new Column("CREATED_AT", DataType.VARCHAR),
                new Column("CODE", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow(new Object[] {"1", name, createdAt, "c"});
        return table;
    }

    private static Properties batchSizeProperties(final String batchSize)
    {
        final Properties properties = new Properties();
        properties.setProperty("batchSize", batchSize);
        return properties;
    }

    /** Data type factory that counts how often a property value instantiates it. */
    public static class CountingDataTypeFactory extends DefaultDataTypeFactory
    {
        private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

        public CountingDataTypeFactory()
        {
            INSTANCE_COUNT.incrementAndGet();
        }

        static void resetInstanceCount()
        {
            INSTANCE_COUNT.set(0);
        }

        static int getInstanceCount()
        {
            return INSTANCE_COUNT.get();
        }
    }

    private static final class FailingDatabaseOperation extends DatabaseOperation
    {
        private final DatabaseUnitException failure;

        private FailingDatabaseOperation()
        {
            this(new DatabaseUnitException("Tear down failed on purpose."));
        }

        private FailingDatabaseOperation(final DatabaseUnitException failure)
        {
            this.failure = failure;
        }

        @Override
        public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
                throws DatabaseUnitException
        {
            throw failure;
        }
    }

    private static final class RecordingBatchSizeOperation extends DatabaseOperation
    {
        private Object batchSize;

        @Override
        public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
                throws DatabaseUnitException
        {
            batchSize = connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE);
        }

        private Object getBatchSize()
        {
            return batchSize;
        }
    }

    private static final class BatchSizeSettingListener implements IOperationListener
    {
        private final int batchSize;

        private BatchSizeSettingListener(final int batchSize)
        {
            this.batchSize = batchSize;
        }

        @Override
        public void connectionRetrieved(final IDatabaseConnection connection)
        {
            connection.getConfig().setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, batchSize);
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection)
        {
            // nothing to do
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection)
        {
            // nothing to do
        }
    }

    private static final class RecordingDatabaseOperation extends DatabaseOperation
    {
        private final List<List<String>> executedTableNames = new ArrayList<>();

        @Override
        public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
                throws DatabaseUnitException
        {
            executedTableNames.add(Arrays.asList(dataSet.getTableNames()));
        }

        private List<List<String>> getExecutedTableNames()
        {
            return executedTableNames;
        }
    }

    private IDatabaseTester makeDatabaseTester() throws Exception
    {
        final IDatabaseConnection databaseConnection = makeDatabaseConnection();
        return new DefaultDatabaseTester(databaseConnection);
    }

    protected IDatabaseConnection makeDatabaseConnection() throws Exception
    {
        final MockStatementFactory mockStatementFactory =
                new MockStatementFactory();
        final IBatchStatement mockBatchStatement = new MockBatchStatement();
        mockStatementFactory.setupStatement(mockBatchStatement);

        final MockDatabaseConnection mockDbConnection =
                new MockDatabaseConnection();
        mockDbConnection.setupConnection(mockConnection);
        mockDbConnection.setupStatementFactory(mockStatementFactory);
        // the supported case: an autocommit connection. Individual tests
        // re-stub this to false to exercise the non-autocommit warning.
        Mockito.lenient().when(mockConnection.getAutoCommit()).thenReturn(true);

        final DatabaseConfig config = mockDbConnection.getConfig();
        config.setFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES,
                true);

        return mockDbConnection;
    }
}
