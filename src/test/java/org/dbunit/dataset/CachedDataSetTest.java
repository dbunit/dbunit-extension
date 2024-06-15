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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileReader;

import org.dbunit.dataset.xml.FlatXmlDataSetTest;
import org.dbunit.dataset.xml.FlatXmlProducer;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.x (Apr 18, 2003)
 */
class CachedDataSetTest extends AbstractDataSetDecoratorTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final FileReader reader =
                new FileReader(FlatXmlDataSetTest.DATASET_FILE);
        return new CachedDataSet(new FlatXmlProducer(new InputSource(reader)));
    }

    @Override
    public void testGetTable() throws Exception
    {
        super.testGetTable();
    }

    /**
     * Test constructor CacheDataSet(IDataSet dataSet).
     *
     * The automated test inherited from AbstractDataSetDecoratorTest do not
     * test this constructor.
     */
    @Test
    void testCachedDataSetDataSetConstructor() throws Exception
    {
        final IDataSet cachedDataSetCreatedByProducer = createDataSet();

        // createDateSet() returns a CacheDataSet that was created using the
        // producer constructor. By wrapping its returned value in another
        // CachedDataSet, we test the CacheDataSet(IDataSet dataSet)
        // constructor which this test is designed for.
        final CachedDataSet cachedDataSetCreatedByDataSetConstructor =
                new CachedDataSet(cachedDataSetCreatedByProducer);

        // The test consist of iterating through all the tables found in
        // cachedDataSetCreatedByProducer and see if
        // cachedDataSetCreatedByDataSetConstructor has them also. If they
        // are missing, something went wrong.
        final ITableIterator iterator =
                cachedDataSetCreatedByProducer.iterator();
        while (iterator.next())
        {
            final String tableNameFromProducer =
                    iterator.getTable().getTableMetaData().getTableName();
            try
            {
                assertThat(cachedDataSetCreatedByDataSetConstructor
                        .getTable(tableNameFromProducer)).isNotNull();
            } catch (final Exception exception)
            {
                fail("Table " + tableNameFromProducer + " was not cached.");
            }
        }
    }
}
