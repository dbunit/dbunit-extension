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
package org.dbunit.ext.oracle;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

import org.dbunit.dataset.datatype.BinaryStreamDataType;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.dataset.datatype.StringDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialized factory that recognizes Oracle data types.
 * @author manuel.laflamme
 * @since Jul 17, 2003
 * @version $Revision$
 */
public class OracleDataTypeFactory extends DefaultDataTypeFactory
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(OracleDataTypeFactory.class);
    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS = Arrays.asList(new String[] {"oracle"});

    /** Data type for Oracle <code>BLOB</code> columns. */
    public static final DataType ORACLE_BLOB = new OracleBlobDataType();
    /** Data type for Oracle <code>CLOB</code> columns. */
    public static final DataType ORACLE_CLOB = new OracleClobDataType();
    /** Data type for Oracle <code>NCLOB</code> columns. */
    public static final DataType ORACLE_NCLOB = new OracleNClobDataType();
    /** Data type for Oracle <code>XMLTYPE</code> columns. */
    public static final DataType ORACLE_XMLTYPE = new OracleXMLTypeDataType();
    /** Data type for Oracle <code>SDO_GEOMETRY</code> columns. */
    public static final DataType ORACLE_SDO_GEOMETRY_TYPE = new OracleSdoGeometryDataType();

    /** Data type for Oracle <code>LONG RAW</code> columns. */
    public static final DataType LONG_RAW = new BinaryStreamDataType(
            "LONG RAW", Types.LONGVARBINARY);

    /** Data type for Oracle <code>ROWID</code> columns. */
    public static final DataType ROWID_TYPE = new StringDataType("ROWID", Types.OTHER);

    /**
     * @see org.dbunit.dataset.datatype.IDbProductRelatable#getValidDbProducts()
     */
    public Collection getValidDbProducts()
    {
      return DATABASE_PRODUCTS;
    }

    public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException
    {
    	if(logger.isDebugEnabled())
    		logger.debug("createDataType(sqlType={}, sqlTypeName={}) - start", String.valueOf(sqlType), sqlTypeName);

        // Map Oracle DATE to TIMESTAMP
        if (sqlType == Types.DATE)
        {
            return DataType.TIMESTAMP;
        }

        // TIMESTAMP
        if (sqlTypeName.startsWith("TIMESTAMP"))
        {
            return DataType.TIMESTAMP;
        }

        // XMLTYPE
        if ("XMLTYPE".equals(sqlTypeName) || "SYS.XMLTYPE".equals(sqlTypeName))
        {
            return ORACLE_XMLTYPE;
        }

        // BLOB
        if ("BLOB".equals(sqlTypeName))
        {
            return ORACLE_BLOB;
        }

        // CLOB
        if ("CLOB".equals(sqlTypeName))
        {
            return ORACLE_CLOB;
        }
        
        // NCLOB
        if  ("NCLOB".equals(sqlTypeName))
        {
            return ORACLE_NCLOB;
        }

        // NVARCHAR2
        if ("NVARCHAR2".equals(sqlTypeName))
        {
            return DataType.VARCHAR;
        }

		// NCHAR
        if (sqlTypeName.startsWith("NCHAR"))
        {
            return DataType.CHAR;
        }

        // FLOAT
        if ("FLOAT".equals(sqlTypeName))
        {
            return DataType.FLOAT;
        }

        // LONG RAW
        if (LONG_RAW.toString().equals(sqlTypeName))
        {
            return LONG_RAW;
        }

        // BINARY_DOUBLE/BINARY_FLOAT
        // Note that you have to configure your driver appropriate:
        // Oracle-specific property to support IEEE floating-point is enabled setting the following property
        // <value>SetFloatAndDoubleUseBinary=true</value>
        if ("BINARY_DOUBLE".equals(sqlTypeName)) 
        {
            return DataType.DOUBLE;
        }
        if ("BINARY_FLOAT".equals(sqlTypeName)) 
        {
            return DataType.FLOAT;
        }

        // ROWID
        if ("ROWID".equals(sqlTypeName))
        {
            return ROWID_TYPE;
        }

        // SDO_GEOMETRY
        if ("SDO_GEOMETRY".equals(sqlTypeName) || "MDSYS.SDO_GEOMETRY".equals(sqlTypeName))
        {
            return ORACLE_SDO_GEOMETRY_TYPE;
        }

        return super.createDataType(sqlType, sqlTypeName);
    }
}

