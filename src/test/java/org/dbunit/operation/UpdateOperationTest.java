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

import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UpdateOperation} using mock objects.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
class UpdateOperationTest
{
    @Test
    void testMockExecute() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                "update schema.table set c2 = 1234, c3 = 'false' where c4 = 0 and c1 = 'toto'",
                "update schema.table set c2 = 123.45, c3 = NULL where c4 = 0 and c1 = 'qwerty'",};

        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final String[] primaryKeys = {"c4", "c1"};
        final DefaultTable table = new DefaultTable(
                new DefaultTableMetaData(tableName, columns, primaryKeys));
        table.addRow(new Object[] {"toto", "1234", "false", "0"});
        table.addRow(new Object[] {"qwerty", new Double("123.45"), null, "0"});
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
        new UpdateOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithBlanksDisabledAndEmptyString() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";

        final Column[] columns =
                new Column[] {new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final String[] primaryKeys = {"c4"};
        final DefaultTable table = new DefaultTable(
                new DefaultTableMetaData(tableName, columns, primaryKeys));
        table.addRow(new Object[] {"", "1"});
        final IDataSet dataSet = new DefaultDataSet(table);

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.setExpectedExecuteBatchCalls(0);
        statement.setExpectedClearBatchCalls(0);
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
                .setFeature(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, false);
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateOperation().execute(connection, dataSet),
                "Update should not succedd");
        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithBlanksDisabledAndNonEmptyStrings() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                String.format("update %s.%s set c3 = 'not-empty' where c4 = 1",
                        schemaName, tableName),
                String.format("update %s.%s set c3 = NULL where c4 = 2",
                        schemaName, tableName)};

        final Column[] columns =
                new Column[] {new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final String[] primaryKeys = {"c4"};
        final DefaultTable table = new DefaultTable(
                new DefaultTableMetaData(tableName, columns, primaryKeys));
        table.addRow(new Object[] {"not-empty", "1"});
        table.addRow(new Object[] {null, "2"});
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
                .setFeature(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, false);
        new UpdateOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithBlanksAllowed() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                String.format("update %s.%s set c3 = 'not-empty' where c4 = 1",
                        schemaName, tableName),
                String.format("update %s.%s set c3 = NULL where c4 = 2",
                        schemaName, tableName),
                String.format("update %s.%s set c3 = '' where c4 = 3",
                        schemaName, tableName),};

        final Column[] columns =
                new Column[] {new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final String[] primaryKeys = {"c4"};
        final DefaultTable table = new DefaultTable(
                new DefaultTableMetaData(tableName, columns, primaryKeys));
        table.addRow(new Object[] {"not-empty", "1"});
        table.addRow(new Object[] {null, "2"});
        table.addRow(new Object[] {"", "3"});
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
                .setFeature(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, true);
        new UpdateOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteWithEscapedName() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                "update [schema].[table] set [c2] = 1234, [c3] = 'false' where [c4] = 0 and [c1] = 'toto'",
                "update [schema].[table] set [c2] = 123.45, [c3] = NULL where [c4] = 0 and [c1] = 'qwerty'",};

        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final String[] primaryKeys = {"c4", "c1"};
        final DefaultTable table = new DefaultTable(
                new DefaultTableMetaData(tableName, columns, primaryKeys));
        table.addRow(new Object[] {"toto", "1234", "false", "0"});
        table.addRow(new Object[] {"qwerty", new Double("123.45"), null, "0"});
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
        new UpdateOperation().execute(connection, dataSet);

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
        new UpdateOperation().execute(connection, dataSet);

        factory.verify();
        connection.verify();
    }
}
