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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Aug 13, 2003
 * @version $Revision$
 */
public abstract class AbstractDataTypeFactoryTest
{

    public IDataTypeFactory createFactory() throws Exception
    {
        return new DefaultDataTypeFactory();
    }

    @Test
    void testCreateDataType() throws Exception
    {
        final DataType[] expectedTypes = new DataType[] {DataType.UNKNOWN,
                DataType.CHAR, DataType.VARCHAR, DataType.LONGVARCHAR,
                // DataType.CLOB,
                DataType.NUMERIC, DataType.DECIMAL, DataType.BOOLEAN,
                DataType.TINYINT, DataType.SMALLINT, DataType.INTEGER,
                DataType.BIGINT, DataType.REAL, DataType.FLOAT, DataType.DOUBLE,
                // DataType.DATE,
                DataType.TIME, DataType.TIMESTAMP, DataType.BINARY,
                DataType.VARBINARY, DataType.LONGVARBINARY,
                // DataType.BLOB,
        };

        final IDataTypeFactory factory = createFactory();
        for (int i = 0; i < expectedTypes.length; i++)
        {
            final DataType expected = expectedTypes[i];
            final DataType actual = factory
                    .createDataType(expected.getSqlType(), expected.toString());
            assertThat(actual).as("type").isSameAs(expected);
        }
    }

    @Test
    public void testCreateDateDataType() throws Exception
    {
        final int sqlType = Types.DATE;
        final String sqlTypeName = "DATE";

        final DataType expected = DataType.DATE;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateBlobDataType() throws Exception
    {
        final int sqlType = Types.BLOB;
        final String sqlTypeName = "BLOB";

        final DataType expected = DataType.BLOB;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);

    }

    @Test
    public void testCreateClobDataType() throws Exception
    {
        final int sqlType = Types.CLOB;
        final String sqlTypeName = "CLOB";

        final DataType expected = DataType.CLOB;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

}
