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
 * Base unchecked exception for errors raised by the DbUnit framework.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
public class DatabaseUnitRuntimeException extends RuntimeException
{

    /**
	 * 
	 */
	private static final long serialVersionUID = -3238403495229458202L;


    /**
     * Constructs an <code>DatabaseUnitRuntimeException</code> with no specified
     * detail message and no encapsulated exception.
     */
    public DatabaseUnitRuntimeException()
    {
        super();
    }

    /**
     * Constructs an <code>DatabaseUnitRuntimeException</code> with the specified
     * detail message and no encapsulated exception.
     * @param msg Exception message
     */
    public DatabaseUnitRuntimeException(String msg)
    {
        super(msg);
    }

    /**
     * Constructs an <code>DatabaseUnitRuntimeException</code> with the specified
     * detail message and encapsulated exception.
     * @param msg
     * @param cause The cause of this exception
     */
    public DatabaseUnitRuntimeException(String msg, Throwable cause)
    {
        super(msg, cause);
    }

    /**
     * Constructs an <code>DatabaseUnitRuntimeException</code> with the encapsulated
     * exception and use its message as detail message.
     * @param cause The cause of this exception
     */
    public DatabaseUnitRuntimeException(Throwable cause)
    {
        super(cause.toString(), cause);
    }

    /**
     * Returns the encapsulated exception or <code>null</code> if none.
     * @deprecated Use {@link Exception#getCause()} instead
     */
    public Throwable getException()
    {
        return super.getCause();
    }

}




