package org.dbunit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test of extends of the PrepAndExpected.
 *
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
class DefaultPrepAndExpectedTestCaseExtIT extends DefaultPrepAndExpectedTestCase
{
    private static final String PREP_DATA_FILE_NAME =
            "/xml/flatXmlDataSetTest.xml";
    private static final String EXP_DATA_FILE_NAME =
            "/xml/flatXmlDataSetTestChanged.xml";

    private static final VerifyTableDefinition TEST_TABLE =
            makeVerifyTableDefinition("TEST_TABLE");
    private static final VerifyTableDefinition SECOND_TABLE =
            makeVerifyTableDefinition("SECOND_TABLE");
    private static final VerifyTableDefinition EMPTY_TABLE =
            makeVerifyTableDefinition("EMPTY_TABLE");
    private static final VerifyTableDefinition PK_TABLE =
            makeVerifyTableDefinition("PK_TABLE");
    private static final VerifyTableDefinition ONLY_PK_TABLE =
            makeVerifyTableDefinition("ONLY_PK_TABLE");
    private static final VerifyTableDefinition EMPTY_MULTITYPE_TABLE =
            makeVerifyTableDefinition("EMPTY_MULTITYPE_TABLE");

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();

    private static VerifyTableDefinition makeVerifyTableDefinition(
            final String tableName)
    {
        return new VerifyTableDefinition(tableName, new String[] {});
    }

    @Override
    @BeforeEach
    protected void setUp() throws Exception
    {
        setDataFileLoader(dataFileLoader);

        // don't call super.setUp() here as prep data is not loaded yet
        // (getDataSet() is null)
        // super.setUp();
    }

    @Test
    void testSuccessRun_withMatchingPrepAndExpectedFiles_doesNotThrowException() throws Exception
    {
        final String[] prepDataFiles = {PREP_DATA_FILE_NAME};
        final String[] expectedDataFiles = {PREP_DATA_FILE_NAME};
        final VerifyTableDefinition[] tables = {TEST_TABLE, SECOND_TABLE,
                EMPTY_TABLE, PK_TABLE, ONLY_PK_TABLE, EMPTY_MULTITYPE_TABLE};

        final IDatabaseTester databaseTester = makeDatabaseTester();
        setDatabaseTester(databaseTester);

        configureTest(tables, prepDataFiles, expectedDataFiles);

        // reopen connection as configureTest() closes its own after
        // obtaining the case-sensitivity feature setting; preTest() and
        // postTest() then share and close this one connection themselves
        // instead of each needing a fresh one (#800)
        final IDatabaseTester databaseTesterNew1 = makeDatabaseTester();
        setDatabaseTester(databaseTesterNew1);
        assertDoesNotThrow(() -> preTest(),
                "Did not expect tc.postTest() to throw, but it did!");

        // skip modifying data and just verify the insert

        postTest();
    }

    @Test
    void testFailRun_withMismatchedExpectedFile_throwsDbComparisonFailure() throws Exception
    {
        final String[] prepDataFiles = {PREP_DATA_FILE_NAME};
        final String[] expectedDataFiles = {EXP_DATA_FILE_NAME};
        final VerifyTableDefinition[] tables = {TEST_TABLE, SECOND_TABLE,
                EMPTY_TABLE, PK_TABLE, ONLY_PK_TABLE, EMPTY_MULTITYPE_TABLE};

        final IDatabaseTester databaseTester = makeDatabaseTester();
        setDatabaseTester(databaseTester);

        configureTest(tables, prepDataFiles, expectedDataFiles);

        // reopen connection as configureTest() closes its own after
        // obtaining the case-sensitivity feature setting; preTest() and
        // postTest() then share and close this one connection themselves
        // instead of each needing a fresh one (#800)
        final IDatabaseTester databaseTesterNew1 = makeDatabaseTester();
        setDatabaseTester(databaseTesterNew1);

        preTest();

        // skip modifying data and just verify the insert

        assertThrows(DbComparisonFailure.class, () -> postTest(),
                "Expected tc.postTest() to throw DbComparisonFailure, but it didn't");

    }

    protected IDatabaseTester makeDatabaseTester() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = dbEnv.getConnection();
        return new DefaultDatabaseTester(connection);
    }
}
