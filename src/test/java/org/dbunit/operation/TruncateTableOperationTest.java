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

package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TruncateTableOperation} using mock objects.
 *
 * @author Manuel Laflamme
 * @since Apr 13, 2003
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
class TruncateTableOperationTest extends DeleteAllOperationTest
{
    @Mock
    private IDatabaseConnection connection;

    @Mock
    private Connection jdbcConnection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Override
    protected DatabaseOperation getDeleteAllOperation()
    {
        return new TruncateTableOperation();
    }

    @Override
    protected String getExpectedStament(final String tableName)
    {
        return "truncate table " + tableName;
    }

    @Test
    void testGetDeleteAllCommandSuffix_withTurkishDefaultLocaleAndDb2ProductName_appendsImmediate()
            throws Exception
    {
        final Locale original = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try
        {
            when(connection.getConnection()).thenReturn(jdbcConnection);
            when(jdbcConnection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName())
                    .thenReturn("DB2/NT");

            final String actual = new TruncateTableOperation()
                    .getDeleteAllCommandSuffix(connection);

            // "db2" itself contains no I/i, so a Turkish default locale
            // cannot actually break this specific match - this is a
            // consistency/coverage test for the Locale.ENGLISH pin, not a
            // demonstrated-bug regression test.
            assertThat(actual)
                    .as("DB2 product name must still get the IMMEDIATE"
                            + " suffix under a Turkish default locale.")
                    .isEqualTo(" IMMEDIATE");
        } finally
        {
            Locale.setDefault(original);
        }
    }
}
