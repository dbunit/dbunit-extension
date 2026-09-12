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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Statement;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.RowCountDifference;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real-database integration test of the row count check wired into
 * {@link DefaultPrepAndExpectedTestCase}: a leaked row in a table the test never lists, a
 * reference table wrongly listed for cleanup, and the exclude list silencing a legitimate
 * case of either. Deltas are asserted rather than absolute counts, so the test does not
 * depend on {@code EMPTY_TABLE}/{@code SECOND_TABLE} starting genuinely empty.
 */
@ClearRowCountCheckSystemProperties
class DefaultPrepAndExpectedTestCaseRowCountCheckIT
{
    private static final String EMPTY_TABLE = "EMPTY_TABLE";
    private static final String SECOND_TABLE = "SECOND_TABLE";

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();

    private IDatabaseConnection connection;
    private DefaultPrepAndExpectedTestCase tc;

    @BeforeEach
    void setUp() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        connection = dbEnv.getConnection();
        connection.getConfig().setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);

        final IDatabaseTester databaseTester = new DefaultDatabaseTester(connection);
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        tc = new DefaultPrepAndExpectedTestCase(dataFileLoader, databaseTester);
    }

    @AfterEach
    void cleanUp() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        final IDatabaseConnection cleanupConnection = dbEnv.getConnection();
        try
        {
            deleteAllRowsQuietly(cleanupConnection, EMPTY_TABLE);
            deleteAllRowsQuietly(cleanupConnection, SECOND_TABLE);
        } finally
        {
            closeQuietly(cleanupConnection);
        }
    }

    @Test
    void testPostTest_rowLeakedIntoUnlistedTable_throwsUnexpectedRowCountExceptionNamingIt()
            throws Exception
    {
        tc.preTest();

        // the code under test wrote to a table the developer forgot to list for teardown
        insertRow(EMPTY_TABLE);

        final Throwable thrown = catchThrowable(() -> tc.postTest());

        assertThat(thrown)
                .as("A row left behind in a table absent from prep/expected must fail the"
                        + " test with UnexpectedRowCountException.")
                .isInstanceOf(UnexpectedRowCountException.class);
        assertThat(differenceFor((UnexpectedRowCountException) thrown, EMPTY_TABLE).getDelta())
                .as("A row was left behind, so the delta must be positive; not asserting the"
                        + " exact value, since EMPTY_TABLE is shared with other IT classes"
                        + " and may not start genuinely empty.")
                .isPositive();
    }

    @Test
    void testPostTest_referenceTableWronglyListedForCleanup_throwsUnexpectedRowCountExceptionWithNegativeDelta()
            throws Exception
    {
        // pre-existing reference data, seeded before this test's baseline is captured
        insertRow(SECOND_TABLE);

        // wrongly listing SECOND_TABLE for cleanup - its rows get wiped, by CLEAN_INSERT
        // during setupData() here, or by DELETE_ALL during cleanupData() otherwise
        final Column[] columns = {new Column("COLUMN0", DataType.VARCHAR)};
        tc.setPrepDs(new DefaultDataSet(new DefaultTable(SECOND_TABLE, columns)));

        tc.preTest();

        final Throwable thrown = catchThrowable(() -> tc.postTest());

        assertThat(thrown)
                .as("A reference table wrongly listed for cleanup must fail the test with"
                        + " UnexpectedRowCountException.")
                .isInstanceOf(UnexpectedRowCountException.class);
        assertThat(differenceFor((UnexpectedRowCountException) thrown, SECOND_TABLE).getDelta())
                .as("Pre-existing rows were wiped, so the delta must be negative; not"
                        + " asserting the exact value, since SECOND_TABLE is shared with"
                        + " other IT classes and may carry more than this test's own row.")
                .isNegative();
    }

    @Test
    void testPostTest_leakedRowInExcludedTable_doesNotThrow() throws Exception
    {
        connection.getConfig().setProperty(
                DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                new String[] {EMPTY_TABLE});

        tc.preTest();
        insertRow(EMPTY_TABLE);

        assertThatCode(() -> tc.postTest())
                .as("A table matching an exclude pattern must never be reported, even"
                        + " though its count actually changed.")
                .doesNotThrowAnyException();
    }

    private RowCountDifference differenceFor(final UnexpectedRowCountException exception,
            final String tableName)
    {
        return exception.getDifferences().stream()
                .filter(difference -> difference.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No difference reported for table '" + tableName + "': "
                                + exception.getDifferences()));
    }

    private void insertRow(final String tableName) throws Exception
    {
        try (Statement statement = connection.getConnection().createStatement())
        {
            statement.execute(
                    "INSERT INTO " + tableName + " (COLUMN0) VALUES ('rowCountCheckIT')");
        }
    }

    private static void deleteAllRowsQuietly(final IDatabaseConnection connection,
            final String tableName)
    {
        try (Statement statement = connection.getConnection().createStatement())
        {
            statement.execute("DELETE FROM " + tableName);
        } catch (final Exception e)
        {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    private static void closeQuietly(final IDatabaseConnection connection)
    {
        try
        {
            connection.close();
        } catch (final Exception e)
        {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }
}
