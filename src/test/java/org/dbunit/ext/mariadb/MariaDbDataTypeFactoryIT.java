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
package org.dbunit.ext.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.sql.Statement;
import java.util.Objects;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.ext.mysql.MySqlMetadataHandler;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xml.sax.InputSource;

/**
 * Proves {@link MariaDbDataTypeFactory} handles MariaDB's native
 * {@code UUID}/{@code INET4}/{@code INET6} column types end-to-end through a
 * live connection, not just in isolated {@code createDataType()} unit tests.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "mariadb")
class MariaDbDataTypeFactoryIT
{
    private IDatabaseConnection _connection;
    private final String testTable = "MARIADB_TYPE_DIVERGENCE_TABLE";
    // @formatter:off
    private static final String xmlData = "<?xml version=\"1.0\"?>" +
            "<dataset>" +
            "<MARIADB_TYPE_DIVERGENCE_TABLE ID=\"1\" "
                + "UUID_COL=\"08004327-3f6c-4335-9738-0b2bf885cc43\" "
                + "INET4_COL=\"192.168.1.1\" "
                + "INET6_COL=\"::1\" />" +
            "</dataset>";
    // @formatter:on

    @BeforeEach
    protected void setUp() throws Exception
    {
        _connection = DatabaseEnvironment.getInstance().getConnection();
        final Statement stat = _connection.getConnection().createStatement();
        stat.execute("DROP TABLE IF EXISTS " + testTable + ";");
        stat.execute("CREATE TABLE " + testTable
                + "(ID INT NOT NULL PRIMARY KEY, "
                + "UUID_COL UUID, INET4_COL INET4, INET6_COL INET6);");
        stat.close();
        _connection.close();
        _connection = DatabaseEnvironment.getInstance().getConnection();

        final DatabaseConfig config = _connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new MariaDbDataTypeFactory());
        config.setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER,
                new MySqlMetadataHandler());
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        if (!Objects.isNull(_connection))
        {
            final Statement stat =
                    _connection.getConnection().createStatement();
            stat.execute("DROP TABLE IF EXISTS " + testTable + ";");
            _connection.close();

            _connection = null;
        }
    }

    @Test
    void testMariaDbNativeTypes_withUuidInet4Inet6Columns_roundTripThroughDatabase()
            throws Exception
    {
        assertThat(_connection).as("didn't get a connection.").isNotNull();

        final IDataSet dataSet = new FlatXmlDataSetBuilder()
                .build(new InputSource(new StringReader(xmlData)));

        IDataSet ids = _connection.createDataSet();
        final ITableMetaData tableMetaData = ids.getTableMetaData(testTable);
        for (final Column column : tableMetaData.getColumns())
        {
            if ("UUID_COL".equalsIgnoreCase(column.getColumnName())
                    || "INET4_COL".equalsIgnoreCase(column.getColumnName())
                    || "INET6_COL".equalsIgnoreCase(column.getColumnName()))
            {
                // MariaDB reports these as SQL type OTHER with a native type
                // name; MariaDbDataTypeFactory maps that to VARCHAR.
                assertThat(column.getSqlTypeName())
                        .as("sql type name of " + column.getColumnName() + ".")
                        .isNotNull();
                assertThat(column.getDataType())
                        .as("data type of " + column.getColumnName() + ".")
                        .isSameAs(DataType.VARCHAR);
            }
        }

        DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);

        ids = _connection.createDataSet();
        final ITable actualTable = ids.getTable(testTable);
        assertThat(actualTable.getValue(0, "UUID_COL")).as("uuid column value.")
                .isEqualTo("08004327-3f6c-4335-9738-0b2bf885cc43");
        assertThat(actualTable.getValue(0, "INET4_COL")).as("inet4 column value.")
                .isEqualTo("192.168.1.1");
        assertThat(actualTable.getValue(0, "INET6_COL")).as("inet6 column value.")
                .isEqualTo("::1");
    }
}
