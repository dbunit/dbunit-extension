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

/**
 * Iterator over the {@link ITable}s of an {@link IDataSet}.
 *
 * @author Manuel Laflamme
 * @since Apr 5, 2003
 * @version $Revision$
 */
public interface ITableIterator
{
    /**
     * Position this iterator to the next table. The iterator is initially
     * positioned before the first table; the first call to the method next
     * makes the first table the current table; the second call makes the
     * second table the current table, and so on.
     *
     * @return <code>true</code> if the new current table is valid;
     * <code>false</code> if there are no more table
     */
    public boolean next() throws DataSetException;

    /**
     * Returns the metadata of the current table.
     */
    public ITableMetaData getTableMetaData() throws DataSetException;

    /**
     * Returns the current table.
     */
    public ITable getTable() throws DataSetException;
}
