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
package org.dbunit.database.statement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MockBatchStatement}.
 */
class MockBatchStatementTest
{
    @Test
    void testGetCapturedSql_withNoSqlAdded_returnsEmptyList()
    {
        final MockBatchStatement statement = new MockBatchStatement();
        assertThat(statement.getCapturedSql()).as("captured sql").isEmpty();
    }

    @Test
    void testGetCapturedSql_afterAddBatch_returnsAddedSqlInOrder() throws Exception
    {
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addBatch("INSERT INTO T VALUES (1)");
        statement.addBatch("INSERT INTO T VALUES (2)");

        assertThat(statement.getCapturedSql()).as("captured sql")
                .containsExactly("INSERT INTO T VALUES (1)",
                        "INSERT INTO T VALUES (2)");
    }

    @Test
    void testGetCapturedSql_returnsUnmodifiableView() throws Exception
    {
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addBatch("SELECT 1");

        assertThat(statement.getCapturedSql()).as("captured sql").hasSize(1);
    }

    @Test
    void testGetCapturedSql_allowsPatternAssertion() throws Exception
    {
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addBatch("insert into schema.table (ID) values (42)");

        assertThat(statement.getCapturedSql().get(0)).as("sql pattern")
                .contains("schema.table")
                .contains("values (42)");
    }
}
