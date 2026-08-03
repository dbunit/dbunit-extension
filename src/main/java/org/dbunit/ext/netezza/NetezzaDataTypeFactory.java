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

	/** JDBC type code for Netezza's <code>RECADDR</code> type. */
	public static final int RECADDR = 1;
	/** JDBC type code for Netezza's <code>NUMERIC</code> type. */
	public static final int NUMERIC = 2;
	/** JDBC type code for Netezza's <code>DECIMAL</code> type. */
	public static final int DECIMAL = 3;
	/** JDBC type code for Netezza's <code>INTEGER</code> type. */
	public static final int INTEGER = 4;
	/** JDBC type code for Netezza's <code>SMALLINT</code> type. */
	public static final int SMALLINT = 5;
	/** JDBC type code for Netezza's <code>DOUBLE</code> type. */
	public static final int DOUBLE = 8;
	/** JDBC type code for Netezza's <code>INTERVAL</code> type. */
	public static final int INTERVAL = 10;
	/** JDBC type code for Netezza's <code>BOOLEAN</code> type. */
	public static final int BOOLEAN = -7;
	/** JDBC type code for Netezza's <code>CHAR</code> type. */
	public static final int CHAR = -1;
	/** JDBC type code for Netezza's <code>FLOAT</code> type. */
	public static final int FLOAT = 6;
	/** JDBC type code for Netezza's <code>REAL</code> type. */
	public static final int REAL = 7;
	/** JDBC type code for Netezza's <code>VARCHAR</code> type. */
	public static final int VARCHAR = 12;
	/** JDBC type code for Netezza's <code>DATE</code> type. */
	public static final int DATE = 91;
	/** JDBC type code for Netezza's <code>TIME</code> type. */
	public static final int TIME = 92;
	/** JDBC type code for Netezza's <code>TIMESTAMP</code> type. */
	public static final int TIMESTAMP = 93;
	/** JDBC type code for Netezza's <code>TIMETZ</code> type. */
	public static final int TIMETZ = 1266;
	/** JDBC type code for Netezza's <code>UNKNOWN</code> type. */
	public static final int UNKNOWN = 18;
	/** JDBC type code for Netezza's <code>BYTEINT</code> type. */
	public static final int BYTEINT = -6;
	/** JDBC type code for Netezza's <code>INT8</code> type. */
	public static final int INT8 = 20;
	/** JDBC type code for Netezza's <code>VARFIXEDCHAR</code> type. */
	public static final int VARFIXEDCHAR = 21;
	/** JDBC type code for Netezza's <code>NUCL</code> type. */
	public static final int NUCL = 22;
	/** JDBC type code for Netezza's <code>PROT</code> type. */
	public static final int PROT = 23;
	/** JDBC type code for Netezza's <code>BLOB</code> type. */
	public static final int BLOB = 24;
	/** JDBC type code for Netezza's <code>BIGINT</code> type. */
	public static final int BIGINT = -5;
	/** JDBC type code for Netezza's <code>NCHAR</code> type. */
	public static final int NCHAR = -8;
	/** JDBC type code for Netezza's <code>NVARCHAR</code> type. */
	public static final int NVARCHAR = -9;
	/** JDBC type code for Netezza's <code>NTEXT</code> type. */
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

