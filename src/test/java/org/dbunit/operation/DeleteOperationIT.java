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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileReader;
import java.io.Reader;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.LowerCaseDataSet;
import org.dbunit.dataset.NoPrimaryKeyException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public class DeleteOperationIT extends AbstractDatabaseIT
{

    @Test
    void testMockExecute() throws Exception
    {
        final String schemaName = "schema";
        final String tableName1 = "table1";
        final String tableName2 = "table2";
        final String[] expected = {
                "delete from schema.table2 where c2 = 1234 and c1 = 'toto'",
                "delete from schema.table2 where c2 = 123.45 and c1 = 'qwerty'",
                "delete from schema.table1 where c2 = 1234 and c1 = 'toto'",
                "delete from schema.table1 where c2 = 123.45 and c1 = 'qwerty'",};

        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.BOOLEAN),};
        final String[] primaryKeys = {"c2", "c1"};

        final DefaultTable table1 = new DefaultTable(
                new DefaultTableMetaData(tableName1, columns, primaryKeys));
        table1.addRow(
                new Object[] {"qwerty", Double.valueOf("123.45"), "true"});
        table1.addRow(new Object[] {"toto", "1234", Boolean.FALSE});
        final DefaultTable table2 = new DefaultTable(
                new DefaultTableMetaData(tableName2, columns, primaryKeys));
        table2.addTableRows(table1);
        final IDataSet dataSet = new DefaultDataSet(table1, table2);

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addExpectedBatchStrings(expected);
        statement.setExpectedExecuteBatchCalls(2);
        statement.setExpectedClearBatchCalls(2);
        statement.setExpectedCloseCalls(2);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreatePreparedStatementCalls(2);
        factory.setupStatement(statement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupSchema(schemaName);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        new DeleteOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithEscapedNames() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                "delete from [schema].[table] where [c2] = 123.45 and [c1] = 'qwerty'",
                "delete from [schema].[table] where [c2] = 1234 and [c1] = 'toto'",};

        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.BOOLEAN),};
        final String[] primaryKeys = {"c2", "c1"};

        final DefaultTable table = new DefaultTable(
                new DefaultTableMetaData(tableName, columns, primaryKeys));
        table.addRow(new Object[] {"toto", "1234", Boolean.FALSE});
        table.addRow(new Object[] {"qwerty", Double.valueOf("123.45"), "true"});
        final IDataSet dataSet = new DefaultDataSet(table);

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addExpectedBatchStrings(expected);
        statement.setExpectedExecuteBatchCalls(1);
        statement.setExpectedClearBatchCalls(1);
        statement.setExpectedCloseCalls(1);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreatePreparedStatementCalls(1);
        factory.setupStatement(statement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupSchema(schemaName);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        connection.getConfig()
                .setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "[?]");
        new DeleteOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithEmptyTable() throws Exception
    {
        final Column[] columns = {new Column("c1", DataType.VARCHAR)};
        final ITable table = new DefaultTable(
                new DefaultTableMetaData("name", columns, columns));
        final IDataSet dataSet = new DefaultDataSet(table);

        // setup mock objects
        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreatePreparedStatementCalls(0);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        new DeleteOperation().execute(connection, dataSet);

        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteAndNoPrimaryKey() throws Exception
    {
        final IDataSet dataSet = _connection.createDataSet();
        final ITableMetaData metaData = dataSet.getTableMetaData("TEST_TABLE");
        assertThrows(
                NoPrimaryKeyException.class, () -> new DeleteOperation()
                        .getOperationData(metaData, null, _connection),
                "Should throw a NoPrimaryKeyException");
    }

    @Test
    void testExecute() throws Exception
    {
        final Reader in = new FileReader(
                TestUtils.getFile("xml/deleteOperationTest.xml"));
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(dataSet);

    }

    @Test
    void testExecuteCaseInsensitive() throws Exception
    {
        final Reader in = new FileReader(
                TestUtils.getFile("xml/deleteOperationTest.xml"));
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(new LowerCaseDataSet(dataSet));
    }

    void testExecute(final IDataSet dataSet) throws Exception
    {
        final String tableName = "PK_TABLE";
        final String columnName = "PK0";

        // verify table before
        final ITable tableBefore = createOrderedTable(tableName, columnName);
        assertThat(tableBefore.getRowCount()).as("row count before")
                .isEqualTo(3);
        assertThat(tableBefore.getValue(0, columnName)).as("before")
                .hasToString("0");
        assertThat(tableBefore.getValue(1, columnName)).as("before")
                .hasToString("1");
        assertThat(tableBefore.getValue(2, columnName)).as("before")
                .hasToString("2");

        DatabaseOperation.DELETE.execute(_connection, dataSet);

        final ITable tableAfter = createOrderedTable(tableName, columnName);
        assertThat(tableAfter.getRowCount()).as("row count after").isEqualTo(2);
        assertThat(tableAfter.getValue(0, columnName)).as("after")
                .hasToString("0");
        assertThat(tableAfter.getValue(1, columnName)).as("after")
                .hasToString("2");
    }
}
