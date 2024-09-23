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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract data type implementation that provides generic methods that are
 * appropriate for most data type implementations. Among those is the 
 * generic implementation of the {@link #compare(Object, Object)} method. 
 * 
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Mar 19, 2002
 */
public abstract class AbstractDataType extends DataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractDataType.class);

    private final String _name;
    private final int _sqlType;
    private final Class _classType;
    private final boolean _isNumber;

    public AbstractDataType(String name, int sqlType, Class classType,
            boolean isNumber)
    {
        _sqlType = sqlType;
        _name = name;
        _classType = classType;
        _isNumber = isNumber;
    }

    ////////////////////////////////////////////////////////////////////////////
    // DataType class

    public int compare(Object o1, Object o2) throws TypeCastException
    {
        logger.debug("compare(o1={}, o2={}) - start", o1, o2);

        try
        {
        	// New in 2.3: Object level check for equality - should give massive performance improvements
        	// in the most cases because the typecast can be avoided (null values and equal objects)
            if(areObjectsEqual(o1, o2))
            {
                return 0;
            }
        	
        	
        	// Comparable check based on the results of method "typeCast"
            Object value1 = typeCast(o1);
            Object value2 = typeCast(o2);

            // Check for "null"s again because typeCast can produce them

            if (value1 == null && value2 == null)
            {
                return 0;
            }

            if (value1 == null && value2 != null)
            {
                return -1;
            }

            if (value1 != null && value2 == null)
            {
                return 1;
            }

            return compareNonNulls(value1, value2);

        }
        catch (ClassCastException e)
        {
            throw new TypeCastException(e);
        }
    }

    /**
     * Compares non-null values to each other. Both objects are guaranteed to be not
     * null and to implement the interface {@link Comparable}. The two given objects
     * are the results of the {@link #typeCast(Object)} method call which is usually
     * implemented by a specialized {@link DataType} implementation.
     * @param value1 First value resulting from the {@link #typeCast(Object)} method call
     * @param value2 Second value resulting from the {@link #typeCast(Object)} method call
     * @return The result of the {@link Comparable#compareTo(Object)} invocation.
     * @throws TypeCastException
     */
    protected int compareNonNulls(Object value1, Object value2) throws TypeCastException
    {
        logger.debug("compareNonNulls(value1={}, value2={}) - start", value1, value2);

        Comparable value1comp = (Comparable)value1;
        Comparable value2comp = (Comparable)value2;
        return value1comp.compareTo(value2comp);
    }

    /**
     * Checks whether the given objects are equal or not.
     * @param o1 first object
     * @param o2 second object
     * @return <code>true</code> if both objects are <code>null</code> (and hence equal)
     * or if the <code>o1.equals(o2)</code> is <code>true</code>.
     */
    protected final boolean areObjectsEqual(Object o1, Object o2) 
    {
        if(o1 == null && o2 == null)
        {
            return true;
        }
        return o1 != null && o1.equals(o2);
        // Note that no more check is needed for o2 because it definitely does is not equal to o1
        // Instead immediately proceed with the typeCast method
    }

    public int getSqlType()
    {
        logger.debug("getSqlType() - start");

        return _sqlType;
    }

    public Class getTypeClass()
    {
        logger.debug("getTypeClass() - start");

        return _classType;
    }

    public boolean isNumber()
    {
        logger.debug("isNumber() - start");

        return _isNumber;
    }

    public boolean isDateTime()
    {
        logger.debug("isDateTime() - start");

        return false;
    }

    public Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        Object value = resultSet.getObject(column);
        if (value == null || resultSet.wasNull())
        {
            return null;
        }
        return value;
    }

    public void setSqlValue(Object value, int column, PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        statement.setObject(column, typeCast(value), getSqlType());
    }

    /**
     * @param clazz The fully qualified name of the class to be loaded
     * @param connection The JDBC connection needed to load the given class
     * @return The loaded class
     * @throws ClassNotFoundException
     */
    protected final Class loadClass(String clazz, Connection connection) throws ClassNotFoundException
    {
        ClassLoader connectionClassLoader = connection.getClass().getClassLoader();
        return this.loadClass(clazz, connectionClassLoader);
    }
    
    /**
     * @param clazz The fully qualified name of the class to be loaded
     * @param classLoader The classLoader to be used to load the given class
     * @return The loaded class
     * @throws ClassNotFoundException
     */
    protected final Class loadClass(String clazz, ClassLoader classLoader) throws ClassNotFoundException
    {
        Class loadedClass = classLoader.loadClass(clazz);
        return loadedClass;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Object class

    public String toString()
    {
        return _name;
    }
}



