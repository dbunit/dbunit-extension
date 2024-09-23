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
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.StringReader;
import java.math.BigDecimal;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.CompositeTable;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 22, 2002
 */
public class DbUnitAssertIT
{
    public static final String FILE_PATH = "xml/assertionTest.xml";

    private DbUnitAssert assertion = new DbUnitAssert();

    private IDataSet getDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder()
                .build(TestUtils.getFileReader(FILE_PATH));
    }

    ////////////////////////////////////////////////////////////////////////////
    // Test methods

    @Test
    void testAssertTablesEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_SAME_VALUE"),
                new Column[] {new Column("COLUMN0", DataType.VARCHAR)});
    }

    @Test
    void testAssertTablesEmtpyEquals() throws Exception
    {
        final IDataSet empty1 = new XmlDataSet(
                TestUtils.getFileReader("xml/assertionTest-empty1.xml"));
        final IDataSet empty2 = new FlatXmlDataSetBuilder()
                .build(TestUtils.getFileReader("xml/assertionTest-empty2.xml"));
        assertion.assertEquals(empty1, empty2);
    }

    @Test
    void testAssertTablesEqualsColumnNamesCaseInsensitive() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_LOWER_COLUMN_NAMES"));
    }

    @Test
    void testAssertTablesAndNamesNotEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_DIFFERENT_NAME"));
    }

    @Test
    void testAssertTablesAndColumnCountNotEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final ITable testTable = dataSet.getTable("TEST_TABLE");
        final ITable testTable3 = dataSet.getTable("TEST_TABLE_WITH_3_COLUMNS");
        final String expectedMsg =
                "column count (table=TEST_TABLE, expectedColCount=4, actualColCount=3) expected:<[COLUMN0, COLUMN1, COLUMN2, COLUMN3]> but was:<[COLUMN0, COLUMN1, COLUMN2]>";
        assertThatThrownBy(() -> assertion.assertEquals(testTable, testTable3))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining(expectedMsg)
                .extracting("expected", "actual").asString()
                .contains("[COLUMN0, COLUMN1, COLUMN2, COLUMN3]",
                        "[COLUMN0, COLUMN1, COLUMN2]");
    }

    @Test
    void testAssertTablesAndColumnSequenceNotEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();

        assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_DIFFERENT_COLUMN_SEQUENCE"));
    }

    @Test
    void testAssertTablesAndColumnNamesNotEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final ITable testTable = dataSet.getTable("TEST_TABLE");
        final ITable testTable2 =
                dataSet.getTable("TEST_TABLE_WITH_DIFFERENT_COLUMN_NAMES");
        final String expectedMsg =
                "column mismatch (table=TEST_TABLE) expected:<[COLUMN0, COLUMN1, COLUMN2, COLUMN3]> but was:<[COLUMN4, COLUMN5, COLUMN6, COLUMN7]>";
        assertThatThrownBy(() -> assertion.assertEquals(testTable, testTable2))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining(expectedMsg)
                .extracting("expected", "actual").asString()
                .contains("[COLUMN0, COLUMN1, COLUMN2, COLUMN3]",
                        "[COLUMN4, COLUMN5, COLUMN6, COLUMN7]");

    }

    @Test
    void testAssertTablesAndRowCountNotEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();
        final ITable testTable = dataSet.getTable("TEST_TABLE");
        final ITable testTable2 = dataSet.getTable("TEST_TABLE_WITH_ONE_ROW");
        final String expectedMsg =
                "row count (table=TEST_TABLE) expected:<2> but was:<1>";
        assertThatThrownBy(() -> assertion.assertEquals(testTable, testTable2))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining(expectedMsg)
                .extracting("expected", "actual").asString().contains("2", "1");

    }

    @Test
    void testAssertTablesAndValuesNotEquals() throws Exception
    {
        final IDataSet dataSet = getDataSet();

        final ITable testTable = dataSet.getTable("TEST_TABLE");
        final ITable testTable2 =
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE");
        final String expectedMsg =
                "value (table=TEST_TABLE, row=1, col=COLUMN2) expected:<row 1 col 2> but was:<wrong value>";
        assertThatThrownBy(() -> assertion.assertEquals(testTable, testTable2))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining(expectedMsg)
                .extracting("expected", "actual").asString()
                .contains("row 1 col 2", "wrong value");

    }

    @Test
    void testAssertTablesWithColFilterAndValuesNotEqualExcluded()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();

        // Column2 has the wrong value, so exclude -> test should run
        // successfully
        final String[] allColumnsThatAreNotEqual = new String[] {"COLUMN2"};
        assertion.assertEqualsIgnoreCols(dataSet.getTable("TEST_TABLE"),
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"),
                allColumnsThatAreNotEqual);
    }

    @Test
    void testAssertTablesWithColFilterAndValuesNotEqualNotExcluded()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();

        // Column0 has correct value. Column2 has the wrong value but is not
        // filtered.
        // -> test should fail
        final String[] filteredColumns = new String[] {"COLUMN0"};
        final ITable testTable = dataSet.getTable("TEST_TABLE");
        final ITable testTable3 =
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE");
        final String expectedMsg =
                "value (table=TEST_TABLE, row=1, col=COLUMN2) expected:<row 1 col 2> but was:<wrong value>";
        assertThatThrownBy(() -> assertion.assertEqualsIgnoreCols(testTable,
                testTable3, filteredColumns))
                        .as("Should throw an DbComparisonFailure")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining(expectedMsg)
                        .extracting("expected", "actual").asString()
                        .contains("row 1 col 2", "wrong value");

    }

    @Test
    void testAssertTablesAndValuesNotEquals_AdditionalColumnInfo()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();

        final ITable testTable = dataSet.getTable("TEST_TABLE");
        final ITable testTable3 =
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE");
        final Column[] additionalColInfo =
                new Column[] {new Column("COLUMN0", DataType.VARCHAR)};
        final String expectedMsg =
                "org.dbunit.assertion.DbComparisonFailure[value (table=TEST_TABLE, row=1, col=COLUMN2, "
                        + "Additional row info: ('COLUMN0': expected=<row 1 col 0>, actual=<row 1 col 0>))"
                        + "expected:<row 1 col 2> but was:<wrong value>]";
        assertThatThrownBy(() -> assertion.assertEquals(testTable, testTable3,
                additionalColInfo)).as("Should throw an DbComparisonFailure")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasToString(expectedMsg)
                        .extracting("expected", "actual").asString()
                        .contains("row 1 col 2", "wrong value");

    }

    @Test
    void testAssertTablesEqualsAndIncompatibleDataType() throws Exception
    {
        final String tableName = "TABLE_NAME";

        // Setup actual table
        final Column[] actualColumns =
                new Column[] {new Column("BOOLEAN", DataType.BOOLEAN),};
        final Object[] actualRow = new Object[] {Boolean.TRUE,};
        final DefaultTable actualTable =
                new DefaultTable(tableName, actualColumns);
        actualTable.addRow(actualRow);

        // Setup expected table
        final Column[] expectedColumns =
                new Column[] {new Column("BOOLEAN", DataType.VARCHAR),};
        final Object[] expectedRow = new Object[] {"1",};
        final DefaultTable expectedTable =
                new DefaultTable(tableName, expectedColumns);
        expectedTable.addRow(expectedRow);
        final String expectedMsg =
                "Incompatible data types: (table=TABLE_NAME, col=BOOLEAN) expected:<VARCHAR> but was:<BOOLEAN>";
        assertThatThrownBy(
                () -> assertion.assertEquals(expectedTable, actualTable))
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining(expectedMsg)
                        .extracting("expected", "actual").asString()
                        .contains("VARCHAR", "BOOLEAN");

    }

    @Test
    void testAssertTablesByQueryWithColFilterAndValuesNotEqualExcluded()
            throws Exception
    {
        final DatabaseEnvironment env = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = env.getConnection();

        final IDataSet dataSet = env.getInitDataSet();
        final ITable expectedTable = dataSet.getTable("TEST_TABLE");

        final ITable table = dataSet.getTable("TEST_TABLE");
        final ITable filteredTable = new ModifyingTable(table, "COLUMN2");
        DatabaseOperation.CLEAN_INSERT.execute(connection,
                new DefaultDataSet(filteredTable));

        // Ignore COLUMN2 which has been modified by the "ModifyingTable" above
        // and
        // hence does not match.
        // When we ignore this column, the assertion should work without failure
        final String[] ignoreCols = new String[] {"COLUMN2"};
        assertion.assertEqualsByQuery(expectedTable, connection, "TEST_TABLE",
                "select * from TEST_TABLE order by 1", ignoreCols);
    }

    @Test
    void testAssertTablesByQueryWithColFilterAndValuesNotEqualNotExcluded()
            throws Exception
    {
        final DatabaseEnvironment env = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = env.getConnection();

        final IDataSet dataSet = env.getInitDataSet();
        final ITable expectedTable = dataSet.getTable("TEST_TABLE");

        final ITable table = dataSet.getTable("TEST_TABLE");
        final ITable filteredTable = new ModifyingTable(table, "COLUMN2");
        DatabaseOperation.CLEAN_INSERT.execute(connection,
                new DefaultDataSet(filteredTable));

        // Ignore COLUMN1 which has NOT been modified by the "ModifyingTable".
        // The
        // modified COLUMN2 does
        // not match and is not ignored. So the assertion should fail.
        final String[] ignoreCols = new String[] {"COLUMN1"};
        final String expectedMsg =
                "value (table=TEST_TABLE, row=0, col=COLUMN2) expected:<row 0 col 2> but was:<row 0 col 2 (modified COLUMN2)>";
        assertThatThrownBy(() -> assertion.assertEqualsByQuery(expectedTable,
                connection, "TEST_TABLE", "select * from TEST_TABLE order by 1",
                ignoreCols)).as("The assertion should not work")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining(expectedMsg)
                        .extracting("expected", "actual").asString()
                        .contains("row 0 col 2",
                                "row 0 col 2 (modified COLUMN2)");

    }

    @Test
    void testAssertTablesEqualsAndCompatibleDataType() throws Exception
    {
        final String tableName = "TABLE_NAME";
        final java.sql.Timestamp now =
                new java.sql.Timestamp(System.currentTimeMillis());

        // Setup actual table
        final Column[] actualColumns =
                new Column[] {new Column("BOOLEAN", DataType.BOOLEAN),
                        new Column("TIMESTAMP", DataType.TIMESTAMP),
                        new Column("STRING", DataType.CHAR),
                        new Column("NUMERIC", DataType.NUMERIC),};
        final Object[] actualRow =
                new Object[] {Boolean.TRUE, now, "0", new BigDecimal("123.4"),};
        final DefaultTable actualTable =
                new DefaultTable(tableName, actualColumns);
        actualTable.addRow(actualRow);

        // Setup expected table
        final Column[] expectedColumns =
                new Column[] {new Column("BOOLEAN", DataType.UNKNOWN),
                        new Column("TIMESTAMP", DataType.UNKNOWN),
                        new Column("STRING", DataType.UNKNOWN),
                        new Column("NUMERIC", DataType.UNKNOWN),};
        final Object[] expectedRow = new Object[] {"1",
                Long.valueOf(now.getTime()), Integer.valueOf("0"), "123.4000",};
        final DefaultTable expectedTable =
                new DefaultTable(tableName, expectedColumns);
        expectedTable.addRow(expectedRow);

        assertion.assertEquals(expectedTable, actualTable);
    }

    @Test
    void testAssertDataSetsEquals() throws Exception
    {
        final IDataSet dataSet1 = getDataSet();

        // change table names order
        final String[] names = DataSetUtils.getReverseTableNames(dataSet1);
        final IDataSet dataSet2 = new FilteredDataSet(names, dataSet1);

        assertThat(dataSet2).as("Datasets are the same instances.")
                .isNotSameAs(dataSet1);
        assertion.assertEquals(dataSet1, dataSet2);
    }

    @Test
    void testAssertDataSetsEqualsTableNamesCaseInsensitive() throws Exception
    {
        final IDataSet dataSet1 = getDataSet();

        // change table names case
        final String[] names = dataSet1.getTableNames();
        for (int i = 0; i < names.length; i++)
        {
            names[i] = names[i].toLowerCase();
        }
        final IDataSet dataSet2 = new FilteredDataSet(names, dataSet1);

        assertThat(dataSet2).as("Datasets are the same instances.")
                .isNotSameAs(dataSet1);
        assertion.assertEquals(dataSet1, dataSet2);
    }

    @Test
    void testAssertDataSetsTableNamesCaseSensitiveNotEquals() throws Exception
    {
        final IDataSet dataSet1 = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(TestUtils.getFileReader(
                        "xml/assertion_table_name_case_sensitive_1.xml"));
        final IDataSet dataSet2 = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(TestUtils.getFileReader(
                        "xml/assertion_table_name_case_sensitive_2.xml"));
        final DbComparisonFailure expected2 = catchThrowableOfType(
                () -> assertion.assertEquals(dataSet1, dataSet2),
                DbComparisonFailure.class);
        assertThat(expected2.getExpected())
                .as("[TEST_TABLE_WITH_CASE_SENSITIVE_NAME]").toString()
                .contains("Expected table name did not match.");
        assertThat(expected2.getActual()).as("Actual table name did not match.")
                .toString().contains("[test_table_with_case_sensitive_name]");

    }

    @Test
    void testAssertDataSetsTableNamesCaseSensitiveWithLowerCaseEquals()
            throws Exception
    {
        final IDataSet dataSet1 = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(TestUtils.getFileReader(
                        "xml/assertion_table_name_case_sensitive_with_lower_case.xml"));
        final IDataSet dataSet2 = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(TestUtils.getFileReader(
                        "xml/assertion_table_name_case_sensitive_with_lower_case.xml"));
        assertThat(dataSet2).as("Datasets are the same instances.")
                .isNotSameAs(dataSet1);
        assertion.assertEquals(dataSet1, dataSet2);
    }

    @Test
    void testAssertDataSetsAndTableCountNotEquals() throws Exception
    {
        final IDataSet dataSet1 = getDataSet();

        // only one table
        final String[] names = new String[] {dataSet1.getTableNames()[0]};
        final IDataSet dataSet2 = new FilteredDataSet(names, dataSet1);

        assertThat(dataSet2).as("Datasets are the same instances.")
                .isNotSameAs(dataSet1);

        assertThatThrownBy(() -> assertion.assertEquals(dataSet1, dataSet2))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining("table count expected:<10> but was:<1>")
                .extracting("expected", "actual").asString()
                .contains("10", "1");

    }

    @Test
    void testAssertDataSetsAndTableNamesNotEquals() throws Exception
    {
        final IDataSet dataSet1 = getDataSet();

        // reverse table names
        final String[] names = dataSet1.getTableNames();
        final ITable[] tables = new ITable[names.length];
        for (int i = 0; i < names.length; i++)
        {
            final String reversedName =
                    new StringBuilder(names[i]).reverse().toString();
            tables[i] = new CompositeTable(reversedName,
                    dataSet1.getTable(names[i]));
        }
        final IDataSet dataSet2 = new DefaultDataSet(tables);

        assertThat(dataSet2).as("Datasets are the same instances.")
                .isNotSameAs(dataSet1);
        assertThat(dataSet2.getTableNames()).as("table count")
                .hasSameSizeAs(dataSet1.getTableNames());

        assertThatThrownBy(() -> assertion.assertEquals(dataSet1, dataSet2))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testAssertDataSetsAndTablesNotEquals() throws Exception
    {
        final IDataSet dataSet1 = getDataSet();

        // different row counts (double)
        final IDataSet dataSet2 = new CompositeDataSet(dataSet1, dataSet1);

        assertThat(dataSet2).as("Datasets are the same instances.")
                .isNotSameAs(dataSet1);
        assertThat(dataSet2.getTableNames()).as("table count")
                .hasSameSizeAs(dataSet1.getTableNames());

        assertThatThrownBy(() -> assertion.assertEquals(dataSet1, dataSet2))
                .as("Should throw an DbComparisonFailure")
                .isInstanceOf(DbComparisonFailure.class)
                .hasMessageContaining(
                        "row count (table=TEST_TABLE) expected:<2> but was:<4>")
                .extracting("expected", "actual").asString().contains("2", "4");

    }

    @Test
    void testAssertDataSetsWithFailureHandler() throws Exception
    {
        final DiffCollectingFailureHandler fh =
                new DiffCollectingFailureHandler();

        final String xml1 = "<dataset>\n"
                + "<TEST_TABLE COLUMN0='row 0 col 0' COLUMN1='row 0 col 1'/>\n"
                + "</dataset>\n";
        final IDataSet dataSet1 =
                new FlatXmlDataSetBuilder().build(new StringReader(xml1));
        final String xml2 = "<dataset>\n"
                + "<TEST_TABLE COLUMN0='row 0 col somthing' COLUMN1='row 0 col something mysterious'/>\n"
                + "</dataset>\n";
        final IDataSet dataSet2 =
                new FlatXmlDataSetBuilder().build(new StringReader(xml2));

        // Invoke the assertion
        assertion.assertEquals(dataSet1, dataSet2, fh);
        // We expect that no failure was thrown even if the dataSets were not
        // equal.
        // This is because our custom failureHandler
        assertThat(fh.getDiffList()).hasSize(2);
    }

    @Test
    void testGetComparisonDataType_ExpectedTypeUnknown()
    {
        final Column expectedColumn = new Column("COL1", DataType.UNKNOWN);
        final Column actualColumn = new Column("COL1", DataType.VARCHAR);
        final DataType dataType = new DbUnitAssert.ComparisonColumn(
                "BLABLA_TABLE_NOT_NEEDED_HERE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler()).getDataType();
        assertThat(dataType).isEqualTo(DataType.VARCHAR);
    }

    @Test
    void testGetComparisonDataType_ActualTypeUnknown()
    {
        final Column expectedColumn = new Column("COL1", DataType.VARCHAR);
        final Column actualColumn = new Column("COL1", DataType.UNKNOWN);
        final DataType dataType = new DbUnitAssert.ComparisonColumn(
                "BLABLA_TABLE_NOT_NEEDED_HERE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler()).getDataType();
        assertThat(dataType).isEqualTo(DataType.VARCHAR);
    }

    @Test
    void testGetComparisonDataType_BothTypesSetIncompatible()
    {
        final Column expectedColumn = new Column("COL1", DataType.VARCHAR);
        final Column actualColumn = new Column("COL1", DataType.NUMERIC);
        final String expectedMsg =
                "Incompatible data types: (table=BLABLA_TABLE_NOT_NEEDED_HERE, col=COL1) expected:<VARCHAR> but was:<NUMERIC>";
        final FailureHandler failureHandler =
                assertion.getDefaultFailureHandler();
        assertThatThrownBy(() -> new DbUnitAssert.ComparisonColumn(
                "BLABLA_TABLE_NOT_NEEDED_HERE", expectedColumn, actualColumn,
                failureHandler)).as("Incompatible datatypes should not work")
                        .isInstanceOf(DbComparisonFailure.class)
                        .hasMessageContaining(expectedMsg)
                        .extracting("expected", "actual").asString()
                        .contains("VARCHAR", "NUMERIC");
    }

    @Test
    void testGetComparisonDataType_BothTypesSetToSame()
    {
        final Column expectedColumn = new Column("COL1", DataType.VARCHAR);
        final Column actualColumn = new Column("COL1", DataType.VARCHAR);
        final DataType dataType = new DbUnitAssert.ComparisonColumn(
                "BLABLA_TABLE_NOT_NEEDED_HERE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler()).getDataType();
        assertThat(dataType).isEqualTo(DataType.VARCHAR);
    }

    @Test
    void testGetComparisonDataType_BothTypesUnknown()
    {
        final Column expectedColumn = new Column("COL1", DataType.UNKNOWN);
        final Column actualColumn = new Column("COL1", DataType.UNKNOWN);
        final DataType dataType = new DbUnitAssert.ComparisonColumn(
                "BLABLA_TABLE_NOT_NEEDED_HERE", expectedColumn, actualColumn,
                assertion.getDefaultFailureHandler()).getDataType();
        assertThat(dataType).isEqualTo(DataType.UNKNOWN);
    }

    /**
     * Test utility that modifies all values for a specific column arbitrarily
     */
    protected static class ModifyingTable implements ITable
    {
        private ITable _wrappedTable;
        private String _columnToModify;

        public ModifyingTable(final ITable originalTable,
                final String columnToModify)
        {
            this._wrappedTable = originalTable;
            this._columnToModify = columnToModify;
        }

        @Override
        public int getRowCount()
        {
            return this._wrappedTable.getRowCount();
        }

        @Override
        public ITableMetaData getTableMetaData()
        {
            return this._wrappedTable.getTableMetaData();
        }

        @Override
        public Object getValue(final int row, final String column)
                throws DataSetException
        {
            final Object originalValue = _wrappedTable.getValue(row, column);

            // Modify the value if column name matches
            if (column.equalsIgnoreCase(this._columnToModify))
            {
                return String.valueOf(originalValue) + " (modified "
                        + _columnToModify + ")";
            }
            return originalValue;
        }

    }

}
