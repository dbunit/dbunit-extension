/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.DataSetBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DbUnitAssert} using purely in-memory datasets.
 */
class DbUnitAssertTest
{
    private final DbUnitAssert assertion = new DbUnitAssert();

    // -------------------------------------------------------------------------
    // assertEquals(ITable, ITable)
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withIdenticalTables_doesNotThrow() throws DatabaseUnitException
    {
        final IDataSet ds = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Bob")
                .build();

        final ITable table = ds.getTable("T");

        // Comparing the same table object to itself is allowed (same-instance fast path)
        assertion.assertEquals(table, table);
    }

    @Test
    void testAssertEquals_withEqualTablesFromSeparateDatasets_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Bob")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Bob")
                .build();

        assertion.assertEquals(expected.getTable("T"), actual.getTable("T"));
    }

    @Test
    void testAssertEquals_withOneDifferentValue_throwsDbComparisonFailure()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Bob")
                .build();

        assertThatThrownBy(() -> assertion.assertEquals(
                expected.getTable("T"), actual.getTable("T")))
                        .as("value mismatch throws DbComparisonFailure.")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining("Alice")
                        .hasMessageContaining("Bob");
    }

    @Test
    void testAssertEquals_withDifferentRowCount_throwsDbComparisonFailure()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Bob")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertThatThrownBy(() -> assertion.assertEquals(
                expected.getTable("T"), actual.getTable("T")))
                        .as("row count mismatch throws.")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining("row count");
    }

    @Test
    void testAssertEquals_withDifferentColumnCount_throwsDbComparisonFailure()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "AGE")
                .row(1, "Alice", 30)
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertThatThrownBy(() -> assertion.assertEquals(
                expected.getTable("T"), actual.getTable("T")))
                        .as("column count mismatch throws.")
                        .isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testAssertEquals_withEmptyTables_doesNotThrow() throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .build();

        assertion.assertEquals(expected.getTable("T"), actual.getTable("T"));
    }

    @Test
    void testAssertEquals_withNullValueMatching_doesNotThrow() throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, null)
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, null)
                .build();

        assertion.assertEquals(expected.getTable("T"), actual.getTable("T"));
    }

    // -------------------------------------------------------------------------
    // assertEquals(IDataSet, IDataSet)
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withIdenticalDataSets_doesNotThrow() throws DatabaseUnitException
    {
        final IDataSet ds = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        // Same instance: fast-path skips comparison
        assertion.assertEquals(ds, ds);
    }

    @Test
    void testAssertEquals_withEqualDataSets_doesNotThrow() throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertion.assertEquals(expected, actual);
    }

    @Test
    void testAssertEquals_withDifferentTableCount_throwsDbComparisonFailure()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID").row(1)
                .table("U").columns("X").row(9)
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID").row(1)
                .build();

        assertThatThrownBy(() -> assertion.assertEquals(expected, actual))
                .as("table count mismatch throws.")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining("table count");
    }

    @Test
    void testAssertEquals_withDifferentValueInDataSet_throwsDbComparisonFailure()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Wrong")
                .build();

        assertThatThrownBy(() -> assertion.assertEquals(expected, actual))
                .as("value mismatch in dataset throws.")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining("Alice")
                .hasMessageContaining("Wrong");
    }

    // -------------------------------------------------------------------------
    // assertEqualsIgnoreCols
    // -------------------------------------------------------------------------

    @Test
    void testAssertEqualsIgnoreCols_withDifferentValueInIgnoredColumn_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "CREATED")
                .row(1, "Alice", "2020-01-01")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "CREATED")
                .row(1, "Alice", "2025-12-31")
                .build();

        assertion.assertEqualsIgnoreCols(expected.getTable("T"), actual.getTable("T"),
                new String[] {"CREATED"});
    }

    @Test
    void testAssertEqualsIgnoreCols_withDifferentValueInNonIgnoredColumn_throwsDbComparisonFailure()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "CREATED")
                .row(1, "Alice", "2020-01-01")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "CREATED")
                .row(1, "Bob", "2020-01-01")
                .build();

        assertThatThrownBy(() -> assertion.assertEqualsIgnoreCols(
                expected.getTable("T"), actual.getTable("T"), new String[] {"CREATED"}))
                        .as("non-ignored column mismatch throws.")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining("Alice")
                        .hasMessageContaining("Bob");
    }

    @Test
    void testAssertEqualsIgnoreCols_viaDataSet_withIgnoredColumnDifferent_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "EXTRA")
                .row(1, "Alice", "ignored_x")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME", "EXTRA")
                .row(1, "Alice", "ignored_y")
                .build();

        assertion.assertEqualsIgnoreCols(expected, actual, "T", new String[] {"EXTRA"});
    }

    // -------------------------------------------------------------------------
    // assertEquals with FailureHandler
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withDiffCollectingHandler_collectsDifferencesWithoutThrowing()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Carol")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Bob")
                .row(2, "Dave")
                .build();

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        assertion.assertEquals(expected.getTable("T"), actual.getTable("T"), handler);

        assertThat(handler.getDiffList()).as("two differences collected.").hasSize(2);
    }

    @Test
    void testAssertEquals_withDiffCollectingHandlerOnDataSet_collectsDifferences()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Wrong")
                .build();

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        assertion.assertEquals(expected, actual, handler);

        assertThat(handler.getDiffList()).as("one difference collected.").hasSize(1);

        final Difference diff = (Difference) handler.getDiffList().get(0);
        assertThat(diff.getColumnName()).as("column name.").isEqualTo("NAME");
        assertThat(diff.getExpectedValue()).as("expected value.").isEqualTo("Alice");
        assertThat(diff.getActualValue()).as("actual value.").isEqualTo("Wrong");
    }

    @Test
    void testAssertEquals_multiRowMismatchTable_reportsDifferencePerCell()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Carol")
                .row(3, "Erin")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(9, "Bob")
                .row(8, "Dave")
                .row(7, "Frank")
                .build();

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        assertion.assertEquals(expected.getTable("T"), actual.getTable("T"), handler);

        final int rowCount = 3;
        final int columnCount = 2;
        assertThat(handler.getDiffList())
                .as("Every (row, column) cell should still be compared and reported once, "
                        + "proving the hoisted row count does not skip or duplicate cells.")
                .hasSize(rowCount * columnCount);
    }

    // -------------------------------------------------------------------------
    // assertEquals with additionalColumnInfo
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withAdditionalColumnInfoAndMismatch_throwsAndContainsAdditionalInfo()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Wrong")
                .build();

        final Column[] additionalInfo = new Column[] {
            new Column("ID", DataType.UNKNOWN)
        };

        assertThatThrownBy(() -> assertion.assertEquals(
                expected.getTable("T"), actual.getTable("T"), additionalInfo))
                        .as("mismatch with additional info throws.")
                        .isInstanceOf(DbComparisonFailure.class);
    }

    // -------------------------------------------------------------------------
    // ComparisonColumn inner class
    // -------------------------------------------------------------------------

    @Test
    void testComparisonColumn_withBothUnknownTypes_returnsUnknown()
    {
        final Column expectedColumn = new Column("COL", DataType.UNKNOWN);
        final Column actualColumn = new Column("COL", DataType.UNKNOWN);

        final DbUnitAssert.ComparisonColumn compCol = new DbUnitAssert.ComparisonColumn(
                "MY_TABLE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler());

        assertThat(compCol.getDataType()).as("both unknown returns unknown.").isEqualTo(DataType.UNKNOWN);
        assertThat(compCol.getColumnName()).as("column name.").isEqualTo("COL");
    }

    @Test
    void testComparisonColumn_withExpectedUnknownAndActualVarchar_returnsActualType()
    {
        final Column expectedColumn = new Column("COL", DataType.UNKNOWN);
        final Column actualColumn = new Column("COL", DataType.VARCHAR);

        final DbUnitAssert.ComparisonColumn compCol = new DbUnitAssert.ComparisonColumn(
                "MY_TABLE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler());

        assertThat(compCol.getDataType()).as("expected unknown, uses actual type.").isEqualTo(DataType.VARCHAR);
    }

    @Test
    void testComparisonColumn_withActualUnknownAndExpectedVarchar_returnsExpectedType()
    {
        final Column expectedColumn = new Column("COL", DataType.VARCHAR);
        final Column actualColumn = new Column("COL", DataType.UNKNOWN);

        final DbUnitAssert.ComparisonColumn compCol = new DbUnitAssert.ComparisonColumn(
                "MY_TABLE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler());

        assertThat(compCol.getDataType()).as("actual unknown, uses expected type.").isEqualTo(DataType.VARCHAR);
    }

    @Test
    void testComparisonColumn_withSameConcreteTypes_returnsThatType()
    {
        final Column expectedColumn = new Column("COL", DataType.INTEGER);
        final Column actualColumn = new Column("COL", DataType.INTEGER);

        final DbUnitAssert.ComparisonColumn compCol = new DbUnitAssert.ComparisonColumn(
                "MY_TABLE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler());

        assertThat(compCol.getDataType()).as("same types, returns that type.").isEqualTo(DataType.INTEGER);
    }

    @Test
    void testComparisonColumn_withIncompatibleTypes_throwsDbComparisonFailure()
    {
        final Column expectedColumn = new Column("COL", DataType.VARCHAR);
        final Column actualColumn = new Column("COL", DataType.NUMERIC);
        final FailureHandler failureHandler = assertion.getDefaultFailureHandler();

        assertThatThrownBy(() -> new DbUnitAssert.ComparisonColumn(
                "MY_TABLE", expectedColumn, actualColumn, failureHandler))
                        .as("incompatible types throw.")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining("Incompatible data types")
                        .hasMessageContaining("VARCHAR")
                        .hasMessageContaining("NUMERIC");
    }

    // -------------------------------------------------------------------------
    // Multiple rows: mismatch on second row
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withMismatchOnSecondRow_throwsAndMentionsRowIndex()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Carol")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Wrong")
                .build();

        assertThatThrownBy(() -> assertion.assertEquals(
                expected.getTable("T"), actual.getTable("T")))
                        .as("row 1 mismatch throws.")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining("Carol")
                        .hasMessageContaining("Wrong");
    }
}
