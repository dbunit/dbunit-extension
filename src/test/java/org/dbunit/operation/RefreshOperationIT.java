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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.Reader;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.Assertion;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ForwardOnlyDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.dbunit.dataset.NoPrimaryKeyException;
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
class RefreshOperationIT extends AbstractDatabaseIT
{

    @Test
    void testExecute() throws Exception
    {
        final Reader reader =
                TestUtils.getFileReader("xml/refreshOperationTest.xml");
        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(reader);

        testExecute(dataSet);
    }

    @Test
    void testExecuteCaseInsensitive() throws Exception
    {
        final Reader reader =
                TestUtils.getFileReader("xml/refreshOperationTest.xml");
        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(reader);

        testExecute(new LowerCaseDataSet(dataSet));
    }

    @Test
    void testExecuteForwardOnly() throws Exception
    {
        final Reader reader =
                TestUtils.getFileReader("xml/refreshOperationTest.xml");
        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(reader);

        testExecute(new ForwardOnlyDataSet(dataSet));
    }

    private void testExecute(final IDataSet dataSet) throws Exception
    {
        final String[] tableNames = {"PK_TABLE", "ONLY_PK_TABLE"};
        final int[] tableRowCount = {3, 1};
        final String primaryKey = "PK0";

        // verify table before
        assertThat(tableRowCount).as("array lenght").hasSameSizeAs(tableNames);
        for (int i = 0; i < tableNames.length; i++)
        {
            final ITable tableBefore =
                    createOrderedTable(tableNames[i], primaryKey);
            assertThat(tableBefore.getRowCount()).as("row count before")
                    .isEqualTo(tableRowCount[i]);
        }

        DatabaseOperation.REFRESH.execute(_connection, dataSet);

        // verify table after
        final IDataSet expectedDataSet =
                new FlatXmlDataSetBuilder().build(TestUtils
                        .getFileReader("xml/refreshOperationTestExpected.xml"));

        for (int i = 0; i < tableNames.length; i++)
        {
            final ITable expectedTable =
                    expectedDataSet.getTable(tableNames[i]);
            final ITable tableAfter =
                    createOrderedTable(tableNames[i], primaryKey);
            Assertion.assertEquals(expectedTable, tableAfter);
        }
    }

    @Test
    void testExecuteAndNoPrimaryKeys() throws Exception
    {
        final String tableName = "TEST_TABLE";

        final Reader reader =
                TestUtils.getFileReader("xml/refreshOperationNoPKTest.xml");
        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(reader);

        // verify table before
        assertThat(_connection.getRowCount(tableName)).as("row count before")
                .isEqualTo(6);

        assertThrows(NoPrimaryKeyException.class,
                () -> DatabaseOperation.REFRESH.execute(_connection, dataSet),
                "Should not be here!");

        // verify table after
        assertThat(_connection.getRowCount(tableName)).as("row count before")
                .isEqualTo(6);
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
        DatabaseOperation.REFRESH.execute(connection, dataSet);

        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteUnknownColumn() throws Exception
    {
        final String tableName = "table";

        // setup table
        final Column[] columns =
                new Column[] {new Column("unknown", DataType.VARCHAR),};
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow();
        table.setValue(0, columns[0].getColumnName(), "value");
        final IDataSet insertDataset = new DefaultDataSet(table);

        final IDataSet databaseDataSet = new DefaultDataSet(new DefaultTable(
                tableName,
                new Column[] {new Column("column", DataType.VARCHAR),}));

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.setExpectedExecuteBatchCalls(0);
        statement.setExpectedClearBatchCalls(0);
        statement.setExpectedCloseCalls(0);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreatePreparedStatementCalls(0);
        factory.setupStatement(statement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(databaseDataSet);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        try
        {
            new RefreshOperation().execute(connection, insertDataset);
            fail("Should not be here!");
        } catch (final NoSuchColumnException e)
        {

        }

        statement.verify();
        factory.verify();
        connection.verify();
    }

}
