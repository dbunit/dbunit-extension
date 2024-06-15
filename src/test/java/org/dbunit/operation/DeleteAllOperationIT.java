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

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.EmptyTableDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Eric Pugh TODO Refactor all the references to
 *         AbstractDataSetTest.removeExtraTestTables() to something better.
 * @version $Revision$
 * @since Feb 18, 2002
 */
class DeleteAllOperationIT extends AbstractDatabaseIT
{

    @Override
    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUp();

        DatabaseOperation.CLEAN_INSERT.execute(_connection,
                getEnvironment().getInitDataSet());
    }

    protected DatabaseOperation getDeleteAllOperation()
    {
        return new DeleteAllOperation();
    }

    protected String getExpectedStament(final String tableName)
    {
        return "delete from " + tableName;
    }

    @Test
    void testMockExecute() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String expected =
                getExpectedStament(schemaName + "." + tableName);

        final IDataSet dataSet =
                new DefaultDataSet(new DefaultTable(tableName));

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addExpectedBatchString(expected);
        statement.setExpectedExecuteBatchCalls(1);
        statement.setExpectedClearBatchCalls(1);
        statement.setExpectedCloseCalls(1);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreateStatementCalls(1);
        factory.setupStatement(statement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupSchema(schemaName);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        getDeleteAllOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithEscapedNames() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String expected =
                getExpectedStament("'" + schemaName + "'.'" + tableName + "'");

        final IDataSet dataSet =
                new DefaultDataSet(new DefaultTable(tableName));

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addExpectedBatchString(expected);
        statement.setExpectedExecuteBatchCalls(1);
        statement.setExpectedClearBatchCalls(1);
        statement.setExpectedCloseCalls(1);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreateStatementCalls(1);
        factory.setupStatement(statement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupSchema(schemaName);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        connection.getConfig()
                .setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "'?'");
        getDeleteAllOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecute() throws Exception
    {
        final IDataSet databaseDataSet = _connection.createDataSet();
        final IDataSet dataSet =
                AbstractDataSetTest.removeExtraTestTables(databaseDataSet);

        testExecute(dataSet);
    }

    @Test
    void testExecuteEmpty() throws Exception
    {
        final IDataSet databaseDataSet = _connection.createDataSet();
        final IDataSet dataSet =
                AbstractDataSetTest.removeExtraTestTables(databaseDataSet);

        testExecute(new EmptyTableDataSet(dataSet));
    }

    @Test
    void testExecuteCaseInsentive() throws Exception
    {
        final IDataSet dataSet = AbstractDataSetTest
                .removeExtraTestTables(_connection.createDataSet());

        testExecute(new LowerCaseDataSet(dataSet));
    }

    /*
     * The AbstractDataSetTest.removeExtraTestTables() is required when you run
     * on something besides hypersone (like mssql or oracle) to deal with the
     * extra tables that may not have data.
     * 
     * Need something like getDefaultTables or something that is totally cross
     * dbms.
     */
    private void testExecute(final IDataSet dataSet) throws Exception
    {
        // dataSet = dataSet);
        final ITable[] tablesBefore = DataSetUtils.getTables(AbstractDataSetTest
                .removeExtraTestTables(_connection.createDataSet()));
        getDeleteAllOperation().execute(_connection, dataSet);
        final ITable[] tablesAfter = DataSetUtils.getTables(AbstractDataSetTest
                .removeExtraTestTables(_connection.createDataSet()));

        assertThat(tablesBefore).as("table count > 0").hasSizeGreaterThan(0);
        assertThat(tablesAfter).as("table count").hasSameSizeAs(tablesBefore);
        for (int i = 0; i < tablesBefore.length; i++)
        {
            final ITable table = tablesBefore[i];
            final String name = table.getTableMetaData().getTableName();

            if (!name.toUpperCase().startsWith("EMPTY"))
            {
                assertThat(table.getRowCount()).as(name + " before")
                        .isPositive();
            }
        }

        for (int i = 0; i < tablesAfter.length; i++)
        {
            final ITable table = tablesAfter[i];
            final String name = table.getTableMetaData().getTableName();
            assertThat(table.getRowCount()).as(name + " after " + i).isZero();
        }
    }

    @Test
    void testExecuteWithEmptyDataset() throws Exception
    {
        getDeleteAllOperation().execute(_connection,
                new DefaultDataSet(new ITable[0]));
    }
}
