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
 * Thrown to indicate that a database column has been accessed that does not
 * exist. 
 * 
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public class NoSuchColumnException extends DataSetException
{
    /**
     * @deprecated since 2.3.0. Prefer constructor taking a table/columnName as argument
     */
    public NoSuchColumnException()
    {
    }
    
    /**
     * @deprecated since 2.3.0. Prefer constructor taking a table/columnName as argument
     */
    public NoSuchColumnException(String msg)
    {
        super(msg);
    }

    /**
     * Creates an exception using the given table name + column name
     * @param tableName table in which the column was not found. Can be null
     * @param columnName the column that was not found
     * @since 2.3.0
     */
    public NoSuchColumnException(String tableName, String columnName)
    {
        this(tableName, columnName, null);
    }

    /**
     * Creates an exception using the given table name + column name
     * @param tableName table in which the column was not found. Can be null
     * @param columnName the column that was not found
     * @param msg Additional message to append to the exception text
     * @since 2.3.0
     */
    public NoSuchColumnException(String tableName, String columnName, String msg)
    {
        super(buildText(tableName, columnName, msg));
    }

	/**
	 * @param msg
	 * @param e
     * @deprecated since 2.3.0. Prefer constructor taking a table/columnName as argument
	 */
	public NoSuchColumnException(String msg, Throwable e)
    {
        super(msg, e);
    }

    /**
     * @param e
     * @deprecated since 2.3.0. Prefer constructor taking a table/columnName as argument
     */
    public NoSuchColumnException(Throwable e)
    {
        super(e);
    }
    
    
    private static String buildText(String tableName, String columnName, String message) {
        final StringBuilder sb = new StringBuilder();
    	if(tableName != null){
    		sb.append(tableName).append(".");
    	}
    	sb.append(columnName);
    	if(message != null) {
    		sb.append(" - ").append(message);
    	}
		return sb.toString();
	}

}





