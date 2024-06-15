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

package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.DdlExecutor;
import org.dbunit.HypersonicEnvironment;
import org.dbunit.TestFeature;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 14, 2002
 */
class DatabaseTableMetaDataIT extends AbstractDatabaseIT
{

    public static final String TEST_TABLE = "TEST_TABLE";

    protected IDataSet createDataSet() throws Exception
    {
        return _connection.createDataSet();
    }

    @Override
    protected String convertString(final String str) throws Exception
    {
        return DatabaseEnvironment.getInstance().convertString(str);
    }

    @Test
    void testGetPrimaryKeys() throws Exception
    {
        final String tableName = "PK_TABLE";
        // String[] expected = {"PK0"};
        final String[] expected = {"PK0", "PK1", "PK2"};

        final ITableMetaData metaData =
                createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getPrimaryKeys();
        assertThat(columns).as("pl count").hasSize(expected.length);

        for (int i = 0; i < columns.length; i++)
        {
            final Column column = columns[i];
            assertThat(column.getColumnName()).as("name")
                    .isEqualTo(convertString(expected[i]));
        }
    }

    @Test
    void testGetNoPrimaryKeys() throws Exception
    {
        final String tableName = TEST_TABLE;

        final ITableMetaData metaData =
                createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getPrimaryKeys();
        assertThat(columns).as("pk count").isEmpty();
    }

    @Test
    void testCreation_UnknownTable() throws Exception
    {
        final String tableName = "UNKNOWN_TABLE";
        final IDatabaseConnection connection = getConnection();
        final String schema = connection.getSchema();
        final NoSuchTableException expected = assertThrows(
                NoSuchTableException.class,
                () -> new DatabaseTableMetaData(tableName, getConnection()),
                "Should not be able to create a DatabaseTableMetaData for an unknown table");

        final String msg =
                "Did not find table '" + convertString("UNKNOWN_TABLE")
                        + "' in schema '" + schema + "'";
        assertThat(expected).hasMessage(msg);
    }

    @Test
    void testGetNoColumns() throws Exception
    {
        // Since the "unknown_table" does not exist it also does not have any
        // columns
        final String tableName = "UNKNOWN_TABLE";
        final boolean validate = false;

        final ITableMetaData metaData =
                new DatabaseTableMetaData(tableName, getConnection(), validate);

        final Column[] columns = metaData.getColumns();
        assertThat(columns).isEmpty();
    }

    @Test
    void testColumnIsNullable() throws Exception
    {
        final String tableName = "PK_TABLE";
        final String[] notNullable = {"PK0", "PK1", "PK2"};
        final String[] nullable = {"NORMAL0", "NORMAL1"};

        final ITableMetaData metaData =
                createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getColumns();

        assertThat(columns).as("column count")
                .hasSize(nullable.length + notNullable.length);

        // not nullable
        for (int i = 0; i < notNullable.length; i++)
        {
            final Column column = Columns.getColumn(notNullable[i], columns);
            assertThat(column.getNullable()).as(notNullable[i])
                    .isEqualTo(Column.NO_NULLS);
        }

        // nullable
        for (int i = 0; i < nullable.length; i++)
        {
            final Column column = Columns.getColumn(nullable[i], columns);
            assertThat(column.getNullable()).as(nullable[i])
                    .isEqualTo(Column.NULLABLE);
        }
    }

    @Test
    void testUnsupportedColumnDataType() throws Exception
    {
        final IDataTypeFactory dataTypeFactory = new DefaultDataTypeFactory()
        {
            @Override
            public DataType createDataType(final int sqlType,
                    final String sqlTypeName, final String tableName,
                    final String columnName) throws DataTypeException
            {
                return DataType.UNKNOWN;
            }
        };
        this._connection.getConfig().setProperty(
                DatabaseConfig.PROPERTY_DATATYPE_FACTORY, dataTypeFactory);

        final String tableName = "EMPTY_MULTITYPE_TABLE";
        final ITableMetaData metaData =
                createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getColumns();
        // No columns recognized -> should not provide any columns here
        assertThat(columns).as("Should be an empty column array").isEmpty();
    }

