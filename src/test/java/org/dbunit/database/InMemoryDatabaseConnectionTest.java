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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryDatabaseConnection}.
 */
class InMemoryDatabaseConnectionTest
{
    @Test
    void testCreate_withNoArgs_returnsOpenConnection() throws Exception
    {
        final IDatabaseConnection conn = InMemoryDatabaseConnection.create();
        assertThat(conn.getConnection().isClosed()).as("connection open").isFalse();
        conn.close();
    }

    @Test
    void testCreate_withSchema_returnsConnectionWithSchema() throws Exception
    {
        final IDatabaseConnection conn =
                InMemoryDatabaseConnection.create("PUBLIC");
        assertThat(conn.getSchema()).as("schema").isEqualTo("PUBLIC");
        conn.close();
    }

    @Test
    void testCreate_calledTwice_returnsTwoIsolatedConnections() throws Exception
    {
        final IDatabaseConnection conn1 = InMemoryDatabaseConnection.create();
        final IDatabaseConnection conn2 = InMemoryDatabaseConnection.create();
        assertThat(conn1.getConnection()).as("distinct connections")
                .isNotSameAs(conn2.getConnection());
        conn1.close();
        conn2.close();
    }
}
