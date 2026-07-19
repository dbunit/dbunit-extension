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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.database.QueryDataSet.TableEntry;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QueryTableIterator}.
 *
 * @since 3.2.1
 */
class QueryTableIteratorTest
{
    private static List<TableEntry> singleEntry(final TableEntry entry)
    {
        final List<TableEntry> entries = new ArrayList<>();
        entries.add(entry);
        return entries;
    }

    @Test
    void testGetTableMetaDataThenGetTable_withNoQueryEntry_createsTableOnlyOnce()
            throws Exception
    {
        final ITableMetaData metaData =
                new DefaultTableMetaData("MYTABLE", new org.dbunit.dataset.Column[0]);
        final IResultSetTable table = mock(IResultSetTable.class);
        when(table.getTableMetaData()).thenReturn(metaData);

        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.createTable("MYTABLE")).thenReturn(table);

        final QueryTableIterator iterator = new QueryTableIterator(
                singleEntry(new TableEntry("MYTABLE", null)), connection);
        iterator.next();

        final ITableMetaData actualMetaData = iterator.getTableMetaData();
        final Object actualTable = iterator.getTable();

        assertThat(actualMetaData).as("metadata from getTableMetaData().")
                .isSameAs(metaData);
        assertThat(actualTable).as("table from getTable().").isSameAs(table);
        verify(connection, times(1)).createTable("MYTABLE");
    }

    @Test
    void testGetTableThenGetTableMetaData_withNoQueryEntry_createsTableOnlyOnce()
            throws Exception
    {
        final ITableMetaData metaData =
                new DefaultTableMetaData("MYTABLE", new org.dbunit.dataset.Column[0]);
        final IResultSetTable table = mock(IResultSetTable.class);
        when(table.getTableMetaData()).thenReturn(metaData);

        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.createTable("MYTABLE")).thenReturn(table);

        final QueryTableIterator iterator = new QueryTableIterator(
                singleEntry(new TableEntry("MYTABLE", null)), connection);
        iterator.next();

        final Object actualTable = iterator.getTable();
        final ITableMetaData actualMetaData = iterator.getTableMetaData();

        assertThat(actualTable).as("table from getTable().").isSameAs(table);
        assertThat(actualMetaData).as("metadata from getTableMetaData().")
                .isSameAs(metaData);
        verify(connection, times(1)).createTable("MYTABLE");
    }

    @Test
    void testGetTableMetaDataThenGetTable_withQueryEntry_createsTableOnlyOnce()
            throws Exception
    {
        final String query = "select * from MYTABLE where ID > 1";
        final ITableMetaData metaData =
                new DefaultTableMetaData("MYTABLE", new org.dbunit.dataset.Column[0]);
        final IResultSetTable table = mock(IResultSetTable.class);
        when(table.getTableMetaData()).thenReturn(metaData);

        final IResultSetTableFactory factory = mock(IResultSetTableFactory.class);
        when(factory.createTable(any(String.class), any(String.class), any(IDatabaseConnection.class)))
                .thenReturn(table);

        final DatabaseConfig config = new DatabaseConfig();
        config.setProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY, factory);

        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);

        final QueryTableIterator iterator = new QueryTableIterator(
                singleEntry(new TableEntry("MYTABLE", query)), connection);
        iterator.next();

        final ITableMetaData actualMetaData = iterator.getTableMetaData();
        final Object actualTable = iterator.getTable();

        assertThat(actualMetaData).as("metadata from getTableMetaData().")
                .isSameAs(metaData);
        assertThat(actualTable).as("table from getTable().").isSameAs(table);
        verify(factory, times(1)).createTable("MYTABLE", query, connection);
    }

    @Test
    void testNext_afterGetTable_closesPreviousTableBeforeAdvancing() throws Exception
    {
        final IResultSetTable table1 = mock(IResultSetTable.class);
        final IResultSetTable table2 = mock(IResultSetTable.class);

        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.createTable("T1")).thenReturn(table1);
        when(connection.createTable("T2")).thenReturn(table2);

        final List<TableEntry> entries = new ArrayList<>();
        entries.add(new TableEntry("T1", null));
        entries.add(new TableEntry("T2", null));

        final QueryTableIterator iterator = new QueryTableIterator(entries, connection);
        iterator.next();
        iterator.getTable();
        iterator.next();

        verify(table1, times(1)).close();
    }
}
