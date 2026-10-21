/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.database.rowcount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

class CurrentSchemaConnectionTest
{
    @Test
    void testGetSchema_viewNamingASchema_returnsTheSchemaItWasGiven()
    {
        final CurrentSchemaConnection view =
                new CurrentSchemaConnection(mock(IDatabaseConnection.class), "ACCOUNTING");

        assertThat(view.getSchema()).as("The view names the schema it was given.")
                .isEqualTo("ACCOUNTING");
    }

    @Test
    void testGetConfig_viewOfAConnection_returnsTheConfigOfTheConnectionItViews()
    {
        final DatabaseConfig config = new DatabaseConfig();
        final IDatabaseConnection viewed = mock(IDatabaseConnection.class);
        when(viewed.getConfig()).thenReturn(config);
        final CurrentSchemaConnection view = new CurrentSchemaConnection(viewed, "ACCOUNTING");

        assertThat(view.getConfig())
                .as("Every setting of the caller's connection, the escape pattern and the"
                        + " metadata handler among them, must still apply through the view.")
                .isSameAs(config);
    }

    @Test
    void testGetConnection_viewOfAConnection_returnsTheJdbcConnectionOfTheConnectionItViews()
            throws Exception
    {
        final Connection jdbcConnection = mock(Connection.class);
        final IDatabaseConnection viewed = mock(IDatabaseConnection.class);
        when(viewed.getConnection()).thenReturn(jdbcConnection);
        final CurrentSchemaConnection view = new CurrentSchemaConnection(viewed, "ACCOUNTING");

        assertThat(view.getConnection())
                .as("The view reads through the JDBC connection of the connection it views.")
                .isSameAs(jdbcConnection);
    }

    @Test
    void testClose_viewOfAConnection_leavesTheConnectionItViewsOpen() throws Exception
    {
        final IDatabaseConnection viewed = mock(IDatabaseConnection.class);
        final CurrentSchemaConnection view = new CurrentSchemaConnection(viewed, "ACCOUNTING");

        assertThatCode(view::close).as("Closing a view is not an error.")
                .doesNotThrowAnyException();

        verify(viewed, never()).close();
    }
}
