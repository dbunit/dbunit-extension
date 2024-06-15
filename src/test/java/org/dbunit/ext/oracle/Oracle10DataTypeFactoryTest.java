/*
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @since 2.3.0
 * @version $Revision$
 */
public class Oracle10DataTypeFactoryTest extends OracleDataTypeFactoryTest
{

    @Override
    public IDataTypeFactory createFactory() throws Exception
    {
        return new Oracle10DataTypeFactory();
    }

    @Override
    @Test
    public void testCreateBlobDataType() throws Exception
    {
        final int sqlType = Types.BLOB;
        final String sqlTypeName = "BLOB";

        final DataType expected = Oracle10DataTypeFactory.BLOB_AS_STREAM;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Override
    @Test
    public void testCreateClobDataType() throws Exception
    {
        final int sqlType = Types.CLOB;
        final String sqlTypeName = "CLOB";

        final DataType expected = Oracle10DataTypeFactory.CLOB_AS_STRING;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }
}