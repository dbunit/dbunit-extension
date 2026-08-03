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

package org.dbunit;


/**
 * Base checked exception for errors raised by the DbUnit framework.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class DatabaseUnitException extends Exception
{

    /**
	 * 
	 */
	private static final long	serialVersionUID	= 7597982895850877156L;


    /**
     * Constructs an <code>DatabaseUnitException</code> with no detail
     * message and no encapsulated exception.
     */
    public DatabaseUnitException()
    {
        super();
    }

    /**
     * Constructs an <code>DatabaseUnitException</code> with the specified detail
     * message and no encapsulated exception.
     */
    public DatabaseUnitException(String msg)
    {
        super(msg);
    }

    /**
     * Constructs an <code>DatabaseUnitException</code> with the specified detail
     * message and encapsulated exception.
     */
    public DatabaseUnitException(String msg, Throwable e)
    {
        super(msg, e);
    }

    /**
     * Constructs an <code>DatabaseUnitException</code> with the encapsulated
     * exception and use string representation as detail message.
     */
    public DatabaseUnitException(Throwable e)
    {
        super(e);
    }

    /**
     * Returns the nested exception or <code>null</code> if none.
     * @deprecated Use {@link #getCause()} to retrieve the nested exception
     */
    public Throwable getException()
    {
        return super.getCause();
    }

}
