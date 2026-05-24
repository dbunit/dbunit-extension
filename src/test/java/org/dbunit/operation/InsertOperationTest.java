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
import static org.junit.jupiter.api.Assertions.fail;

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
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InsertOperation} using mock objects.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
class InsertOperationTest
{
    @Test
    void testMockExecute() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                "insert into schema.table (c1, c2, c3) values ('toto', 1234, 'false')",
                "insert into schema.table (c1, c2, c3) values ('qwerty', 123.45, 'true')",};

        // setup table
        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.BOOLEAN),};
        final DefaultTable table = new DefaultTable(tableName, columns);
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
        new InsertOperation().execute(connection, dataSet);

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
        final DefaultTable table = new DefaultTable(tableName, columns);
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
        final InsertOperation io = new InsertOperation();
        assertThrows(IllegalArgumentException.class,
                () -> io.execute(connection, dataSet),
                "Update should not succedd");
        // ignore

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
                String.format(
                        "insert into %s.%s (c3, c4) values ('not-empty', 1)",
                        schemaName, tableName),
                String.format("insert into %s.%s (c3, c4) values (NULL, 2)",
                        schemaName, tableName)};

        final Column[] columns =
                new Column[] {new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final DefaultTable table = new DefaultTable(tableName, columns);
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
        new InsertOperation().execute(connection, dataSet);

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
                String.format(
                        "insert into %s.%s (c3, c4) values ('not-empty', 1)",
                        schemaName, tableName),
                String.format("insert into %s.%s (c3, c4) values (NULL, 2)",
                        schemaName, tableName),
                String.format("insert into %s.%s (c3, c4) values ('', 3)",
                        schemaName, tableName),};

        final Column[] columns =
                new Column[] {new Column("c3", DataType.VARCHAR),
                        new Column("c4", DataType.NUMERIC),};
        final DefaultTable table = new DefaultTable(tableName, columns);
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
        new InsertOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteUnknownColumn() throws Exception
    {
        final String tableName = "table";

        // setup table
        final Column[] columns =
                new Column[] {new Column("column", DataType.VARCHAR),
                        new Column("unknown", DataType.VARCHAR),};
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow();
        table.setValue(0, columns[0].getColumnName(), null);
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
            new InsertOperation().execute(connection, insertDataset);
            fail("Should not be here!");
        } catch (final NoSuchColumnException e)
        {
        }

        statement.verify();
        factory.verify();
        connection.verify();
    }

    @Test
    void testExecuteIgnoreNone() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                "insert into schema.table (c1, c2, c3) values ('toto', 1234, 'false')",
                "insert into schema.table (c2, c3) values (123.45, 'true')",
                "insert into schema.table (c1, c2, c3) values ('qwerty1', 1, 'true')",
                "insert into schema.table (c1, c2, c3) values ('qwerty2', 2, 'false')",
                "insert into schema.table (c3) values ('false')",};

        // setup table
        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.BOOLEAN),};
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow(new Object[] {"toto", "1234", Boolean.FALSE});
        table.addRow(
                new Object[] {ITable.NO_VALUE, new Double("123.45"), "true"});
        table.addRow(new Object[] {"qwerty1", "1", Boolean.TRUE});
        table.addRow(new Object[] {"qwerty2", "2", Boolean.FALSE});
        table.addRow(
                new Object[] {ITable.NO_VALUE, ITable.NO_VALUE, Boolean.FALSE});
        final IDataSet dataSet = new DefaultDataSet(table);

        // setup mock objects
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addExpectedBatchStrings(expected);
        statement.setExpectedExecuteBatchCalls(4);
        statement.setExpectedClearBatchCalls(4);
        statement.setExpectedCloseCalls(4);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreatePreparedStatementCalls(4);
        factory.setupStatement(statement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupDataSet(dataSet);
        connection.setupSchema(schemaName);
        connection.setupStatementFactory(factory);
        connection.setExpectedCloseCalls(0);

        // execute operation
        new InsertOperation().execute(connection, dataSet);

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
                "insert into 'schema'.'table' ('c1', 'c2', 'c3') values ('toto', 1234, 'false')",
                "insert into 'schema'.'table' ('c1', 'c2', 'c3') values ('qwerty', 123.45, 'true')",};

        // setup table
        final Column[] columns =
                new Column[] {new Column("c1", DataType.VARCHAR),
                        new Column("c2", DataType.NUMERIC),
                        new Column("c3", DataType.BOOLEAN),};
        final DefaultTable table = new DefaultTable(tableName, columns);
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
                .setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "'?'");
        new InsertOperation().execute(connection, dataSet);

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
        new InsertOperation().execute(connection, dataSet);

        factory.verify();
        connection.verify();
    }

    @Test
    void testDefaultValues() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final String[] expected = {
                "insert into schema.table (c1, c3, c4) values (NULL, NULL, NULL)"};

        // setup table
        final Column[] columns = new Column[] {
                new Column("c1", DataType.NUMERIC, Column.NO_NULLS), // Disallow
                                                                     // null, no
                                                                     // default
                new Column("c2", DataType.NUMERIC, DataType.NUMERIC.toString(),
                        Column.NO_NULLS, "2"), // Disallow null, default
                new Column("c3", DataType.NUMERIC, Column.NULLABLE), // Allow
                                                                     // null, no
                                                                     // default
                new Column("c4", DataType.NUMERIC, DataType.NUMERIC.toString(),
                        Column.NULLABLE, "4"), // Allow null, default
        };
        final DefaultTable table = new DefaultTable(tableName, columns);
        table.addRow(new Object[] {null, null, null, null});
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
        new InsertOperation().execute(connection, dataSet);

        statement.verify();
        factory.verify();
        connection.verify();
    }
}
