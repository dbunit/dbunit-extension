/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.assertion.DbUnitAssert;
import org.dbunit.assertion.DbUnitValueComparerAssert;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.dbunit.dataset.DataSetBuilder;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Assertion} static facade.
 *
 * <p>These tests verify that each static method correctly delegates to the
 * underlying {@link DbUnitAssert} or {@link DbUnitValueComparerAssert} instance.
 * They complement the deeper behaviour tests in {@code DbUnitAssertTest}.
 */
class AssertionTest
{
    // -------------------------------------------------------------------------
    // Instance accessors
    // -------------------------------------------------------------------------

    @Test
    void testGetEqualsInstance_onNewInstance_returnsNonNullDbUnitAssert()
    {
        assertThat(Assertion.getEqualsInstance()).as("getEqualsInstance() returns non-null.")
                .isNotNull().isInstanceOf(DbUnitAssert.class);
    }

    @Test
    void testGetValueCompareInstance_onNewInstance_returnsNonNullDbUnitValueComparerAssert()
    {
        assertThat(Assertion.getValueCompareInstance())
                .as("getValueCompareInstance() returns non-null.")
                .isNotNull().isInstanceOf(DbUnitValueComparerAssert.class);
    }

    // -------------------------------------------------------------------------
    // assertEquals(ITable, ITable)
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withEqualTables_doesNotThrow() throws Exception
    {
        final IDataSet ds = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();
        final ITable table = ds.getTable("T");

        Assertion.assertEquals(table, table);
    }

    @Test
    void testAssertEquals_withDifferentTables_throwsDbComparisonFailure() throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(2)
                .build().getTable("T");

        assertThatThrownBy(() -> Assertion.assertEquals(expected, actual))
                .as("value mismatch throws.")
                .isInstanceOf(DbComparisonFailure.class);
    }

    // -------------------------------------------------------------------------
    // assertEquals(ITable, ITable, Column[])
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withAdditionalColumnInfo_doesNotThrow() throws Exception
    {
        final IDataSet ds = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build();
        final ITable table = ds.getTable("T");

        Assertion.assertEquals(table, table, new org.dbunit.dataset.Column[0]);
    }

    // -------------------------------------------------------------------------
    // assertEquals(ITable, ITable, FailureHandler)
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withFailureHandler_collectsDifferencesWithoutThrowing()
            throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID", "VAL")
                .row(1, "A")
                .row(2, "B")
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID", "VAL")
                .row(1, "X")
                .row(2, "Y")
                .build().getTable("T");

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        Assertion.assertEquals(expected, actual, handler);

        assertThat(handler.getDiffList()).as("two differences collected.").hasSize(2);
    }

    // -------------------------------------------------------------------------
    // assertEquals(IDataSet, IDataSet)
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_withEqualDataSets_doesNotThrow() throws Exception
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();
        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();

        Assertion.assertEquals(expected, actual);
    }

    @Test
    void testAssertEquals_withDifferentDataSets_throwsDbComparisonFailure() throws Exception
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();
        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(2)
                .build();

        assertThatThrownBy(() -> Assertion.assertEquals(expected, actual))
                .as("dataset value mismatch throws.")
                .isInstanceOf(DbComparisonFailure.class);
    }

    // -------------------------------------------------------------------------
    // assertEquals(IDataSet, IDataSet, FailureHandler)
    // -------------------------------------------------------------------------

    @Test
    void testAssertEquals_dataSetWithFailureHandler_collectsDifferences() throws Exception
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();
        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(99)
                .build();

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        Assertion.assertEquals(expected, actual, handler);

        assertThat(handler.getDiffList()).as("difference collected.").isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // assertEqualsIgnoreCols(ITable, ITable, String[])
    // -------------------------------------------------------------------------

    @Test
    void testAssertEqualsIgnoreCols_withDifferenceInIgnoredColumn_doesNotThrow()
            throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID", "CREATED")
                .row(1, "2020-01-01")
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID", "CREATED")
                .row(1, "2030-12-31")
                .build().getTable("T");

        Assertion.assertEqualsIgnoreCols(expected, actual, new String[] {"CREATED"});
    }

    @Test
    void testAssertEqualsIgnoreCols_withDifferenceInNonIgnoredColumn_throwsDbComparisonFailure()
            throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Alice")
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID", "NAME")
                .row(1, "Bob")
                .build().getTable("T");

        assertThatThrownBy(() -> Assertion.assertEqualsIgnoreCols(expected, actual,
                new String[] {"ID"}))
                        .as("non-ignored column mismatch throws.")
                        .isInstanceOf(DbComparisonFailure.class);
    }

    // -------------------------------------------------------------------------
    // assertEqualsIgnoreCols(IDataSet, IDataSet, String, String[])
    // -------------------------------------------------------------------------

    @Test
    void testAssertEqualsIgnoreCols_viaDataSet_withIgnoredColumnDifferent_doesNotThrow()
            throws Exception
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID", "EXTRA")
                .row(1, "x")
                .build();
        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID", "EXTRA")
                .row(1, "y")
                .build();

        Assertion.assertEqualsIgnoreCols(expected, actual, "T", new String[] {"EXTRA"});
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(ITable, ITable, ValueComparer, Map)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withEqualTables_doesNotThrow() throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");

        Assertion.assertWithValueComparer(expected, actual,
                ValueComparers.isActualEqualToExpected, Collections.emptyMap());
    }

    @Test
    void testAssertWithValueComparer_withDifferentTables_throwsDbComparisonFailure()
            throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(2)
                .build().getTable("T");

        assertThatThrownBy(() -> Assertion.assertWithValueComparer(expected, actual,
                ValueComparers.isActualEqualToExpected, Collections.emptyMap()))
                        .as("value comparer mismatch throws.")
                        .isInstanceOf(DbComparisonFailure.class);
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(IDataSet, IDataSet, ValueComparer, Map)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withEqualDataSets_doesNotThrow() throws Exception
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();
        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();

        Assertion.assertWithValueComparer(expected, actual,
                ValueComparers.isActualEqualToExpected, Collections.emptyMap());
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer — FailureHandler overloads
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_tableWithFailureHandler_collectsDifferences()
            throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(99)
                .build().getTable("T");

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        Assertion.assertWithValueComparer(expected, actual, handler,
                ValueComparers.isActualEqualToExpected, Collections.emptyMap());

        assertThat(handler.getDiffList()).as("difference collected.").isNotEmpty();
    }

    @Test
    void testAssertWithValueComparer_dataSetWithFailureHandler_collectsDifferences()
            throws Exception
    {
        final IDataSet expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build();
        final IDataSet actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(99)
                .build();

        final DiffCollectingFailureHandler handler = new DiffCollectingFailureHandler();
        Assertion.assertWithValueComparer(expected, actual, handler,
                ValueComparers.isActualEqualToExpected, Collections.emptyMap());

        assertThat(handler.getDiffList()).as("difference collected.").isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // assertWithValueComparer(ITable, ITable, Column[], ValueComparer, Map)
    // -------------------------------------------------------------------------

    @Test
    void testAssertWithValueComparer_withAdditionalColumnInfo_doesNotThrow() throws Exception
    {
        final ITable expected = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");
        final ITable actual = new DataSetBuilder()
                .table("T").columns("ID")
                .row(1)
                .build().getTable("T");

        Assertion.assertWithValueComparer(expected, actual,
                new org.dbunit.dataset.Column[0],
                ValueComparers.isActualEqualToExpected, Collections.emptyMap());
    }
}
