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
package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileReader;
import java.io.Reader;
import java.sql.SQLException;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.Assertion;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.TestFeature;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.ForwardOnlyDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.dbunit.dataset.SortedTable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public class InsertOperationIT extends AbstractDatabaseIT
{

    // public void testExecuteNullAsNone() throws Exception
    // {
    // String schemaName = "schema";
    // String tableName = "table";
    // String[] expected = {
    // "insert into schema.table (c1, c2, c3) values ('toto', 1234, 'false')",
    // "insert into schema.table (c2, c3) values (123.45, 'true')",
    // "insert into schema.table (c1, c2, c3) values ('qwerty1', 1, 'true')",
    // "insert into schema.table (c1, c2, c3) values ('qwerty2', 2, 'false')",
    // "insert into schema.table (c3) values ('false')",
    // };
    //
    // // setup table
    // List valueList = new ArrayList();
    // valueList.add(new Object[]{"toto", "1234", Boolean.FALSE});
    // valueList.add(new Object[]{null, new Double("123.45"), "true"});
    // valueList.add(new Object[]{"qwerty1", "1", Boolean.TRUE});
    // valueList.add(new Object[]{"qwerty2", "2", Boolean.FALSE});
    // valueList.add(new Object[]{null, null, Boolean.FALSE});
    // Column[] columns = new Column[]{
    // new Column("c1", DataType.VARCHAR),
    // new Column("c2", DataType.NUMERIC),
    // new Column("c3", DataType.BOOLEAN),
    // };
    // DefaultTable table = new DefaultTable(tableName, columns, valueList);
    // IDataSet dataSet = new DefaultDataSet(table);
    //
    // // setup mock objects
    // MockBatchStatement statement = new MockBatchStatement();
    // statement.addExpectedBatchStrings(expected);
    // statement.setExpectedExecuteBatchCalls(4);
    // statement.setExpectedClearBatchCalls(4);
    // statement.setExpectedCloseCalls(4);
    //
    // MockStatementFactory factory = new MockStatementFactory();
    // factory.setExpectedCreatePreparedStatementCalls(4);
    // factory.setupStatement(statement);
    //
    // MockDatabaseConnection connection = new MockDatabaseConnection();
    // connection.setupDataSet(dataSet);
    // connection.setupSchema(schemaName);
    // connection.setupStatementFactory(factory);
    // connection.setExpectedCloseCalls(0);
    // DatabaseConfig config = connection.getConfig();
    // config.setFeature(DatabaseConfig.FEATURE_NULL_AS_NONE, true);
    //
    // // execute operation
    // new InsertOperation().execute(connection, dataSet);
    //
    // statement.verify();
    // factory.verify();
    // connection.verify();
    // }

    @Test
    void testExecute_withClobData_insertsClobSuccessfully() throws Exception
    {
        // execute this test only if the target database support CLOB
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.CLOB))
        {
            final String tableName = "CLOB_TABLE";

            final Reader in =
                    new FileReader(TestUtils.getFile("xml/clobInsertTest.xml"));
            final IDataSet xmlDataSet = new FlatXmlDataSetBuilder().build(in);

            assertThat(_connection.getRowCount(tableName)).as("count before")
                    .isEqualTo(0);

            DatabaseOperation.INSERT.execute(_connection, xmlDataSet);

            final ITable tableAfter =
                    _connection.createDataSet().getTable(tableName);
            assertThat(tableAfter.getRowCount()).as("count after").isEqualTo(3);
            Assertion.assertEquals(xmlDataSet.getTable(tableName), tableAfter);
        }
    }

    @Test
    void testExecute_withBlobData_insertsBlobSuccessfully() throws Exception
    {
        // execute this test only if the target database support BLOB
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.BLOB))
        {
            final String tableName = "BLOB_TABLE";

            final Reader in =
                    new FileReader(TestUtils.getFile("xml/blobInsertTest.xml"));
            final IDataSet xmlDataSet = new FlatXmlDataSetBuilder().build(in);

            assertThat(_connection.getRowCount(tableName)).as("count before")
                    .isEqualTo(0);

            DatabaseOperation.INSERT.execute(_connection, xmlDataSet);

            final ITable tableAfter =
                    _connection.createDataSet().getTable(tableName);
            assertThat(tableAfter.getRowCount()).as("count after").isEqualTo(3);
            Assertion.assertEquals(xmlDataSet.getTable(tableName), tableAfter);
        }
    }

    @Test
    void testExecute_withSdoGeometryData_insertsSdoGeometrySuccessfully() throws Exception
    {
        // execute this test only if the target database supports SDO_GEOMETRY
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.SDO_GEOMETRY))
        {
            final String tableName = "SDO_GEOMETRY_TABLE";

            final Reader in = new FileReader(
                    TestUtils.getFile("xml/sdoGeometryInsertTest.xml"));
            final IDataSet xmlDataSet = new FlatXmlDataSetBuilder().build(in);

            assertThat(_connection.getRowCount(tableName)).as("count before")
                    .isEqualTo(0);

            DatabaseOperation.INSERT.execute(_connection, xmlDataSet);

            final ITable tableAfter =
                    _connection.createDataSet().getTable(tableName);
            assertThat(tableAfter.getRowCount()).as("count after").isEqualTo(1);
            Assertion.assertEquals(xmlDataSet.getTable(tableName), tableAfter);
        }
    }

    @Test
    void testExecute_withXmlTypeData_insertsXmlTypeSuccessfully() throws Exception
    {
        // execute this test only if the target database support CLOB
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.XML_TYPE))
        {
            final String tableName = "XML_TYPE_TABLE";

            final Reader in = new FileReader(
                    TestUtils.getFile("xml/xmlTypeInsertTest.xml"));
            final IDataSet xmlDataSet = new FlatXmlDataSetBuilder().build(in);

            assertThat(_connection.getRowCount(tableName)).as("count before")
                    .isEqualTo(0);

            DatabaseOperation.INSERT.execute(_connection, xmlDataSet);

            final ITable tableAfter =
                    _connection.createDataSet().getTable(tableName);
            assertThat(tableAfter.getRowCount()).as("count after").isEqualTo(3);
            Assertion.assertEquals(xmlDataSet.getTable(tableName), tableAfter);
        }
    }

    @Test
    void testExecute_withMissingColumns_nullsUnspecifiedColumns() throws Exception
    {
        final Reader in = TestUtils.getFileReader("xml/missingColumnTest.xml");
        final IDataSet xmlDataSet = new XmlDataSet(in);

        final ITable[] tablesBefore =
                DataSetUtils.getTables(_connection.createDataSet());
        DatabaseOperation.INSERT.execute(_connection, xmlDataSet);
        final ITable[] tablesAfter =
                DataSetUtils.getTables(_connection.createDataSet());

        // verify tables before
        for (int i = 0; i < tablesBefore.length; i++)
        {
            final ITable table = tablesBefore[i];
            final String tableName = table.getTableMetaData().getTableName();
            if (tableName.startsWith("EMPTY"))
            {
                assertThat(table.getRowCount()).as(tableName + " before")
                        .isZero();
            }
        }

        // verify tables after
        for (int i = 0; i < tablesAfter.length; i++)
        {
            final ITable databaseTable = tablesAfter[i];
            final String tableName =
                    databaseTable.getTableMetaData().getTableName();

            if (tableName.startsWith("EMPTY"))
            {
                final Column[] columns =
                        databaseTable.getTableMetaData().getColumns();
                final ITable xmlTable = xmlDataSet.getTable(tableName);

                // verify row count
                assertThat(databaseTable.getRowCount()).as("row count")
                        .isEqualTo(xmlTable.getRowCount());

                // for each table row
                for (int j = 0; j < databaseTable.getRowCount(); j++)
                {
                    // verify first column values
                    final Object expected =
                            xmlTable.getValue(j, columns[0].getColumnName());
                    final Object actual = databaseTable.getValue(j,
                            columns[0].getColumnName());

                    assertThat(actual)
                            .as(tableName + "." + columns[0].getColumnName())
                            .isEqualTo(expected);

                    // all remaining columns should be null except mssql server
                    // timestamp column which is of type binary.
                    for (int k = 1; k < columns.length; k++)
                    {
                        final String columnName = columns[k].getColumnName();
                        assertThat(databaseTable.getValue(j, columnName))
                                .as(tableName + "." + columnName).isNull();
                    }
                }
            }
        }

    }

    @Test
    void testExecute_withXmlDataSet_insertsAllRows() throws Exception
    {
        final Reader in =
                TestUtils.getFileReader("xml/insertOperationTest.xml");
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(dataSet);
    }

    @Test
    void testExecute_withLowerCaseDataSet_insertsAllRows() throws Exception
    {
        final Reader in =
                TestUtils.getFileReader("xml/insertOperationTest.xml");
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(new LowerCaseDataSet(dataSet));
    }

    @Test
    void testExecute_withForwardOnlyDataSet_insertsAllRows() throws Exception
    {
        final Reader in =
                TestUtils.getFileReader("xml/insertOperationTest.xml");
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(new ForwardOnlyDataSet(dataSet));
    }

    @Test
    void testExecute_batchedStatements_allRowsInserted() throws Exception
    {
        final Reader in = TestUtils.getFileReader("xml/insertOperationTest.xml");
        final IDataSet dataSet = new XmlDataSet(in);

        _connection.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);

        final ITable[] tablesBefore = DataSetUtils.getTables(_connection.createDataSet());
        DatabaseOperation.INSERT.execute(_connection, dataSet);
        final ITable[] tablesAfter = DataSetUtils.getTables(_connection.createDataSet());

        assertThat(tablesAfter).as("table count.").hasSameSizeAs(tablesBefore);
        for (final ITable table : tablesAfter)
        {
            final String name = table.getTableMetaData().getTableName();
            if (name.startsWith("EMPTY"))
            {
                final SortedTable expectedTable =
                        new SortedTable(dataSet.getTable(name),
                                dataSet.getTable(name).getTableMetaData());
                final SortedTable actualTable = new SortedTable(table);
                Assertion.assertEquals(expectedTable, actualTable);
            }
        }
    }

    @Test
    void testExecute_nullValues_insertedAsNull() throws Exception
    {
        final String tableName = "EMPTY_TABLE";
        final Column[] columns = new Column[]{
                new Column("COLUMN0", DataType.VARCHAR),
                new Column("COLUMN1", DataType.VARCHAR),
                new Column("COLUMN2", DataType.VARCHAR),
                new Column("COLUMN3", DataType.VARCHAR)
        };
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow(new Object[]{"notNull", null, null, null});
        final IDataSet dataSet = new DefaultDataSet(table);

        assertThat(_connection.getRowCount(tableName)).as("count before.").isZero();

        DatabaseOperation.INSERT.execute(_connection, dataSet);

        final ITable actual = _connection.createDataSet().getTable(tableName);
        assertThat(actual.getRowCount()).as("count after.").isEqualTo(1);
        assertThat(actual.getValue(0, "COLUMN0")).as("COLUMN0.").isEqualTo("notNull");
        assertThat(actual.getValue(0, "COLUMN1")).as("COLUMN1.").isNull();
        assertThat(actual.getValue(0, "COLUMN2")).as("COLUMN2.").isNull();
    }

    @Test
    void testExecute_emptyStringWithAllowEmptyFields_insertedAsEmptyString() throws Exception
    {
        final String tableName = "EMPTY_TABLE";
        final Column[] columns = new Column[]{
                new Column("COLUMN0", DataType.VARCHAR),
                new Column("COLUMN1", DataType.VARCHAR),
                new Column("COLUMN2", DataType.VARCHAR),
                new Column("COLUMN3", DataType.VARCHAR)
        };
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow(new Object[]{"hasValue", "", "", ""});
        final IDataSet dataSet = new DefaultDataSet(table);

        _connection.getConfig().setFeature(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, true);

        assertThat(_connection.getRowCount(tableName)).as("count before.").isZero();

        DatabaseOperation.INSERT.execute(_connection, dataSet);

        final ITable actual = _connection.createDataSet().getTable(tableName);
        assertThat(actual.getRowCount()).as("count after.").isEqualTo(1);
        assertThat(actual.getValue(0, "COLUMN0")).as("COLUMN0.").isEqualTo("hasValue");
    }

    private void testExecute(final IDataSet dataSet)
            throws Exception, SQLException
    {
        final ITable[] tablesBefore =
                DataSetUtils.getTables(_connection.createDataSet());
        DatabaseOperation.INSERT.execute(_connection, dataSet);
        final ITable[] tablesAfter =
                DataSetUtils.getTables(_connection.createDataSet());

        assertThat(tablesAfter).as("table count").hasSameSizeAs(tablesBefore);
        for (int i = 0; i < tablesBefore.length; i++)
        {
            final ITable table = tablesBefore[i];
            final String name = table.getTableMetaData().getTableName();

            if (name.startsWith("EMPTY"))
            {
                assertThat(table.getRowCount()).as(name + "before").isZero();
            }
        }

        for (int i = 0; i < tablesAfter.length; i++)
        {
            final ITable table = tablesAfter[i];
            final String name = table.getTableMetaData().getTableName();

            if (name.startsWith("EMPTY"))
            {
                if (dataSet instanceof ForwardOnlyDataSet)
                {
                    assertThat(table.getRowCount()).as(name).isPositive();
                } else
                {
                    final SortedTable expectedTable =
                            new SortedTable(dataSet.getTable(name),
                                    dataSet.getTable(name).getTableMetaData());
                    final SortedTable actualTable = new SortedTable(table);
                    Assertion.assertEquals(expectedTable, actualTable);
                }
            }
        }
    }
}
