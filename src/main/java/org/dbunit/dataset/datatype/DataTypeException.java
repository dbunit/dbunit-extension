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

package org.dbunit.dataset.datatype;

import org.dbunit.dataset.DataSetException;

/**
 * Base checked exception for errors related to {@link DataType} value conversion.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 */

public class DataTypeException extends DataSetException
{

    /**
     * Constructs a <code>DataTypeException</code> with no detail message and no encapsulated
     * exception.
     */
    public DataTypeException()
    {
        super();
    }

    /**
     * Constructs a <code>DataTypeException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public DataTypeException(String msg)
    {
        super(msg);
    }

    /**
     * Constructs a <code>DataTypeException</code> with the encapsulated exception.
     *
     * @param e the encapsulated exception.
     */
    public DataTypeException(Throwable e)
    {
        super(e);
    }

    /**
     * Constructs a <code>DataTypeException</code> with the specified detail message and
     * encapsulated exception.
     *
     * @param msg the detail message.
     * @param e the encapsulated exception.
     */
    public DataTypeException(String msg, Throwable e)
    {
        super(msg, e);
    }
}
