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
package org.dbunit.ext.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataTypeFactoryTest;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Sep 3, 2003
 * @version $Revision$
 */
class MySqlDataTypeFactoryTest extends AbstractDataTypeFactoryTest
{

    @Override
    public IDataTypeFactory createFactory() throws Exception
    {
        return new MySqlDataTypeFactory();
    }

    @Test
    void testCreateLongtextDataType() throws Exception
    {
        final DataType actual =
                createFactory().createDataType(Types.OTHER, "longtext");
        final DataType expected = DataType.CLOB;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateLongtextUpperCaseDataType() throws Exception
    {
        // MySql 5 reports the datatypes in uppercase, so this here must also
        // work
        final DataType actual =
                createFactory().createDataType(Types.OTHER, "LONGTEXT");
        final DataType expected = DataType.CLOB;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateBooleanDataType() throws Exception
    {
        final DataType actual =
                createFactory().createDataType(Types.OTHER, "bit");
        final DataType expected = DataType.BOOLEAN;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateBooleanUpperCaseDataType() throws Exception
    {
        // MySql 5 reports the datatypes in uppercase, so this here must also
        // work
        final DataType actual =
                createFactory().createDataType(Types.OTHER, "BIT");
        final DataType expected = DataType.BOOLEAN;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreatePointDataType() throws Exception
    {
        final DataType actual =
                createFactory().createDataType(Types.OTHER, "point");
        final DataType expected = DataType.BINARY;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreatePointUpperCaseDataType() throws Exception
    {
        // MySql 5 reports the datatypes in uppercase, so this here must also
        // work
        final DataType actual =
                createFactory().createDataType(Types.OTHER, "POINT");
        final DataType expected = DataType.BINARY;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateTinyintUnsignedDatatype() throws Exception
    {
        final int sqlType = Types.BIT; // MySqlConnector/J reports
        // "TINYINT UNSIGNED" columns as
        // SQL type "BIT".
        final String sqlTypeName =
                MySqlDataTypeFactory.SQL_TYPE_NAME_TINYINT_UNSIGNED;

        final DataType expected = DataType.TINYINT;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateIntegerUnsignedDatatype() throws Exception
    {
        final int sqlType = Types.INTEGER;
        final String sqlTypeName =
                "INTEGER" + MySqlDataTypeFactory.UNSIGNED_SUFFIX;

        final DataType expected = DataType.BIGINT;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

}
