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
package org.dbunit.ext.mckoi;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

/**
 * MckoiDataTypeFactory - This class is for the DBUnit data type factory for Mckoi database
 * 
 * @author Luigi Talamona (luigitalamona AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public class MckoiDataTypeFactory extends DefaultDataTypeFactory {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(MckoiDataTypeFactory.class);


    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS = Arrays.asList(new String[] {"Mckoi"});

    /**
     * @see org.dbunit.dataset.datatype.IDbProductRelatable#getValidDbProducts()
     */
    public Collection getValidDbProducts()
    {
      return DATABASE_PRODUCTS;
    }


    public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException {
        DataType retValue = super.createDataType(sqlType, sqlTypeName);

        if (logger.isDebugEnabled()) {
            logger.debug("createDataType(sqlType={}, sqlTypeName={}) - start", String.valueOf(sqlType), sqlTypeName);
        }

        retValue = (sqlTypeName.equals("BOOLEAN"))? DataType.BOOLEAN: retValue;
        retValue = (sqlTypeName.equals("CHAR"))? DataType.CHAR: retValue;
        retValue = (sqlTypeName.equals("CHARACTER"))? DataType.CHAR: retValue;

        retValue = (sqlTypeName.equals("VARCHAR"))? DataType.VARCHAR: retValue;
        retValue = (sqlTypeName.equals("LONGVARCHAR"))? DataType.LONGVARCHAR: retValue;
        retValue = (sqlTypeName.equals("CHARACTER VARYING"))? DataType.VARCHAR: retValue;
        retValue = (sqlTypeName.equals("LONG CHARACTER VARYING"))? DataType.VARCHAR: retValue;
        retValue = (sqlTypeName.equals("TEXT"))? DataType.VARCHAR: retValue;
        retValue = (sqlTypeName.equals("STRING"))? DataType.VARCHAR: retValue;

        retValue = (sqlTypeName.equals("CLOB"))? DataType.CLOB: retValue;
        retValue = (sqlTypeName.equals("TINYINT"))? DataType.TINYINT: retValue;
        retValue = (sqlTypeName.equals("SMALLINT"))? DataType.SMALLINT: retValue;
        retValue = (sqlTypeName.equals("INTEGER"))? DataType.INTEGER: retValue;
        retValue = (sqlTypeName.equals("INT"))? DataType.INTEGER: retValue;

        retValue = (sqlTypeName.equals("BIGINT"))? DataType.BIGINT: retValue;
        retValue = (sqlTypeName.equals("FLOAT"))? DataType.FLOAT: retValue;
        retValue = (sqlTypeName.equals("DOUBLE"))? DataType.DOUBLE: retValue;
        retValue = (sqlTypeName.equals("REAL"))? DataType.REAL: retValue;
        retValue = (sqlTypeName.equals("NUMERIC"))? DataType.NUMERIC: retValue;
        retValue = (sqlTypeName.equals("DECIMAL"))? DataType.DECIMAL: retValue;
        retValue = (sqlTypeName.equals("DATE"))? DataType.DATE: retValue;
        retValue = (sqlTypeName.equals("TIME"))? DataType.TIME: retValue;
        retValue = (sqlTypeName.equals("TIMESTAMP"))? DataType.TIMESTAMP: retValue;

        retValue = (sqlTypeName.equals("BINARY"))? DataType.BINARY: retValue;
        retValue = (sqlTypeName.equals("VARBINARY"))? DataType.VARBINARY: retValue;
        retValue = (sqlTypeName.equals("LONGVARBINARY"))? DataType.LONGVARBINARY: retValue;
        retValue = (sqlTypeName.equals("BLOB"))? DataType.BLOB: retValue;
        retValue = (sqlTypeName.equals("JAVA_OBJECT"))? DataType.forSqlType(Types.JAVA_OBJECT): retValue;
        return retValue;
    }
}


 	  	 
