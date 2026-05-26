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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BatchStatement}.
 */
class BatchStatementTest
{
    private IDatabaseConnection dbConn;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception
    {
        dbConn = InMemoryDatabaseConnection.create();
        conn = dbConn.getConnection();
        conn.createStatement().execute(
                "CREATE TABLE TEST_BATCH (ID INTEGER, NAME VARCHAR(50))");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        dbConn.close();
    }

    @Test
    void testExecuteBatch_withNoStatements_returnsZero() throws Exception
    {
        final BatchStatement statement = new BatchStatement(conn);
        final int result = statement.executeBatch();
        assertThat(result).as("empty batch result.").isEqualTo(0);
        statement.close();
    }

    @Test
    void testAddBatch_andExecuteBatch_withSingleInsert_returnsOne() throws Exception
    {
        final BatchStatement statement = new BatchStatement(conn);
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (1, 'Alice')");
        final int result = statement.executeBatch();
        assertThat(result).as("single insert result.").isEqualTo(1);
        statement.close();
    }

    @Test
    void testAddBatch_andExecuteBatch_withMultipleInserts_returnsSumOfUpdates() throws Exception
    {
        final BatchStatement statement = new BatchStatement(conn);
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (1, 'Alice')");
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (2, 'Bob')");
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (3, 'Carol')");
        final int result = statement.executeBatch();
        assertThat(result).as("multi-insert batch result.").isEqualTo(3);
        statement.close();
    }

    @Test
    void testClearBatch_withPendingStatements_preventsThem() throws Exception
    {
        final BatchStatement statement = new BatchStatement(conn);
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (1, 'Alice')");
        statement.clearBatch();
        final int result = statement.executeBatch();
        assertThat(result).as("result after clear.").isEqualTo(0);
        statement.close();
    }

    @Test
    void testClose_withOpenStatement_preventsFurtherAddBatch() throws Exception
    {
        final BatchStatement statement = new BatchStatement(conn);
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (1, 'Alice')");
        statement.close();
        assertThatThrownBy(() -> statement.addBatch("INSERT INTO TEST_BATCH VALUES (2, 'Bob')"))
                .as("add after close.")
                .isInstanceOf(SQLException.class);
    }

    @Test
    void testAddBatch_andExecuteBatch_withMixedDmlTypes_returnsSumOfAffectedRows() throws Exception
    {
        conn.createStatement()
                .execute("INSERT INTO TEST_BATCH VALUES (99, 'ToDelete')");
        final BatchStatement statement = new BatchStatement(conn);
        statement.addBatch("INSERT INTO TEST_BATCH VALUES (1, 'Alice')");
        statement.addBatch("UPDATE TEST_BATCH SET NAME = 'Updated' WHERE ID = 99");
        statement.addBatch("DELETE FROM TEST_BATCH WHERE ID = 99");
        final int result = statement.executeBatch();
        assertThat(result).as("mixed dml batch result.").isEqualTo(3);
        statement.close();
    }
}
