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

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Difference}.
 */
class DifferenceTest
{
    private static final String TABLE_NAME = "MY_TABLE";
    private static final String COL_NAME = "MY_COL";
    private static final Object EXPECTED_VALUE = "expected_val";
    private static final Object ACTUAL_VALUE = "actual_val";
    private static final int ROW_INDEX = 3;

    private ITable buildTable() throws DataSetException
    {
        final Column[] cols = new Column[] {new Column(COL_NAME, DataType.UNKNOWN)};
        return new DefaultTable(TABLE_NAME, cols);
    }

    @Test
    void testConstructor_withAllFields_storesAllFields() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();
        final String failMessage = "fail reason";

        final Difference diff = new Difference(expectedTable, actualTable,
                ROW_INDEX, COL_NAME, EXPECTED_VALUE, ACTUAL_VALUE, failMessage);

        assertThat(diff.getExpectedTable()).as("expectedTable.").isSameAs(expectedTable);
        assertThat(diff.getActualTable()).as("actualTable.").isSameAs(actualTable);
        assertThat(diff.getRowIndex()).as("rowIndex.").isEqualTo(ROW_INDEX);
        assertThat(diff.getColumnName()).as("columnName.").isEqualTo(COL_NAME);
        assertThat(diff.getExpectedValue()).as("expectedValue.").isEqualTo(EXPECTED_VALUE);
        assertThat(diff.getActualValue()).as("actualValue.").isEqualTo(ACTUAL_VALUE);
        assertThat(diff.getFailMessage()).as("failMessage.").isEqualTo(failMessage);
    }

    @Test
    void testConstructor_withoutFailMessage_defaultsToEmptyString() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();

        final Difference diff = new Difference(expectedTable, actualTable,
                ROW_INDEX, COL_NAME, EXPECTED_VALUE, ACTUAL_VALUE);

        assertThat(diff.getFailMessage()).as("failMessage default.").isEqualTo("");
    }

    @Test
    void testSetFailMessage_withNewMessage_updatesMessage() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();
        final Difference diff = new Difference(expectedTable, actualTable,
                ROW_INDEX, COL_NAME, EXPECTED_VALUE, ACTUAL_VALUE);

        final String newMessage = "updated fail reason";
        diff.setFailMessage(newMessage);

        assertThat(diff.getFailMessage()).as("updated failMessage.").isEqualTo(newMessage);
    }

    @Test
    void testToString_withAllFields_containsKeyParts() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();

        final Difference diff = new Difference(expectedTable, actualTable,
                ROW_INDEX, COL_NAME, EXPECTED_VALUE, ACTUAL_VALUE, "some reason");

        final String result = diff.toString();

        assertThat(result).as("toString class name.").contains(Difference.class.getName());
        assertThat(result).as("toString rowIndex.").contains(String.valueOf(ROW_INDEX));
        assertThat(result).as("toString columnName.").contains(COL_NAME);
        assertThat(result).as("toString expectedValue.").contains(String.valueOf(EXPECTED_VALUE));
        assertThat(result).as("toString actualValue.").contains(String.valueOf(ACTUAL_VALUE));
        assertThat(result).as("toString failMessage.").contains("some reason");
    }

    @Test
    void testGetRowIndex_withZeroIndex_returnsZero() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();

        final Difference diff = new Difference(expectedTable, actualTable,
                0, COL_NAME, EXPECTED_VALUE, ACTUAL_VALUE);

        assertThat(diff.getRowIndex()).as("rowIndex zero.").isZero();
    }

    @Test
    void testGetExpectedValue_withNullExpected_returnsNull() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();

        final Difference diff = new Difference(expectedTable, actualTable,
                ROW_INDEX, COL_NAME, null, ACTUAL_VALUE);

        assertThat(diff.getExpectedValue()).as("expectedValue null.").isNull();
    }

    @Test
    void testGetActualValue_withNullActual_returnsNull() throws DataSetException
    {
        final ITable expectedTable = buildTable();
        final ITable actualTable = buildTable();

        final Difference diff = new Difference(expectedTable, actualTable,
                ROW_INDEX, COL_NAME, EXPECTED_VALUE, null);

        assertThat(diff.getActualValue()).as("actualValue null.").isNull();
    }
}
