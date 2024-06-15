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

import org.dbunit.DatabaseEnvironment;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.dataset.filter.ITableFilterSimple;
import org.dbunit.util.QualifiedTableName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
class DatabaseDataSetIT extends AbstractDataSetTest
{
    private IDatabaseConnection _connection;

    ////////////////////////////////////////////////////////////////////////////
    // TestCase class

    @BeforeEach
    protected void setUpConnection() throws Exception
    {
        _connection = DatabaseEnvironment.getInstance().getConnection();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {

        _connection = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSetTest class

    @Override
    protected String convertString(final String str) throws Exception
    {
        return DatabaseEnvironment.getInstance().convertString(str);
    }

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return _connection.createDataSet();
    }

    @Override
    protected String[] getExpectedNames() throws Exception
    {
        return _connection.createDataSet().getTableNames();
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Test methods

    @Test
    void testGetSelectStatement() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final Column[] columns =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),};
        final String expected = "select c1, c2, c3 from schema.table";

        final ITableMetaData metaData =
                new DefaultTableMetaData(tableName, columns);
        final String sql =
                DatabaseDataSet.getSelectStatement(schemaName, metaData, null);
        assertThat(sql).as("select statement").isEqualTo(expected);
    }

    @Test
    void testGetSelectStatementWithEscapedNames() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final Column[] columns =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),};
        final String expected = "select 'c1', 'c2', 'c3' from 'schema'.'table'";

        final ITableMetaData metaData =
                new DefaultTableMetaData(tableName, columns);
        final String sql =
                DatabaseDataSet.getSelectStatement(schemaName, metaData, "'?'");
        assertThat(sql).as("select statement").isEqualTo(expected);
    }

    @Test
    void testGetSelectStatementWithEscapedNamesAndOrderBy() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final Column[] columns =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),};
        final String expected =
                "select 'c1', 'c2', 'c3' from 'schema'.'table' order by 'c1', 'c2'";

        final String[] primaryKeys = {"c1", "c2"};

        final ITableMetaData metaData =
                new DefaultTableMetaData(tableName, columns, primaryKeys);
        final String sql =
                DatabaseDataSet.getSelectStatement(schemaName, metaData, "'?'");
        assertThat(sql).as("select statement").isEqualTo(expected);
    }

    @Test
    void testGetSelectStatementWithPrimaryKeys() throws Exception
    {
        final String schemaName = "schema";
        final String tableName = "table";
        final Column[] columns =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),};
        final String expected =
                "select c1, c2, c3 from schema.table order by c1, c2, c3";

        final ITableMetaData metaData =
                new DefaultTableMetaData(tableName, columns, columns);
        final String sql =
                DatabaseDataSet.getSelectStatement(schemaName, metaData, null);
        assertThat(sql).as("select statement").isEqualTo(expected);
    }

    @Test
    void testGetQualifiedTableNames() throws Exception
    {
        final String[] expectedNames = getExpectedNames();

        final IDatabaseConnection connection = new DatabaseConnection(
                _connection.getConnection(), _connection.getSchema());
        connection.getConfig()
                .setFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, true);

        final IDataSet dataSet = connection.createDataSet();
        final String[] actualNames = dataSet.getTableNames();

        assertThat(actualNames).as("name count").hasSize(expectedNames.length);
        for (int i = 0; i < actualNames.length; i++)
        {
            final String expected = new QualifiedTableName(expectedNames[i],
                    _connection.getSchema()).getQualifiedName();
            final String actual = actualNames[i];
            assertThat(actual).as("name").isEqualTo(expected);
        }
    }

    @Test
    void testGetColumnsAndQualifiedNamesEnabled() throws Exception
    {
        final String tableName =
                new QualifiedTableName("TEST_TABLE", _connection.getSchema())
                        .getQualifiedName();
        final String[] expected = {"COLUMN0", "COLUMN1", "COLUMN2", "COLUMN3"};

        final IDatabaseConnection connection = new DatabaseConnection(
                _connection.getConnection(), _connection.getSchema());
        connection.getConfig()
                .setFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, true);

        final ITableMetaData metaData =
                connection.createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getColumns();
        assertThat(columns).as("column count").hasSize(expected.length);
        for (int i = 0; i < columns.length; i++)
        {
            assertThat(columns[i].getColumnName()).as("column name")
                    .isEqualTo(convertString(expected[i]));
        }
    }

    @Test
    void testGetPrimaryKeysAndQualifiedNamesEnabled() throws Exception
    {
        final String tableName =
                new QualifiedTableName("PK_TABLE", _connection.getSchema())
                        .getQualifiedName();
        final String[] expected = {"PK0", "PK1", "PK2"};

        final IDatabaseConnection connection = new DatabaseConnection(
                _connection.getConnection(), _connection.getSchema());
        connection.getConfig()
                .setFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, true);

        final ITableMetaData metaData =
                connection.createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getPrimaryKeys();

        assertThat(columns).as("column count").hasSize(expected.length);
        for (int i = 0; i < columns.length; i++)
        {
            assertThat(columns[i].getColumnName()).as("column name")
                    .isEqualTo(convertString(expected[i]));
        }
    }

    @Test
    void testGetPrimaryKeysWithColumnFilters() throws Exception
    {

        // TODO (felipeal): I don't know if PK_TABLE is a standard JDBC name or
        // if
        // it's HSQLDB specific. Anyway, now that HSQLDB's schema is set on
        // property,
        // we cannot add it as prefix here....
        final String tableName = "PK_TABLE";
        // String tableName = DataSetUtils.getQualifiedName(
        // _connection.getSchema(), "PK_TABLE");

        final String[] expected = {"PK0", "PK2"};

        final DefaultColumnFilter filter = new DefaultColumnFilter();
        filter.includeColumn("PK0");
        filter.includeColumn("PK2");

        final IDatabaseConnection connection = new DatabaseConnection(
                _connection.getConnection(), _connection.getSchema());
        connection.getConfig().setProperty(
                DatabaseConfig.PROPERTY_PRIMARY_KEY_FILTER, filter);

        final ITableMetaData metaData =
                connection.createDataSet().getTableMetaData(tableName);
        final Column[] columns = metaData.getPrimaryKeys();

        assertThat(columns).as("column count").hasSize(expected.length);
        for (int i = 0; i < columns.length; i++)
        {
            assertThat(columns[i].getColumnName()).as("column name")
                    .isEqualTo(convertString(expected[i]));
        }
    }

    // public void testGetTableNamesAndCaseSensitive() throws Exception
    // {
    // DatabaseMetaData metaData = _connection.getConnection().getMetaData();
    // metaData.
    // }

    @Override
    public void testCreateDuplicateDataSet() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Override
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        // Cannot test! Unsupported feature.
    }

    @Test
    void testGetTableThatIsFiltered() throws Exception
    {
        final String existingTableToFilter = convertString("TEST_TABLE");
        final ITableFilterSimple tableFilter = new ITableFilterSimple()
        {
            @Override
            public boolean accept(final String tableName)
                    throws DataSetException
            {
                if (tableName.equals(existingTableToFilter))
                    return false;
                return true;
            }
        };
        final IDataSet dataSet =
                new DatabaseDataSet(_connection, false, tableFilter);

        final NoSuchTableException expected = assertThrows(
                NoSuchTableException.class,
                () -> dataSet.getTable(existingTableToFilter),
                "Should not be able to retrieve table from dataset that has not been loaded - expected an exception");
        assertThat(expected).hasMessage(existingTableToFilter);
    }

}
