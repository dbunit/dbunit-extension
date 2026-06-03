/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
package org.dbunit.ext.postgresql;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter to handle conversion between Postgresql
 * native Enum type and Strings.
 *
 * @author Jarvis Cochrane (jarvis@cochrane.com.au)
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.6
 */
public class GenericEnumType extends AbstractDataType {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(GenericEnumType.class);
    
    private final String sqlTypeName;

    /**
     * @param sqlTypeName The name of the enum type needed to invoke the "setType()" method on
     * the PGObject class.
     */
    public GenericEnumType(String sqlTypeName) 
    {
        super(sqlTypeName, Types.OTHER, String.class, false);
        
        if (sqlTypeName == null) {
            throw new NullPointerException(
                    "The parameter 'sqlTypeName' must not be null");
        }
        this.sqlTypeName = sqlTypeName;
    }

    public Object getSqlValue(int column, ResultSet resultSet) throws SQLException, TypeCastException 
    {
        return resultSet.getString(column);
    }

    public void setSqlValue(Object enumObject, int column,
                            PreparedStatement statement) throws SQLException, TypeCastException 
    {
        statement.setObject(column, getEnum(enumObject, statement.getConnection()));
    }

    public Object typeCast(Object arg0) throws TypeCastException 
    {
        return arg0.toString();
    }

    private Object getEnum(Object value, Connection connection) throws TypeCastException {

        logger.debug("getEnum(value={}, connection={}) - start", value, connection);

        Object tempEnum = null;

        try {
            Class aPGObjectClass = super.loadClass("org.postgresql.util.PGobject", connection);
            Constructor ct = aPGObjectClass.getConstructor(null);
            tempEnum = ct.newInstance(null);

            Method setTypeMethod = aPGObjectClass.getMethod("setType", new Class[]{String.class});
            setTypeMethod.invoke(tempEnum, new Object[]{this.sqlTypeName});

            Method setValueMethod = aPGObjectClass.getMethod("setValue", new Class[]{String.class});
            setValueMethod.invoke(tempEnum, new Object[]{value.toString()});

        } catch (ClassNotFoundException e) {
            throw new TypeCastException(value, this, e);
        } catch (InvocationTargetException e) {
            throw new TypeCastException(value, this, e);
        } catch (NoSuchMethodException e) {
            throw new TypeCastException(value, this, e);
        } catch (IllegalAccessException e) {
            throw new TypeCastException(value, this, e);
        } catch (InstantiationException e) {
            throw new TypeCastException(value, this, e);
        }

        return tempEnum;
    }

    public String getSqlTypeName() {
        return sqlTypeName;
    }
    
    
}
