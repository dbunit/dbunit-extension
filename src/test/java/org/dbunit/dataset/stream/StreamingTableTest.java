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
package org.dbunit.dataset.stream;

import java.io.FileReader;

import org.dbunit.dataset.ForwardOnlyTableTest;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.xml.FlatXmlDataSetTest;
import org.dbunit.dataset.xml.FlatXmlProducer;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @since Apr 11, 2003
 * @version $Revision$
 */
public class StreamingTableTest extends ForwardOnlyTableTest
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Override
    protected ITable createTable() throws Exception
    {
        final FileReader reader =
                new FileReader(FlatXmlDataSetTest.DATASET_FILE);

        // IDataSetProducer source = new DataSetProducerAdapter(new
        // FlatXmlDataSet(reader));
        final IDataSetProducer source =
                new FlatXmlProducer(new InputSource(reader));
        final ITableIterator iterator = new StreamingDataSet(source).iterator();
        while (iterator.next())
        {
            final ITable table = iterator.getTable();
            final String tableName = table.getTableMetaData().getTableName();
            if (tableName.equals(TEST_TABLE))
            {
                return table;
            }
        }

        throw new IllegalStateException();
    }
}
