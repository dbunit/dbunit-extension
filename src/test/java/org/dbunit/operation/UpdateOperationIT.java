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

import java.io.FileReader;
import java.io.Reader;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.Assertion;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.TestFeature;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.database.statement.MockBatchStatement;
import org.dbunit.database.statement.MockStatementFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ForwardOnlyDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.dbunit.dataset.NoPrimaryKeyException;
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
class UpdateOperationIT extends AbstractDatabaseIT
{

    ////////////////////////////////////////////////////////////////////////////
    //

    @Override
    protected IDataSet getDataSet() throws Exception
    {
        IDataSet dataSet = super.getDataSet();

        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.BLOB))
        {
            dataSet = new CompositeDataSet(
                    new FlatXmlDataSetBuilder()
                            .build(TestUtils.getFile("xml/blobInsertTest.xml")),
                    dataSet);
        }

        if (environment.support(TestFeature.CLOB))
        {
            dataSet = new CompositeDataSet(
                    new FlatXmlDataSetBuilder()
                            .build(TestUtils.getFile("xml/clobInsertTest.xml")),
                    dataSet);
        }

        if (environment.support(TestFeature.SDO_GEOMETRY))
        {
            dataSet = new CompositeDataSet(
                    new FlatXmlDataSetBuilder().build(
                            TestUtils.getFile("xml/sdoGeometryInsertTest.xml")),
                    dataSet);
        }

        if (environment.support(TestFeature.XML_TYPE))
        {
            dataSet = new CompositeDataSet(
                    new FlatXmlDataSetBuilder().build(
                            TestUtils.getFile("xml/xmlTypeInsertTest.xml")),
                    dataSet);
        }

        return dataSet;
    }

    ////////////////////////////////////////////////////////////////////////////
    //

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

    @Test
    void testUpdateClob() throws Exception
    {
        // execute this test only if the target database support CLOB
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.CLOB))
        {
            final String tableName = "CLOB_TABLE";

            {
                final IDataSet beforeDataSet = new FlatXmlDataSetBuilder()
                        .build(TestUtils.getFile("xml/clobInsertTest.xml"));

                final ITable tableBefore =
                        _connection.createDataSet().getTable(tableName);
                assertThat(_connection.getRowCount(tableName))
                        .as("count before").isEqualTo(3);
                Assertion.assertEquals(beforeDataSet.getTable(tableName),
                        tableBefore);
            }

            final IDataSet afterDataSet = new FlatXmlDataSetBuilder()
                    .build(TestUtils.getFile("xml/clobUpdateTest.xml"));
            DatabaseOperation.REFRESH.execute(_connection, afterDataSet);

            {
                final ITable tableAfter =
                        _connection.createDataSet().getTable(tableName);
                assertThat(tableAfter.getRowCount()).as("count after")
                        .isEqualTo(4);
                Assertion.assertEquals(afterDataSet.getTable(tableName),
                        tableAfter);
            }
        }
    }

    @Test
    void testUpdateBlob() throws Exception
    {
        // execute this test only if the target database support BLOB
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.BLOB))
        {
            final String tableName = "BLOB_TABLE";

            {
                final IDataSet beforeDataSet = new FlatXmlDataSetBuilder()
                        .build(TestUtils.getFile("xml/blobInsertTest.xml"));

                final ITable tableBefore =
                        _connection.createDataSet().getTable(tableName);
                assertThat(_connection.getRowCount(tableName))
                        .as("count before").isEqualTo(3);
                Assertion.assertEquals(beforeDataSet.getTable(tableName),
                        tableBefore);

                // System.out.println("****** BEFORE *******");
                // FlatXmlDataSet.write(_connection.createDataSet(),
                // System.out);
            }

            final IDataSet afterDataSet = new FlatXmlDataSetBuilder()
                    .build(TestUtils.getFile("xml/blobUpdateTest.xml"));
            DatabaseOperation.REFRESH.execute(_connection, afterDataSet);

            {
                final ITable tableAfter =
                        _connection.createDataSet().getTable(tableName);
                assertThat(tableAfter.getRowCount()).as("count after")
                        .isEqualTo(4);
                Assertion.assertEquals(afterDataSet.getTable(tableName),
                        tableAfter);

                // System.out.println("****** AFTER *******");
                // FlatXmlDataSet.write(_connection.createDataSet(),
                // System.out);
            }
        }
    }

    @Test
    void testUpdateSdoGeometry() throws Exception
    {
        // execute this test only if the target database supports SDO_GEOMETRY
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.SDO_GEOMETRY))
        {
            final String tableName = "SDO_GEOMETRY_TABLE";

            {
                final IDataSet beforeDataSet =
                        new FlatXmlDataSetBuilder().build(TestUtils
                                .getFile("xml/sdoGeometryInsertTest.xml"));

                final ITable tableBefore =
                        _connection.createDataSet().getTable(tableName);
                assertThat(_connection.getRowCount(tableName))
                        .as("count before").isEqualTo(1);
                Assertion.assertEquals(beforeDataSet.getTable(tableName),
                        tableBefore);
            }

            final IDataSet afterDataSet = new FlatXmlDataSetBuilder()
                    .build(TestUtils.getFile("xml/sdoGeometryUpdateTest.xml"));
            DatabaseOperation.REFRESH.execute(_connection, afterDataSet);

            {
                final ITable tableAfter =
                        _connection.createDataSet().getTable(tableName);
                assertThat(tableAfter.getRowCount()).as("count after")
                        .isEqualTo(8);
                Assertion.assertEquals(afterDataSet.getTable(tableName),
                        tableAfter);
            }
        }
    }

    @Test
    void testUpdateXmlType() throws Exception
    {
        // execute this test only if the target database support XML_TYPE
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.XML_TYPE))
        {
            final String tableName = "XML_TYPE_TABLE";

            {
                final IDataSet beforeDataSet = new FlatXmlDataSetBuilder()
                        .build(TestUtils.getFile("xml/xmlTypeInsertTest.xml"));

                final ITable tableBefore =
                        _connection.createDataSet().getTable(tableName);
                assertThat(_connection.getRowCount(tableName))
                        .as("count before").isEqualTo(3);
                Assertion.assertEquals(beforeDataSet.getTable(tableName),
                        tableBefore);
            }

            final IDataSet afterDataSet = new FlatXmlDataSetBuilder()
                    .build(TestUtils.getFile("xml/xmlTypeUpdateTest.xml"));
            DatabaseOperation.REFRESH.execute(_connection, afterDataSet);

            {
                final ITable tableAfter =
                        _connection.createDataSet().getTable(tableName);
                assertThat(tableAfter.getRowCount()).as("count after")
                        .isEqualTo(4);
                Assertion.assertEquals(afterDataSet.getTable(tableName),
                        tableAfter);
            }
        }
    }

    @Test
    void testExecute() throws Exception
    {
        final Reader in = new FileReader(
                TestUtils.getFile("xml/updateOperationTest.xml"));
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(dataSet);

    }

    @Test
    void testExecuteCaseInsensitive() throws Exception
    {
        final Reader in = new FileReader(
                TestUtils.getFile("xml/updateOperationTest.xml"));
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(new LowerCaseDataSet(dataSet));
    }

    @Test
    void testExecuteForwardOnly() throws Exception
    {
        final Reader in = new FileReader(
                TestUtils.getFile("xml/updateOperationTest.xml"));
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(new ForwardOnlyDataSet(dataSet));
    }

    @Test
    void testExecuteAndNoPrimaryKeys() throws Exception
    {
        final String tableName = "TEST_TABLE";

        final Reader reader =
                TestUtils.getFileReader("xml/updateOperationNoPKTest.xml");
        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(reader);

        // verify table before
        assertThat(_connection.getRowCount(tableName)).as("row count before")
                .isEqualTo(6);

        try
        {
            DatabaseOperation.REFRESH.execute(_connection, dataSet);
            fail("Should not be here!");
        } catch (final NoPrimaryKeyException e)
        {

        }

        // verify table after
        assertThat(_connection.getRowCount(tableName)).as("row count before")
                .isEqualTo(6);
    }

    private void testExecute(final IDataSet dataSet) throws Exception
    {
        final String tableName = "PK_TABLE";
        final String[] columnNames =
                {"PK0", "PK1", "PK2", "NORMAL0", "NORMAL1"};
        final int modifiedRow = 1;

        // verify table before
        final ITable tableBefore =
                createOrderedTable(tableName, columnNames[0]);
        assertThat(tableBefore.getRowCount()).as("row count before")
                .isEqualTo(3);

        DatabaseOperation.UPDATE.execute(_connection, dataSet);

        final ITable tableAfter = createOrderedTable(tableName, columnNames[0]);
        assertThat(tableAfter.getRowCount()).as("row count after").isEqualTo(3);
        for (int i = 0; i < tableAfter.getRowCount(); i++)
        {
            // verify modified row
            if (i == modifiedRow)
            {
                assertThat(tableAfter.getValue(i, "PK0")).as("PK0")
                        .hasToString("1");
                assertThat(tableAfter.getValue(i, "PK1")).as("PK1")
                        .hasToString("1");
                assertThat(tableAfter.getValue(i, "PK2")).as("PK2")
                        .hasToString("1");
                assertThat(tableAfter.getValue(i, "NORMAL0")).as("NORMAL0")
                        .hasToString("toto");
                assertThat(tableAfter.getValue(i, "NORMAL1")).as("NORMAL1")
                        .hasToString("qwerty");
            }
            // all other row must be equals than before update
            else
            {
                for (int j = 0; j < columnNames.length; j++)
                {
                    final String name = columnNames[j];
                    final Object valueAfter = tableAfter.getValue(i, name);
                    final Object valueBefore = tableBefore.getValue(i, name);
                    assertThat(valueAfter).as("c=" + name + ",r=" + j)
                            .isEqualTo(valueBefore);
                }
            }
        }
    }

}
