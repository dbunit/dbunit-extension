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

package org.dbunit.database.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TablesDependencyHelper}'s private
 * {@code normalizeToStoredCase(IDatabaseConnection, String[])}, invoked via
 * reflection since driving it through a public entry point would require
 * mocking the whole {@link DepthFirstSearch}/callback machinery for no
 * benefit - the method's own contract is narrow and self-contained.
 *
 * @since 3.4.0
 */
@ExtendWith(MockitoExtension.class)
class TablesDependencyHelperTest
{
    @Mock
    private IDatabaseConnection connection;

    @Mock
    private Connection jdbcConnection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Test
    void testNormalizeToStoredCase_withTurkishDefaultLocaleAndLowerCaseIdentifierDatabase_normalizesAsciiCorrectly()
            throws Exception
    {
        final Locale original = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try
        {
            when(connection.getConnection()).thenReturn(jdbcConnection);
            when(jdbcConnection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.storesLowerCaseIdentifiers())
                    .thenReturn(true);

            final String[] actual = invokeNormalizeToStoredCase(connection,
                    new String[] {"TABLE_ID"});

            assertThat(actual)
                    .as("normalizeToStoredCase() must use Locale.ENGLISH so a"
                            + " Turkish default locale does not turn 'I' into"
                            + " a dotless 'ı', which would desync the"
                            + " normalized name from the lower-case FK"
                            + " metadata names returned by the driver.")
                    .containsExactly("table_id");
        } finally
        {
            Locale.setDefault(original);
        }
    }

    @Test
    void testNormalizeToStoredCase_withUpperCaseIdentifierDatabase_returnsOriginalArray()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.storesLowerCaseIdentifiers()).thenReturn(false);
        final String[] tableNames = {"TABLE_ID"};

        final String[] actual =
                invokeNormalizeToStoredCase(connection, tableNames);

        assertThat(actual)
                .as("A database that does not store lower-case identifiers"
                        + " must not have its names normalized at all.")
                .isSameAs(tableNames);
    }

    private static String[] invokeNormalizeToStoredCase(
            final IDatabaseConnection connection, final String[] tableNames)
            throws Exception
    {
        final Method method = TablesDependencyHelper.class.getDeclaredMethod(
                "normalizeToStoredCase", IDatabaseConnection.class,
                String[].class);
        method.setAccessible(true);
        return (String[]) method.invoke(null, connection, tableNames);
    }
}
