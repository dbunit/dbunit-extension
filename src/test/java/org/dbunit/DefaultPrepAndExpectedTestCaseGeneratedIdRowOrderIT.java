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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;

/**
 * Proves a {@link DefaultPrepAndExpectedTestCase} false-failure for tables
 * whose generated primary key is the first column, and that
 * {@link VerifyTableDefinition#setSortOnFilteredColumnsOnly(boolean)} fixes
 * it.
 * <p>
 * By default, {@link DefaultPrepAndExpectedTestCase#verifyData} sorts the
 * actual table (loaded from the database) using all of its native columns,
 * identity column first, but sorts the expected table (loaded from the
 * expected dataset file) using only the columns present in that file - which
 * excludes the identity column, since its value cannot be known ahead of
 * time. Excluding the identity column from comparison via
 * {@code excludeColumns} does not help: that filter is applied after both
 * tables are already sorted, so it never influences the sort key.
 * <p>
 * When production code inserts the rows in an order uncorrelated with their
 * data - for example Hibernate reordering a batch insert - the database
 * assigns identity values in that same uncorrelated order. The actual table
 * then sorts by (arbitrary) insertion order while the expected table sorts by
 * data content, so same-data rows compare against the wrong counterpart and
 * the assertion fails despite both sides holding identical data.
 * <p>
 * Setting {@code sortOnFilteredColumnsOnly} true makes both tables sort by
 * only their filtered (post exclude/include) columns instead, so the
 * identity column never participates in the sort and same-data rows line up
 * regardless of insertion order.
 * <p>
 * Tracked as GitHub issue #672 "PrepAndExpectedTestCase should only sort on
 * filtered columns" (bug report), with companion feature request #676 "Allow
 * PrepAndExpectedTestCase to sort only on filtered columns instead of all
 * columns" - this class's fix.
 *
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @since 3.4.1
 */
class DefaultPrepAndExpectedTestCaseGeneratedIdRowOrderIT
{
    private static final String IDENTITY_TABLE_NAME = "IDENTITY_TABLE";
    private static final String IDENTITY_TABLE_ID_COLUMN =
            "IDENTITY_TABLE_ID";

    private static final String PREP_DATA_FILE_NAME =
            "/xml/generatedIdRowOrderPrep.xml";
    private static final String EXPECTED_MATCH_DATA_FILE_NAME =
            "/xml/generatedIdRowOrderExpectedMatch.xml";
    private static final String EXPECTED_MISMATCH_DATA_FILE_NAME =
            "/xml/generatedIdRowOrderExpectedMismatch.xml";

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();

    @Test
    void testVerifyData_defaultSortsAllColumns_rowsInsertedInDifferentOrderButSameData_throwsDbComparisonFailure()
            throws Exception
    {
        final DefaultPrepAndExpectedTestCase tc = makeTestCase();
        tc.configureTest(makeVerifyTableDefinitions(false),
                new String[] {PREP_DATA_FILE_NAME},
                new String[] {EXPECTED_MATCH_DATA_FILE_NAME});
        tc.preTest();

        // Documents the known defect described in the class Javadoc: both
        // sides hold the same two rows, only their insertion/generated-ID
        // order differs. The default (sortOnFilteredColumnsOnly=false)
        // preserves this historical, backward-compatible behavior.
        assertThatThrownBy(() -> tc.postTest())
                .as("Expected the default sort-all-columns behavior to still"
                        + " reproduce the known generated-ID row order defect"
                        + " as a DbComparisonFailure; if this fails, the"
                        + " default may have changed - see this class's"
                        + " Javadoc.")
                .isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testVerifyData_sortOnFilteredColumnsOnly_rowsInsertedInDifferentOrderButSameData_doesNotThrow()
            throws Exception
    {
        final DefaultPrepAndExpectedTestCase tc = makeTestCase();
        tc.configureTest(makeVerifyTableDefinitions(true),
                new String[] {PREP_DATA_FILE_NAME},
                new String[] {EXPECTED_MATCH_DATA_FILE_NAME});
        tc.preTest();

        // Opting in to sortOnFilteredColumnsOnly removes the identity column
        // from the sort key on both sides, so same-data rows line up
        // regardless of insertion order and the comparison passes.
        assertThatCode(() -> tc.postTest())
                .as("Expected sortOnFilteredColumnsOnly=true to fix the"
                        + " generated-ID row order defect so tc.postTest()"
                        + " does not throw, but it did.")
                .doesNotThrowAnyException();
    }

    @Test
    void testVerifyData_sortOnFilteredColumnsOnly_rowsWithGenuineDataMismatch_throwsDbComparisonFailure()
            throws Exception
    {
        final DefaultPrepAndExpectedTestCase tc = makeTestCase();
        tc.configureTest(makeVerifyTableDefinitions(true),
                new String[] {PREP_DATA_FILE_NAME},
                new String[] {EXPECTED_MISMATCH_DATA_FILE_NAME});
        tc.preTest();

        // Control case: sortOnFilteredColumnsOnly=true must not mask a
        // genuine data mismatch (not just a row order difference).
        assertThatThrownBy(() -> tc.postTest())
                .as("Expected tc.postTest() to throw DbComparisonFailure for"
                        + " genuinely mismatched data even with"
                        + " sortOnFilteredColumnsOnly=true, but it didn't.")
                .isInstanceOf(DbComparisonFailure.class);
    }

    private VerifyTableDefinition[] makeVerifyTableDefinitions(
            final boolean sortOnFilteredColumnsOnly)
    {
        final VerifyTableDefinition identityTable = new VerifyTableDefinition(
                IDENTITY_TABLE_NAME, new String[] {IDENTITY_TABLE_ID_COLUMN});
        identityTable
                .setSortOnFilteredColumnsOnly(sortOnFilteredColumnsOnly);
        return new VerifyTableDefinition[] {identityTable};
    }

    private DefaultPrepAndExpectedTestCase makeTestCase() throws Exception
    {
        return new DefaultPrepAndExpectedTestCase(dataFileLoader,
                makeDatabaseTester());
    }

    private IDatabaseTester makeDatabaseTester() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = dbEnv.getConnection();
        final IDatabaseTester databaseTester =
                new DefaultDatabaseTester(connection);
        databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        return databaseTester;
    }
}
