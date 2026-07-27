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
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import org.dbunit.TurkishDefaultLocale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OracleConnection}, focused on the
 * {@code Locale.ENGLISH}-pinned upper-casing of the schema name passed to the
 * constructor.
 *
 * @since 3.4.0
 */
@ExtendWith(MockitoExtension.class)
class OracleConnectionTest
{
    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private ResultSet schemasResultSet;

    @Mock
    private ResultSet catalogsResultSet;

    @Test
    @TurkishDefaultLocale
    void testConstructor_withTurkishDefaultLocaleAndLowerCaseSchema_uppercasesAsciiCorrectly()
            throws Exception
    {
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getIdentifierQuoteString())
                .thenReturn("\"");
        when(databaseMetaData.storesLowerCaseIdentifiers())
                .thenReturn(false);
        when(databaseMetaData.storesUpperCaseIdentifiers())
                .thenReturn(false);
        when(databaseMetaData.getSchemas()).thenReturn(schemasResultSet);
        when(schemasResultSet.next()).thenReturn(false);
        when(databaseMetaData.getCatalogs())
                .thenReturn(catalogsResultSet);
        when(catalogsResultSet.next()).thenReturn(false);

        // Under a Turkish default locale, an un-pinned toUpperCase()
        // would fold 'i' to a dotted capital 'İ' instead of plain ASCII
        // 'I', desyncing the schema from what the driver reports.
        final OracleConnection oracleConnection =
                new OracleConnection(connection, "id");

        assertThat(oracleConnection.getSchema())
                .as("OracleConnection's constructor must use"
                        + " Locale.ENGLISH so a Turkish default locale"
                        + " does not turn 'i' into a dotted capital"
                        + " 'İ'.")
                .isEqualTo("ID");
    }
}
