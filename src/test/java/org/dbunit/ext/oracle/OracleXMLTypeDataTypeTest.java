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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLXML;

import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OracleXMLTypeDataType} using mock objects, focused on
 * the UTF-8 charset used to move between the {@code byte[]} DataType value
 * and the underlying {@link SQLXML}'s {@code String}.
 *
 * @since 3.4.0
 */
@ExtendWith(MockitoExtension.class)
class OracleXMLTypeDataTypeTest
{
    private static final DataType THIS_TYPE =
            OracleDataTypeFactory.ORACLE_XMLTYPE;

    private static final String NON_ASCII_XML =
            "<row attr=\"café\">élève</row>";

    @Mock
    private OracleResultSet mockedOracleResultSet;

    @Mock
    private SQLXML mockedSqlXml;

    @Mock
    private OraclePreparedStatement mockedOraclePreparedStatement;

    @Mock
    private Connection mockedConnection;

    @Mock
    private PreparedStatement mockedPreparedStatement;

    @Test
    void testGetSqlValue_withNonAsciiContent_decodesSqlXmlStringAsUtf8Bytes()
            throws Exception
    {
        when(mockedOracleResultSet.unwrap(OracleResultSet.class))
                .thenReturn(mockedOracleResultSet);
        when(mockedOracleResultSet.getSQLXML(1)).thenReturn(mockedSqlXml);
        when(mockedSqlXml.getString()).thenReturn(NON_ASCII_XML);

        final Object actual =
                THIS_TYPE.getSqlValue(1, mockedOracleResultSet);

        assertThat(actual)
                .as("getSqlValue() must encode the SQLXML's String with UTF-8"
                        + " so non-ASCII characters round-trip, matching"
                        + " setSqlValue()'s decoding of the same bytes.")
                .isEqualTo(NON_ASCII_XML.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testGetSqlValue_withNullSqlXml_returnsNull() throws Exception
    {
        when(mockedOracleResultSet.unwrap(OracleResultSet.class))
                .thenReturn(mockedOracleResultSet);
        when(mockedOracleResultSet.getSQLXML(1)).thenReturn(null);

        final Object actual = THIS_TYPE.getSqlValue(1, mockedOracleResultSet);

        assertThat(actual).as("A NULL SQLXML column must type-cast to null.")
                .isNull();
    }

    @Test
    void testSetSqlValue_withNonAsciiContent_encodesUtf8BytesAsSqlXmlString()
            throws Exception
    {
        final byte[] value = NON_ASCII_XML.getBytes(StandardCharsets.UTF_8);
        when(mockedPreparedStatement.unwrap(OraclePreparedStatement.class))
                .thenReturn(mockedOraclePreparedStatement);
        when(mockedOraclePreparedStatement.getConnection())
                .thenReturn(mockedConnection);
        when(mockedConnection.createSQLXML()).thenReturn(mockedSqlXml);

        THIS_TYPE.setSqlValue(value, 1, mockedPreparedStatement);

        verify(mockedSqlXml)
                .setString(NON_ASCII_XML);
        verify(mockedOraclePreparedStatement).setSQLXML(1, mockedSqlXml);
    }

    @Test
    void testGetSqlTypeName_returnsOracleXmlTypeName()
    {
        assertThat(THIS_TYPE.getSqlTypeName()).isEqualTo("SYS.XMLTYPE");
    }
}
