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
package org.dbunit.ext.mssql;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialized factory that recognizes MS SQL Server data types.
 *
 * @author Manuel Laflamme
 * @since May 19, 2003
 * @version $Revision$
 */
public class MsSqlDataTypeFactory extends DefaultDataTypeFactory
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(MsSqlDataTypeFactory.class);
    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS = Arrays.asList(new String[] {"mssql", "Microsoft SQL Server"});
    
    private static final DateTimeOffsetType DATE_TIME_OFFSET_TYPE = new DateTimeOffsetType();

    /** JDBC type code for MS SQL Server's <code>nchar</code> type. */
    public static final int NCHAR = -8;
    /** JDBC type code for MS SQL Server's <code>nvarchar</code> type. */
    public static final int NVARCHAR = -9;
    /** JDBC type code for MS SQL Server's <code>ntext</code> type. */
    public static final int NTEXT = -10;
    /** JDBC type code for MS SQL Server 2005's <code>ntext</code> type. */
    public static final int NTEXT_MSSQL_2005 = -16;

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

        // TODO : Process MS SQL Server custom datatype here
        if(sqlType == Types.CHAR)
        {
            if (UniqueIdentifierType.UNIQUE_IDENTIFIER_TYPE.equals(sqlTypeName))
            {
                return new UniqueIdentifierType();
            }
        }

        switch(sqlType) {
            case NCHAR: return DataType.CHAR; // nchar
            case NVARCHAR: return DataType.VARCHAR; // nvarchar
            case NTEXT: return DataType.LONGVARCHAR; // ntext
            case NTEXT_MSSQL_2005: return DataType.LONGVARCHAR; // ntext
            case DateTimeOffsetType.TYPE: return DATE_TIME_OFFSET_TYPE;
            default: return super.createDataType(sqlType, sqlTypeName);
        }
    }
}
