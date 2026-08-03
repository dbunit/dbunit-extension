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

import org.dbunit.dataset.DataSetException;

/**
 * This exception is thrown by {@link org.dbunit.dataset.IDataSet} when multiple tables
 * having the same name are accessible. This usually occurs when the database
 * connection have access to multiple schemas containing identical table names.
 * <p>
 * Possible solutions:
 * 1) Use a database connection credential that has access to only one database
 * schema.
 * 2) Specify a schema name to the {@link DatabaseConnection} or
 * {@link DatabaseDataSourceConnection} constructor.
 * 3) Enable the qualified table name support (see How-to documentation).
 * </p>
 * 
 * <p>
 * Another common reason for this exception to be thrown is when an XML file
 * contains the same table multiple times whereas a different table is between
 * the definition of the duplicate table.
 * </p>
 * 
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0  May 1, 2002
 */
public class AmbiguousTableNameException extends DataSetException
{
    /**
     * Constructs an <code>AmbiguousTableNameException</code> with no detail
     * message and no encapsulated exception.
     */
    public AmbiguousTableNameException()
    {
    }

    /**
     * Constructs an <code>AmbiguousTableNameException</code> with the specified detail
     * message and no encapsulated exception.
     *
     * @param msg the detail message, typically the ambiguous table name.
     */
    public AmbiguousTableNameException(String msg)
    {
        super(msg);
    }

    /**
     * Constructs an <code>AmbiguousTableNameException</code> with the specified detail
     * message and encapsulated exception.
     *
     * @param msg the detail message, typically the ambiguous table name.
     * @param e the encapsulated exception.
     */
    public AmbiguousTableNameException(String msg, Throwable e)
    {
        super(msg, e);
    }

    /**
     * Constructs an <code>AmbiguousTableNameException</code> with the encapsulated
     * exception and use string representation as detail message.
     *
     * @param e the encapsulated exception.
     */
    public AmbiguousTableNameException(Throwable e)
    {
        super(e);
    }
}


