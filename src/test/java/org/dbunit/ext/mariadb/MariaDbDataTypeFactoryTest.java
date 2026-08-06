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
package org.dbunit.ext.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.ext.mysql.MySqlDataTypeFactoryTest;
import org.junit.jupiter.api.Test;

/**
 * @author Jeff Jensen
 * @since 3.4.1
 */
class MariaDbDataTypeFactoryTest extends MySqlDataTypeFactoryTest
{

    @Override
    public IDataTypeFactory createFactory() throws Exception
    {
        return new MariaDbDataTypeFactory();
    }

    @Test
    void testGetValidDbProducts_returnsMariadb()
    {
        final MariaDbDataTypeFactory factory = new MariaDbDataTypeFactory();

        assertThat(factory.getValidDbProducts()).as("valid db products.").containsExactly("mariadb");
    }

    @Test
    void testCreateUuidDataType_withUuidTypeName_returnsVarcharDataType() throws Exception
    {
        final DataType actual =
                createFactory().createDataType(Types.OTHER, MariaDbDataTypeFactory.SQL_TYPE_NAME_UUID);
        final DataType expected = DataType.VARCHAR;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateUuidLowerCaseDataType_withLowercaseUuidTypeName_returnsVarcharDataType() throws Exception
    {
        // MariaDB Connector/J's ResultSetMetaData reports this type name in
        // lowercase, unlike DatabaseMetaData#getColumns() which reports it
        // uppercase - both must work.
        final DataType actual = createFactory().createDataType(Types.OTHER, "uuid");
        final DataType expected = DataType.VARCHAR;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateInet4DataType_withInet4TypeName_returnsVarcharDataType() throws Exception
    {
        final DataType actual =
                createFactory().createDataType(Types.OTHER, MariaDbDataTypeFactory.SQL_TYPE_NAME_INET4);
        final DataType expected = DataType.VARCHAR;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateInet4LowerCaseDataType_withLowercaseInet4TypeName_returnsVarcharDataType() throws Exception
    {
        final DataType actual = createFactory().createDataType(Types.OTHER, "inet4");
        final DataType expected = DataType.VARCHAR;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateInet6DataType_withInet6TypeName_returnsVarcharDataType() throws Exception
    {
        final DataType actual =
                createFactory().createDataType(Types.OTHER, MariaDbDataTypeFactory.SQL_TYPE_NAME_INET6);
        final DataType expected = DataType.VARCHAR;
        assertThat(actual).as("type").isSameAs(expected);
    }

    @Test
    void testCreateInet6LowerCaseDataType_withLowercaseInet6TypeName_returnsVarcharDataType() throws Exception
    {
        final DataType actual = createFactory().createDataType(Types.OTHER, "inet6");
        final DataType expected = DataType.VARCHAR;
        assertThat(actual).as("type").isSameAs(expected);
    }

}
