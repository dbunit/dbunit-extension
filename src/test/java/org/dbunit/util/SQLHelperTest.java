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
    private DatabaseMetaData mockDatabaseMetaData;

    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUpConnectionWithFile("hypersonic_dataset.sql");
    }

    @Test
    void testGetPrimaryKeyColumn() throws SQLException
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
    void testGetDatabaseInfoWithException() throws Exception
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
