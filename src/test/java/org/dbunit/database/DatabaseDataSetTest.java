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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.DatabaseMetaData;
import java.sql.Statement;

import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchTableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DatabaseDataSet}, using a real in-memory H2 database
 * so {@link IMetadataHandler} can be spied on for deterministic call
 * counting instead of hand-mocking the JDBC metadata result sets.
 */
class DatabaseDataSetTest
{
    private static final String TABLE_NAME = "TEST_TABLE";
    private static final String SCHEMA_NAME = "PUBLIC";

    private IDatabaseConnection connection;
    private IMetadataHandler metadataHandlerSpy;

    @BeforeEach
    void setUp() throws Exception
    {
        connection = InMemoryDatabaseConnection.create(SCHEMA_NAME);

        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute(
                "CREATE TABLE " + TABLE_NAME + " (ID INTEGER PRIMARY KEY)");
        stmt.close();

        final IMetadataHandler realHandler = (IMetadataHandler) connection
                .getConfig().getProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER);
        metadataHandlerSpy = spy(realHandler);
        connection.getConfig().setProperty(
                DatabaseConfig.PROPERTY_METADATA_HANDLER, metadataHandlerSpy);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (connection != null)
        {
            connection.close();
        }
    }

    @Test
    void testGetTableMetaData_afterGetTableNames_doesNotRevalidateTableExistence()
            throws Exception
    {
        final DatabaseDataSet dataSet = new DatabaseDataSet(connection, true);

        dataSet.getTableNames();
        final ITableMetaData metaData = dataSet.getTableMetaData(TABLE_NAME);

        assertThat(metaData.getTableName()).as("table name.")
                .isEqualTo(TABLE_NAME);
        verify(metadataHandlerSpy, times(1)).getTables(
                any(DatabaseMetaData.class), anyString(), any());
        verify(metadataHandlerSpy, never()).tableExists(
                any(DatabaseMetaData.class), anyString(), anyString());
    }

    @Test
    void testGetTableMetaData_withUnknownTable_throwsNoSuchTableException()
            throws Exception
    {
        final DatabaseDataSet dataSet = new DatabaseDataSet(connection, true);

        assertThatExceptionOfType(NoSuchTableException.class)
                .as("An unknown table must still be rejected by the"
                        + " containsTable() check in getTableMetaData(),"
                        + " even though DatabaseTableMetaData no longer"
                        + " re-validates existence itself.")
                .isThrownBy(() -> dataSet.getTableMetaData("UNKNOWN_TABLE"));
    }
}
