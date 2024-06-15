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

package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;

import org.dbunit.AbstractHSQLTestCase;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.ext.hsqldb.HsqldbDataTypeFactory;
import org.dbunit.util.CollectionsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Sep 9, 2005
 */
class PrimaryKeyFilteredTableWrapperTest extends AbstractHSQLTestCase
{

    private ITable fTable; // fixture
    private IDataSet fDataSet; // fixture

    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUpConnectionWithFile("hypersonic_dataset.sql");
        final IDatabaseConnection connection = super.getConnection();
        final DatabaseConfig config = connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new HsqldbDataTypeFactory());
        this.fDataSet = connection.createDataSet();
        this.fTable = this.fDataSet.getTable(E);
    }

    @Test
    void testConstructorNullTable() throws DataSetException
    {
        final Set<Object> empty = new HashSet<>();
        final IllegalArgumentException actual =
                assertThrows(IllegalArgumentException.class,
                        () -> new PrimaryKeyFilteredTableWrapper(null, empty),
                        "constructor accepted null argument and returned ");
        assertThat(actual).isNotNull();
    }

    @Test
    void testConstructorNullSet() throws DataSetException
    {
        final IllegalArgumentException actual = assertThrows(
                IllegalArgumentException.class,
                () -> new PrimaryKeyFilteredTableWrapper(this.fTable, null),
                "constructor accepted null argument and returned ");
        assertThat(actual).isNotNull();

    }

    @Test
    void testDenyEverything() throws DataSetException
    {
        final PrimaryKeyFilteredTableWrapper table =
                new PrimaryKeyFilteredTableWrapper(this.fTable,
                        new HashSet<>());
        assertMetaInformationEquals(this.fTable, table);
        assertThat(table.getRowCount()).as("table not empty").isZero();
        assertSecondTableIsEmpty(this.fTable, table);
    }

    @Test
    void testAllowEverything() throws DataSetException
    {
        final Set<Object> allowedPKs = getPKs(this.fTable);
        allowEveryThingTest(allowedPKs);
    }

    @Test
    void testAllowEverythingWithClonedSet() throws DataSetException
    {
        final Set<Object> allowedPKs = getPKs(this.fTable);
        final Set<Object> newSet = new HashSet<>(allowedPKs);
        allowEveryThingTest(newSet);
    }

    @Test
    void testFilterLast() throws DataSetException
    {
        doFilter(new String[] {E1, E2, E3});
    }

    @Test
    void testFilterFirst() throws DataSetException
    {
        doFilter(new String[] {E2, E3, E4});
    }

    @Test
    void testFilterMiddle() throws DataSetException
    {
        doFilter(new String[] {E1, E4});
    }

    private void doFilter(final String[] ids) throws DataSetException
    {
        final Set<Object> allowedIds = CollectionsHelper.objectsToSet(ids);
        final ITable table =
                new PrimaryKeyFilteredTableWrapper(this.fTable, allowedIds);
        assertThat(table.getRowCount()).as("size of table does not match")
                .isEqualTo(ids.length);

        final String pkColumn =
                table.getTableMetaData().getPrimaryKeys()[0].getColumnName();
        final int size = table.getRowCount();
        for (int i = 0; i < size; i++)
        {
            final Object pk = table.getValue(i, pkColumn);
            assertThat(pk).as("id didn't match at index " + i)
                    .isEqualTo(ids[i]);
        }
    }

    private void allowEveryThingTest(final Set<Object> set)
            throws DataSetException
    {
        final PrimaryKeyFilteredTableWrapper table =
                new PrimaryKeyFilteredTableWrapper(this.fTable,
                        new HashSet<>(set));
        assertTableSize(this.fTable, set.size());
        assertMetaInformationEquals(this.fTable, table);
        assertThat(table.getRowCount()).as("table is empty").isPositive();
        assertContentIsSame(this.fTable, table);
    }

    private void assertTableSize(final ITable table, final int i)
    {
        final int size = table.getRowCount();
        assertThat(size).as("getRowCount() didn't match").isEqualTo(i);
    }

    private Set<Object> getPKs(final ITable table) throws DataSetException
    {
        final String pkColumn =
                table.getTableMetaData().getPrimaryKeys()[0].getColumnName();
        final HashSet<Object> set = new HashSet<>();
        final int size = table.getRowCount();
        for (int i = 0; i < size; i++)
        {
            final Object pk = table.getValue(i, pkColumn);
            set.add(pk);
        }
        return set;
    }

    private void assertSecondTableIsEmpty(final ITable t1, final ITable t2)
            throws DataSetException
    {
        final int size = t1.getRowCount();
        final Column[] cols = t1.getTableMetaData().getColumns();
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < cols.length; j++)
            {
                final String col = cols[j].getColumnName();
                try
                {
                    final Object o = t2.getValue(j, col);
                    fail("there is an element at (" + i + ", " + col + ")" + o);
                } catch (final RowOutOfBoundsException e)
                {
                    assertThat(e).message().isNotNull();
                }
            }
        }
    }

    private void assertContentIsSame(final ITable t1, final ITable t2)
            throws DataSetException
    {
        final int size = t1.getRowCount();
        final Column[] cols = t1.getTableMetaData().getColumns();
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < cols.length; j++)
            {
                final String col = cols[j].getColumnName();
                final Object o1 = t1.getValue(j, col);
                final Object o2 = t2.getValue(j, col);
                assertThat(o1).as(
                        "element at (" + i + ", " + col + ") is not the same: ")
                        .isEqualTo(o2);
            }
        }
    }

    private void assertMetaInformationEquals(final ITable t1, final ITable t2)
    {
        final ITableMetaData metaData1 = t1.getTableMetaData();
        final ITableMetaData metaData2 = t2.getTableMetaData();
        assertThat(metaData1).as("metadata are not equal").isEqualTo(metaData2);
    }

}
