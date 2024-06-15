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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Apr 6, 2003
 * @version $Revision$
 */
public abstract class AbstractTableIteratorTest extends AbstractTest
{

    protected abstract ITableIterator getIterator() throws Exception;

    protected abstract ITableIterator getEmptyIterator() throws Exception;

    @Test
    void testNext() throws Exception
    {
        int count = 0;
        final String[] names = getExpectedNames();
        final ITableIterator iterator = getIterator();
        while (iterator.next())
        {
            count++;
        }
        assertThat(count).as("count").isEqualTo(names.length);
    }

    @Test
    void testNextAndEmpty() throws Exception
    {
        int count = 0;
        final ITableIterator iterator = getEmptyIterator();
        while (iterator.next())
        {
            count++;
        }
        assertThat(count).as("count").isZero();
    }

    @Test
    void testGetTableMetaData() throws Exception
    {
        int i = 0;
        final String[] names = getExpectedNames();
        final ITableIterator iterator = getIterator();
        while (iterator.next())
        {
            assertThat(iterator.getTableMetaData().getTableName())
                    .as("name " + i).isEqualTo(names[i]);
            i++;
        }
        assertThat(i).as("count").isEqualTo(names.length);
    }

    @Test
    void testGetTableMetaDataBeforeNext() throws Exception
    {
        final ITableIterator iterator = getIterator();
        assertThrows(IndexOutOfBoundsException.class,
                () -> iterator.getTableMetaData(),
                "Should have throw a ???Exception");

        int i = 0;
        final String[] names = getExpectedNames();
        while (iterator.next())
        {
            assertThat(iterator.getTableMetaData().getTableName())
                    .as("name " + i).isEqualTo(names[i]);
            i++;
        }
        assertThat(i).as("count").isEqualTo(names.length);

    }

    @Test
    void testGetTableMetaDataAfterLastNext() throws Exception
    {
        int count = 0;
        final String[] names = getExpectedNames();
        final ITableIterator iterator = getIterator();
        while (iterator.next())
        {
            count++;
        }
        assertThat(count).as("count").isEqualTo(names.length);

        assertThrows(IndexOutOfBoundsException.class,
                () -> iterator.getTableMetaData(),
                "Should have throw a ???Exception");

    }

    @Test
    void testGetTable() throws Exception
    {
        int i = 0;
        final String[] names = getExpectedNames();
        final ITableIterator iterator = getIterator();
        while (iterator.next())
        {
            assertThat(iterator.getTable().getTableMetaData().getTableName())
                    .as("name " + i).isEqualTo(names[i]);
            i++;
        }
        assertThat(i).as("count").isEqualTo(names.length);
    }
}
