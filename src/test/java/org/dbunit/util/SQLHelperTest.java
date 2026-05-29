/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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

package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.AbstractHSQLTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Nov 5, 2005
 */
@ExtendWith(MockitoExtension.class)
class SQLHelperTest extends AbstractHSQLTestCase
{

    @Mock
    private Connection mockConnection;

    @Mock
    private DatabaseMetaData mockDatabaseMetaData;

    @Mock
    private ResultSet mockSchemasResultSet;

    @Mock
    private ResultSet mockCatalogsResultSet;

    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUpConnectionWithFile("hypersonic_dataset.sql");
    }

    @Test
    void testGetPrimaryKeyColumn_withValidTable_returnsPrimaryKeyColumnName() throws SQLException
    {
        final String[] tables = {"A", "B", "C", "D", "E", "F", "G", "H"};
        final Connection conn = getConnection().getConnection();
        assertThat(conn).as("didn't get a connection").isNotNull();
        for (int i = 0; i < tables.length; i++)
        {
            final String table = tables[i];
            final String expectedPK = "PK" + table;
            final String actualPK = SQLHelper.getPrimaryKeyColumn(conn, table);
            assertThat(actualPK).isNotNull();
            assertThat(actualPK).as(
                    "primary key column for table " + table + " does not match")
                    .isEqualTo(expectedPK);
        }
    }

    @Test
    void testSchemaExists_withNullTableSchemRow_returnsFalse() throws SQLException
    {
        when(mockConnection.getMetaData()).thenReturn(mockDatabaseMetaData);
        when(mockDatabaseMetaData.getSchemas()).thenReturn(mockSchemasResultSet);
        when(mockSchemasResultSet.next()).thenReturn(true, false);
        when(mockSchemasResultSet.getString("TABLE_SCHEM")).thenReturn(null);
        when(mockDatabaseMetaData.getCatalogs()).thenReturn(mockCatalogsResultSet);
        when(mockCatalogsResultSet.next()).thenReturn(false);

        boolean result = SQLHelper.schemaExists(mockConnection, "DBUNIT");
        assertThat(result).as("Schema should not be found when TABLE_SCHEM is null.").isFalse();
    }

    @Test
    void testSchemaExists_withNullTableCatRow_returnsFalse() throws SQLException
    {
        when(mockConnection.getMetaData()).thenReturn(mockDatabaseMetaData);
        when(mockDatabaseMetaData.getSchemas()).thenReturn(mockSchemasResultSet);
        when(mockSchemasResultSet.next()).thenReturn(false);
        when(mockDatabaseMetaData.getCatalogs()).thenReturn(mockCatalogsResultSet);
        when(mockCatalogsResultSet.next()).thenReturn(true, false);
        when(mockCatalogsResultSet.getString("TABLE_CAT")).thenReturn(null);

        boolean result = SQLHelper.schemaExists(mockConnection, "DBUNIT");
        assertThat(result).as("Schema should not be found when TABLE_CAT is null.").isFalse();
    }

    @Test
    void testGetDatabaseInfoWithException_withExceptionOnVersion_returnsNotAvailableText() throws Exception
    {
        final String productName = "Some product";
        final String exceptionText =
                "Dummy exception to simulate unimplemented operation exception as occurs "
                        + "in sybase 'getDatabaseMajorVersion()' (com.sybase.jdbc3.utils.UnimplementedOperationException)";
        when(mockDatabaseMetaData.getDatabaseProductName())
                .thenReturn(productName);
        when(mockDatabaseMetaData.getDatabaseMajorVersion())
                .thenThrow(new SQLException(exceptionText));

        final String info = SQLHelper.getDatabaseInfo(mockDatabaseMetaData);
        assertThat(info).isNotNull().contains(productName)
                .contains(SQLHelper.ExceptionWrapper.NOT_AVAILABLE_TEXT);
        verify(mockDatabaseMetaData, times(1)).getDatabaseProductName();
        verify(mockDatabaseMetaData, times(1)).getDatabaseMajorVersion();
    }
}
