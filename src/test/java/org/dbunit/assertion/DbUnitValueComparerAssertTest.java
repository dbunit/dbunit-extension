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

import java.util.HashMap;
import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.DataSetBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DbUnitValueComparerAssert} using in-memory datasets.
 */
class DbUnitValueComparerAssertTest
{
    private final DbUnitValueComparerAssert assertion = new DbUnitValueComparerAssert();

    // -------------------------------------------------------------------------
    // assertWithValueComparer(ITable, ITable) — default comparer
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withIdenticalTables_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet ds = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final ITable table = ds.getTable("T");

        // Same instance — fast path skips comparison
        assertion.assertWithValueComparer(table, table);
    }

    @Test
    void testAssertWithValueComparer_withEqualTables_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertion.assertWithValueComparer(expected.getTable("T"), actual.getTable("T"));
    }

    @Test
    void testAssertWithValueComparer_withMismatch_throwsError()
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

        assertThatThrownBy(() -> assertion.assertWithValueComparer(
                expected.getTable("T"), actual.getTable("T")))
                        .as("value mismatch throws.")
                        .isInstanceOf(Error.class);
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(ITable, ITable, ValueComparer)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withIsEqualComparer_andMatchingValues_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertion.assertWithValueComparer(
                expected.getTable("T"), actual.getTable("T"),
                ValueComparers.isActualEqualToExpected);
    }

    @Test
    void testAssertWithValueComparer_withNeverFailsComparer_andMismatch_doesNotThrow()
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

        // neverFails comparer always returns null (no failure) — no exception expected
        assertion.assertWithValueComparer(
                expected.getTable("T"), actual.getTable("T"),
                ValueComparers.neverFails);
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(ITable, ITable, ValueComparer, Map<String,ValueComparer>)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withColumnSpecificComparer_usesColumnComparer()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "SomethingElse")
                .build();

        final Map<String, ValueComparer> columnComparers = new HashMap<>();
        // Override NAME column to never fail
        columnComparers.put("NAME", ValueComparers.neverFails);

        // ID uses the default (equality), NAME uses neverFails — should pass
        assertion.assertWithValueComparer(
                expected.getTable("T"), actual.getTable("T"),
                ValueComparers.isActualEqualToExpected, columnComparers);
    }

    @Test
    void testAssertWithValueComparer_withEmptyColumnComparersMap_usesDefaultComparer()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertion.assertWithValueComparer(
                expected.getTable("T"), actual.getTable("T"),
                ValueComparers.isActualEqualToExpected, new HashMap<>());
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(ITable, ITable, Column[], ValueComparer, Map)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withAdditionalColumnInfo_andMismatch_throwsError()
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

        final Column[] additionalInfo = new Column[] {new Column("ID", DataType.UNKNOWN)};

        assertThatThrownBy(() -> assertion.assertWithValueComparer(
                expected.getTable("T"), actual.getTable("T"),
                additionalInfo,
                ValueComparers.isActualEqualToExpected,
                new HashMap<>()))
                        .as("mismatch with additional info throws.")
                        .isInstanceOf(Error.class);
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(IDataSet, IDataSet) — default comparer
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withIdenticalDataSets_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet ds = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        // Same instance — fast path
        assertion.assertWithValueComparer(ds, ds);
    }

    @Test
    void testAssertWithValueComparer_withEqualDataSets_doesNotThrow()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        assertion.assertWithValueComparer(expected, actual);
    }

    @Test
    void testAssertWithValueComparer_withDifferentDataSetValues_throwsError()
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

        assertThatThrownBy(() -> assertion.assertWithValueComparer(expected, actual))
                .as("dataset value mismatch throws.")
                .isInstanceOf(Error.class);
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(IDataSet, IDataSet, ValueComparer)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withDataSetAndNeverFailsComparer_andMismatch_doesNotThrow()
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

        assertion.assertWithValueComparer(expected, actual, ValueComparers.neverFails);
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(IDataSet, IDataSet, ValueComparer, Map<String,Map>)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withTableColumnComparers_usesColumnSpecificComparer()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Different")
                .build();

        final Map<String, ValueComparer> columnComparers = new HashMap<>();
        columnComparers.put("NAME", ValueComparers.neverFails);

        final Map<String, Map<String, ValueComparer>> tableColumnComparers = new HashMap<>();
        tableColumnComparers.put("T", columnComparers);

        // ID must match (equality), NAME uses neverFails
        assertion.assertWithValueComparer(expected, actual,
                ValueComparers.isActualEqualToExpected, tableColumnComparers);
    }

    @Test
    void testAssertWithValueComparer_withMultipleTablesAndMismatchInOne_throwsError()
            throws DatabaseUnitException
    {
        final IDataSet expected = new DataSetBuilder()
                .table("A").columns("VAL").row("same")
                .table("B").columns("VAL").row("expected")
                .build();

        final IDataSet actual = new DataSetBuilder()
                .table("A").columns("VAL").row("same")
                .table("B").columns("VAL").row("different")
                .build();

        assertThatThrownBy(() -> assertion.assertWithValueComparer(expected, actual,
                ValueComparers.isActualEqualToExpected, null))
                        .as("mismatch in one table throws.")
                        .isInstanceOf(Error.class);
    }

    @Test
    void testAssertWithValueComparer_withDiffCollectingHandler_collectsDifference()
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

        assertion.assertWithValueComparer(expected.getTable("T"), actual.getTable("T"),
                handler, ValueComparers.isActualEqualToExpected, null);

        assertThat(handler.getDiffList()).as("one difference collected.").hasSize(1);

        final Difference diff = (Difference) handler.getDiffList().get(0);
        assertThat(diff.getColumnName()).as("column name.").isEqualTo("NAME");
        assertThat(diff.getExpectedValue()).as("expected value.").isEqualTo("Alice");
        assertThat(diff.getActualValue()).as("actual value.").isEqualTo("Wrong");
    }
}
