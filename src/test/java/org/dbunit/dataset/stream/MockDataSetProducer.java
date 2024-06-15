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

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;

/**
 * @author Manuel Laflamme
 * @since Jun 12, 2003
 * @version $Revision$
 */
public class MockDataSetProducer implements IDataSetProducer
{
    private int _tableCount;
    private int _columnCount;
    private int _rowCount;
    private IDataSetConsumer _consumer = new DefaultConsumer();

    public void setupTableCount(int tableCount)
    {
        _tableCount = tableCount;
    }

    public void setupColumnCount(int columnCount)
    {
        _columnCount = columnCount;
    }

    public void setupRowCount(int rowCount)
    {
        _rowCount = rowCount;
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSetProducer interface

    public void setConsumer(IDataSetConsumer consumer) throws DataSetException
    {
        _consumer = consumer;
    }

    public void produce() throws DataSetException
    {
        _consumer.startDataSet();
        for (int i = 0; i < _tableCount; i++)
        {
            String tableName = "TABLE" + i;
            Column[] columns = new Column[_columnCount];
            for (int j = 0; j < columns.length; j++)
            {
                columns[j] = new Column("COLUMN" + j, DataType.UNKNOWN);
            }
            ITableMetaData metaData =
                    new DefaultTableMetaData(tableName, columns);

            _consumer.startTable(metaData);

            for (int j = 0; j < _rowCount; j++)
            {
                Object[] values = new Object[_columnCount];
                for (int k = 0; k < values.length; k++)
                {
                    values[k] = j + "," + k;
                }
                _consumer.row(values);
            }
            _consumer.endTable();
        }
        _consumer.endDataSet();
    }
}