    @Test
    void testColumnDataType() throws Exception
    {
        final String tableName = "EMPTY_MULTITYPE_TABLE";

        final List<String> expectedNames = new ArrayList<>();
        expectedNames.add("VARCHAR_COL");
        expectedNames.add("NUMERIC_COL");
        expectedNames.add("TIMESTAMP_COL");

        final List<DataType> expectedTypes = new ArrayList<>();
        expectedTypes.add(DataType.VARCHAR);
        expectedTypes.add(DataType.NUMERIC);
        expectedTypes.add(DataType.TIMESTAMP);

        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        if (environment.support(TestFeature.VARBINARY))
        {
            expectedNames.add("VARBINARY_COL");
            expectedTypes.add(DataType.VARBINARY);
        }

        // Check correct setup
        assertThat(expectedNames).as("expected columns")
                .hasSize(expectedTypes.size());

        final ITableMetaData metaData =
                createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getColumns();
        assertThat(columns).as("column count").hasSize(4);

        for (int i = 0; i < expectedNames.size(); i++)
        {
            final Column column = columns[i];
            assertThat(column.getColumnName()).as("name")
                    .isEqualTo(convertString(expectedNames.get(i)));
            if (expectedTypes.get(i).equals(DataType.NUMERIC))
            {
                // 2009-10-10 TODO John Hurst: hack for Oracle, returns
                // java.sql.Types.DECIMAL for this column
                assertThat(column)
                        .as("Expected numeric datatype, got ["
                                + column.getDataType() + "]")
                        .satisfiesAnyOf(
                                dataType -> assertThat(dataType.getDataType())
                                        .isEqualTo(DataType.NUMERIC),
                                dataType -> assertThat(dataType.getDataType())
                                        .isEqualTo(DataType.DECIMAL));

            } else if (expectedTypes.get(i).equals(DataType.TIMESTAMP)
                    && column.getDataType().equals(DataType.DATE))
            {
                // 2009-10-22 TODO John Hurst: hack for Postgresql, returns DATE
                // for TIMESTAMP.
                // Need to move DataType comparison to DatabaseEnvironment.
                assertTrue(true);
            } else if (expectedTypes.get(i).equals(DataType.VARBINARY)
                    && column.getDataType().equals(DataType.VARCHAR))
            {
                // 2009-10-22 TODO John Hurst: hack for Postgresql, returns
                // VARCHAR for VARBINARY.
                // Need to move DataType comparison to DatabaseEnvironment.
                assertTrue(true);
            } else
            {
                assertThat(column.getDataType()).as("datatype")
                        .isEqualTo(expectedTypes.get(i));

            }
        }
    }

    /**
     * Tests whether dbunit works correctly when the local machine has a
     * specific locale set while having case sensitivity=false (so that the
     * "toUpperCase()" is internally invoked on table names)
     * 
     * @throws Exception
     */
    @Test
    void testCaseInsensitiveAndI18n() throws Exception
    {
        // To test bug report #1537894 where the user has a turkish locale set
        // on his box

        // Change the locale for this test
        final Locale oldLocale = Locale.getDefault();
        // Set the locale to turkish where "i".toUpperCase() produces an
        // "\u0131" ("I" with dot above) which is not equal to "I".
        Locale.setDefault(new Locale("tr", "TR"));

        try
        {
            // Use the "EMPTY_MULTITYPE_TABLE" because it has an "I" in the
            // name.
            // Use as input a completely lower-case string so that the internal
            // "toUpperCase()" has effect
            // 2009-11-06 TODO John Hurst: not working in original form with
            // MySQL.
            // Is it because "internal toUpperCase() mentioned above is actually
            // not being called?
            // Investigate further.
            // String tableName = "empty_multitype_table";
            final String tableName = "EMPTY_MULTITYPE_TABLE";

            final IDataSet dataSet = this._connection.createDataSet();
            final ITable table = dataSet.getTable(tableName);
            // Should now find the table, regardless that we gave the tableName
            // in lowerCase
            assertThat(table).as("Table '" + tableName + "' was not found")
                    .isNotNull();
        } finally
        {
            // Reset locale
            Locale.setDefault(oldLocale);
        }
    }

