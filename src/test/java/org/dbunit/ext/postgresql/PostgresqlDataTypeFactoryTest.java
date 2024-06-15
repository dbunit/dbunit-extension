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
package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IntegerDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 *
 * @author Jarvis Cochrane (jarvis@cochrane.com.au)
 * @author Roberto Lo Giacco (rlogiacco@users.sourceforge.ent)
 * @author Martin Gollogly (zemertz@gmail.com)
 * @since 2.4.5 (Apr 27, 2009)
 */
@EnabledIfEnvironmentVariable(named = "MAVEN_CMD_LINE_ARGS", matches = "(.*)postgresql(.*)")
class PostgresqlDataTypeFactoryTest
{

    /**
     * Test of createDataType method, of class PostgresqlDataTypeFactory.
     */
    @Test
    void testCreateUuidType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory();

        // Test UUID type created properly
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "uuid";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(UuidType.class);
    }

    @Test
    void testCreateIntervalType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory();

        // Test interval type created properly
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "interval";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(IntervalType.class);
    }

    @Test
    void testCreateInetType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory();

        // Test inet type created properly
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "inet";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(InetType.class);
    }

    @Test
    void testCreateCitextType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory();

        // Test CITEXT type created properly
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "citext";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(CitextType.class);
    }

    @Test
    void testCreateEnumType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory()
                {
                    @Override
                    public boolean isEnumType(final String sqlTypeName)
                    {
                        if (sqlTypeName.equalsIgnoreCase("abc_enum"))
                        {
                            return true;
                        }
                        return false;
                    }
                };

        // Test Enum type created properly
        final int sqlType = Types.OTHER;
        final String sqlTypeName = "abc_enum";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(GenericEnumType.class);
        assertThat(((GenericEnumType) result).getSqlTypeName())
                .isEqualTo("abc_enum");
    }

    @Test
    void testCreateDefaultType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory();

        final int sqlType = Types.INTEGER;
        final String sqlTypeName = "int";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(IntegerDataType.class);
    }

    @Test
    void testPostgreSQLOidType() throws Exception
    {

        final PostgresqlDataTypeFactory instance =
                new PostgresqlDataTypeFactory();

        final int sqlType = Types.BIGINT;
        final String sqlTypeName = "oid";

        final DataType result = instance.createDataType(sqlType, sqlTypeName);
        assertThat(result).isInstanceOf(PostgreSQLOidDataType.class);
    }
}
