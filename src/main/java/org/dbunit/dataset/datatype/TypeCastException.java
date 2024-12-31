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

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class TypeCastException extends DataTypeException
{
    // public TypeCastException()
    // {
    // super();
    // }
    //
    // public TypeCastException(String msg)
    // {
    // super(msg);
    // }

    public TypeCastException(Throwable e)
    {
        super(e);
    }

    public TypeCastException(String msg, Throwable e)
    {
        super(msg, e);
    }

    public TypeCastException(Object value, DataType dataType)
    {
        super(buildMessage(value, dataType));
    }

    public TypeCastException(Object value, DataType dataType, Throwable e)
    {
        super(buildMessage(value, dataType), e);
    }

    private static String buildMessage(Object value, DataType dataType)
    {
        String valueClass =
                (value == null ? "null" : value.getClass().getName());
        String message = "Unable to typecast value <" + value + "> of type <"
                + valueClass + "> to " + dataType;
        return message;
    }

}