    /**
     * Tests the pattern-like column retrieval from the database. DbUnit should
     * not interpret any table names as regex patterns.
     * 
     * @throws Exception
     */
    @Test
    void testGetColumnsForTablesMatchingSamePattern() throws Exception
    {
        final Connection jdbcConnection =
                HypersonicEnvironment.createJdbcConnection("tempdb");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_dataset_pattern_test.sql"),
                jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(jdbcConnection);

        try
        {
            final String tableName = "PATTERN_LIKE_TABLE_X_";
            final String[] columnNames = {"VARCHAR_COL_XUNDERSCORE"};

            final ITableMetaData metaData =
                    connection.createDataSet().getTableMetaData(tableName);
            final Column[] columns = metaData.getColumns();
            assertThat(columns).as("column count").hasSize(columnNames.length);

            for (int i = 0; i < columnNames.length; i++)
            {
                final Column column =
                        Columns.getColumn(columnNames[i], columns);
                assertThat(column.getColumnName()).as(columnNames[i])
                        .isEqualTo(columnNames[i]);
            }
        } finally
        {
            HypersonicEnvironment.shutdown(jdbcConnection);
            jdbcConnection.close();
            HypersonicEnvironment.deleteFiles("tempdb");
        }
    }

    @Test
    void testCaseSensitive() throws Exception
    {
        final Connection jdbcConnection =
                HypersonicEnvironment.createJdbcConnection("tempdb");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_case_sensitive_test.sql"),
                jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(jdbcConnection);

        try
        {
            final String tableName = "MixedCaseTable";
            final String tableNameWrongCase = "MIXEDCASETABLE";
            final boolean validate = true;
            final boolean caseSensitive = true;

            final ITableMetaData metaData = new DatabaseTableMetaData(tableName,
                    connection, validate, caseSensitive);
            final Column[] columns = metaData.getColumns();
            assertThat(columns).hasSize(1);
            assertThat(columns[0].getColumnName()).isEqualTo("COL1");

            // Now test with same table name but wrong case
            final NoSuchTableException expected =
                    assertThrows(NoSuchTableException.class, () -> {
                        new DatabaseTableMetaData(tableNameWrongCase,
                                connection, validate, caseSensitive);
                    }, "Should not be able to create DatabaseTableMetaData with non-existing table name "
                            + tableNameWrongCase + ". Created ");
            assertThat(expected.getMessage().indexOf(tableNameWrongCase))
                    .isNotNegative();
        } finally
        {
            HypersonicEnvironment.shutdown(jdbcConnection);
            jdbcConnection.close();
            HypersonicEnvironment.deleteFiles("tempdb");
        }
    }

    /**
     * Ensure that the same table name is returned by
     * {@link DatabaseTableMetaData#getTableName()} as the specified by the
     * input parameter.
     * 
     * @throws Exception
     */
    @Test
    void testFullyQualifiedTableName() throws Exception
    {
        final DatabaseEnvironment environment =
                DatabaseEnvironment.getInstance();
        final String schema = environment.getProfile().getSchema();

        assertThat(schema)
                .as("Precondition: db environment 'schema' must not be null")
                .isNotNull();
        // Connection jdbcConn = _connection.getConnection();
        // String schema = SQLHelper.getSchema(jdbcConn);
        final DatabaseTableMetaData metaData = new DatabaseTableMetaData(
                schema + "." + TEST_TABLE, _connection);
        assertThat(metaData.getTableName())
                .isEqualTo(schema + "." + convertString(TEST_TABLE));
    }

    @Test
    void testDbStoresUpperCaseTableNames() throws Exception
    {
        final IDatabaseConnection connection = getConnection();
        final DatabaseMetaData metaData =
                connection.getConnection().getMetaData();
        if (metaData.storesUpperCaseIdentifiers())
        {
            final DatabaseTableMetaData dbTableMetaData =
                    new DatabaseTableMetaData(
                            TEST_TABLE.toLowerCase(Locale.ENGLISH),
                            _connection);
            // Table name should have been "toUpperCase'd"
            assertThat(dbTableMetaData.getTableName())
                    .isEqualTo(TEST_TABLE.toUpperCase(Locale.ENGLISH));
        } else
        {
            // skip the test
            assertTrue(true);
        }
    }

    @Test
    void testDbStoresLowerCaseTableNames() throws Exception
    {
        final IDatabaseConnection connection = getConnection();
        final DatabaseMetaData metaData =
                connection.getConnection().getMetaData();
        if (metaData.storesLowerCaseIdentifiers())
        {
            final DatabaseTableMetaData dbTableMetaData =
                    new DatabaseTableMetaData(
                            TEST_TABLE.toUpperCase(Locale.ENGLISH),
                            _connection);
            // Table name should have been "toUpperCase'd"
            assertThat(dbTableMetaData.getTableName())
                    .isEqualTo(TEST_TABLE.toLowerCase(Locale.ENGLISH));
        } else
        {
            // skip the test
            assertTrue(true);
        }
    }
}
