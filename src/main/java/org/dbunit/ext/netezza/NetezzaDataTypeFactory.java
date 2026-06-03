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
package org.dbunit.ext.netezza;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetezzaDataTypeFactory - This class is for the DBUnit data type factory for Netezza database
 * 
 * @author Ameet (amit3011 AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.6
 */
public class NetezzaDataTypeFactory extends DefaultDataTypeFactory
{

	/**
	 * Logger for this class
	 */
	private static final Logger logger = LoggerFactory.getLogger(NetezzaDataTypeFactory.class);

	public static final int RECADDR = 1;
	public static final int NUMERIC = 2;
	public static final int DECIMAL = 3;
	public static final int INTEGER = 4;
	public static final int SMALLINT = 5;
	public static final int DOUBLE = 8;
	public static final int INTERVAL = 10;
	public static final int BOOLEAN = -7;
	public static final int CHAR = -1;
	public static final int FLOAT = 6;
	public static final int REAL = 7;
	public static final int VARCHAR = 12;
	public static final int DATE = 91;
	public static final int TIME = 92;
	public static final int TIMESTAMP = 93;
	public static final int TIMETZ = 1266;
	public static final int UNKNOWN = 18;
	public static final int BYTEINT = -6;
	public static final int INT8 = 20;
	public static final int VARFIXEDCHAR = 21;
	public static final int NUCL = 22;
	public static final int PROT = 23;
	public static final int BLOB = 24;
	public static final int BIGINT = -5;
	public static final int NCHAR = -8;
	public static final int NVARCHAR = -9;
	public static final int NTEXT = 27;

	public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException
	{
		if (logger.isDebugEnabled())
			logger.debug("createDataType(sqlType={}, sqlTypeName={}) - start", String.valueOf(sqlType), sqlTypeName);

		switch (sqlType)
		{
			case RECADDR:
				return DataType.VARCHAR;

			case INTEGER:
				return DataType.INTEGER;

			case INTERVAL:
				return DataType.TIMESTAMP;
			case TIMETZ:
				return DataType.TIMESTAMP;
			case BOOLEAN:
				return DataType.BOOLEAN;
			case SMALLINT:
				return DataType.SMALLINT;

			case REAL:
				return DataType.FLOAT;
			case BYTEINT:
				return DataType.INTEGER;
			case INT8:
				return DataType.BIGINT;
			case VARFIXEDCHAR:
				return DataType.CHAR;
			case NUCL:
				return DataType.CHAR;
			case PROT:
				return DataType.CHAR;
			case DATE:
				return DataType.DATE;
			case BLOB:
				return DataType.BLOB;
			case NCHAR:
				return DataType.CHAR;
			case NVARCHAR:
				return DataType.VARCHAR;
			case NTEXT:
				return DataType.LONGVARCHAR;
			case VARCHAR:
				return DataType.VARCHAR;
			default:
				return super.createDataType(sqlType, sqlTypeName);
		}
	}
}

 	  	 
