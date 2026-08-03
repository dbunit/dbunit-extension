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

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;

/**
 * This class provides no op implementations for all of the callbacks in the
 * {@link org.dbunit.dataset.stream.IDataSetConsumer} interface.
 *
 * @author Manuel Laflamme
 * @since Apr 29, 2003
 * @version $Revision$
 */
public class DefaultConsumer implements IDataSetConsumer
{

    public void startDataSet() throws DataSetException
    {
        // no op
    }

    public void endDataSet() throws DataSetException
    {
        // no op
    }

    public void startTable(ITableMetaData metaData) throws DataSetException
    {
        // no op
    }

    public void endTable() throws DataSetException
    {
        // no op
    }

    public void row(Object[] values) throws DataSetException
    {
        // no op
    }
}
