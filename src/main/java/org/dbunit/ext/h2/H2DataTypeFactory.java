/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2008, DbUnit.org
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
package org.dbunit.ext.h2;

import java.util.Arrays;
import java.util.Collection;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialized factory that recognizes H2 data types: {@code BOOLEAN} as
 * {@link DataType#BOOLEAN}, {@code UUID} as {@link DataType#NVARCHAR} and {@code JSON} as
 * {@link H2JsonType}.
 *
 * @author Felipe Leme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.1
 */
public class H2DataTypeFactory extends DefaultDataTypeFactory
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(H2DataTypeFactory.class);
    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS = Arrays.asList(new String[] {"h2"});

    /**
     * @see org.dbunit.dataset.datatype.IDbProductRelatable#getValidDbProducts()
     */
    @Override
    public Collection getValidDbProducts()
    {
        return DATABASE_PRODUCTS;
    }

    @Override
    public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException
    {
        if(logger.isDebugEnabled())
        {
            logger.debug("createDataType(sqlType={}, sqlTypeName={}) - start", String.valueOf(sqlType), sqlTypeName);
        }

        if (sqlTypeName.equals("BOOLEAN"))
        {
            return DataType.BOOLEAN;
        } else if ("UUID".equals(sqlTypeName))
        {
            return DataType.NVARCHAR;
        } else if ("JSON".equals(sqlTypeName))
        {
            return new H2JsonType();
        }

        return super.createDataType(sqlType, sqlTypeName);
    }
}
