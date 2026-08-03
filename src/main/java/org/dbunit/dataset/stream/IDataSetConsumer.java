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
 * Receive notification of the content of a dataset.
 *
 * @author Manuel Laflamme
 * @since Apr 17, 2003
 * @version $Revision$
 */
public interface IDataSetConsumer
{
    /**
     * Receive notification of the beginning of a dataset. This method is
     * invoked only once, before any other methods in this interface.
     * @throws DataSetException if the notification cannot be processed.
     */
    public void startDataSet() throws DataSetException;

    /**
     * Receive notification of the end of a dataset. This method is invoked only
     * once, and it will be the last method invoked in this interface.
     * @throws DataSetException if the notification cannot be processed.
     */
    public void endDataSet() throws DataSetException;

    /**
     * Receive notification of the beginning of a table. This method is invoked
     * at the beginning of every table in the dataset; there will be a
     * corresponding {@link #endDataSet} event for every <code>startTable</code>
     * event (even when the table is empty).
     * @param metaData the table metadata
     * @throws DataSetException if the notification cannot be processed.
     */
    public void startTable(ITableMetaData metaData) throws DataSetException;

    /**
     * Receive notification of the end of a table.
     * @throws DataSetException if the notification cannot be processed.
     */
    public void endTable() throws DataSetException;

    /**
     * Receive notification of a table row. This method is invoked to report
     * each row of a table.
     * @param values The row values.
     * @throws DataSetException if the notification cannot be processed.
     */
    public void row(Object[] values) throws DataSetException;
}
