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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataTypeFactoryTest;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Aug 13, 2003
 * @version $Revision$
 */
class OracleDataTypeFactoryTest extends AbstractDataTypeFactoryTest
{

    @Override
    public IDataTypeFactory createFactory() throws Exception
    {
        return new OracleDataTypeFactory();
    }

    @Override
    @Test
    public void testCreateBlobDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "BLOB";

        final DataType expected = OracleDataTypeFactory.ORACLE_BLOB;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Override
    @Test
    public void testCreateClobDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "CLOB";

        final DataType expected = OracleDataTypeFactory.ORACLE_CLOB;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateNClobDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "NCLOB";

        final DataType expected = OracleDataTypeFactory.ORACLE_NCLOB;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateLongRawDataType() throws Exception
    {
        final int sqlType = Types.LONGVARBINARY;
        final String sqlTypeName = "LONG RAW";

        final DataType expected = OracleDataTypeFactory.LONG_RAW;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateTimestampDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "TIMESTAMP(6)";

        final DataType expected = DataType.TIMESTAMP;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Override
    @Test
    public void testCreateDateDataType() throws Exception
    {
        final int sqlType = Types.DATE;
        final String sqlTypeName = "DATE";

        final DataType expected = DataType.TIMESTAMP;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateNChar2DataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "NCHAR2";

        final DataType expected = DataType.CHAR;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateNVarChar2DataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "NVARCHAR2";

        final DataType expected = DataType.VARCHAR;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateFloatDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "FLOAT";

        final DataType expected = DataType.FLOAT;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateBinaryDoubleDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "BINARY_DOUBLE";

        final DataType expected = DataType.DOUBLE;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateBinaryFloatDataType() throws Exception
    {
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "BINARY_FLOAT";

        final DataType expected = DataType.FLOAT;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    public void testCreateSdoGeometryDataType() throws Exception
    {
        final int sqlType = Types.STRUCT;
        final String sqlTypeName = "SDO_GEOMETRY";

        final DataType expected =
                OracleDataTypeFactory.ORACLE_SDO_GEOMETRY_TYPE;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

}
