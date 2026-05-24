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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeleteAllOperation} using mock objects.
 *
 * @author Manuel Laflamme
 * @author Eric Pugh
 * @version $Revision$
 * @since Feb 18, 2002
 */
class DeleteAllOperationTest
{
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
}
