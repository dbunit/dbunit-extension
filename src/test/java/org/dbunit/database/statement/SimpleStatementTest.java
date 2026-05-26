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

import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SimpleStatement}.
 *
 * <p>{@link SimpleStatement} executes each SQL statement individually rather than
 * submitting them as a true JDBC batch, making it the fallback when the driver
 * does not support batch updates.
 */
class SimpleStatementTest extends AbstractStatementTest
{
    @Override
    protected String createTestTableDdl()
    {
        return "CREATE TABLE TEST_SIMPLE (ID INTEGER, NAME VARCHAR(50))";
    }

    @Test
    void testExecuteBatch_withNoStatements_returnsZero() throws Exception
    {
        final SimpleStatement statement = new SimpleStatement(conn);
        final int result = statement.executeBatch();
        assertThat(result).as("empty batch result.").isEqualTo(0);
        statement.close();
    }

    @Test
    void testAddBatch_andExecuteBatch_withSingleInsert_returnsOne() throws Exception
    {
        final SimpleStatement statement = new SimpleStatement(conn);
        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (1, 'Alice')");
        final int result = statement.executeBatch();
        assertThat(result).as("single insert result.").isEqualTo(1);
        statement.close();
    }

    @Test
    void testAddBatch_andExecuteBatch_withMultipleInserts_returnsSumOfUpdates() throws Exception
    {
        final SimpleStatement statement = new SimpleStatement(conn);
        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (1, 'Alice')");
        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (2, 'Bob')");
        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (3, 'Carol')");
        final int result = statement.executeBatch();
        assertThat(result).as("multi-insert batch result.").isEqualTo(3);
        statement.close();
    }

    @Test
    void testClearBatch_withPendingStatements_preventsExecution() throws Exception
    {
        final SimpleStatement statement = new SimpleStatement(conn);
        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (1, 'Alice')");
        statement.clearBatch();
        final int result = statement.executeBatch();
        assertThat(result).as("result after clear.").isEqualTo(0);

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT COUNT(*) FROM TEST_SIMPLE");
        rs.next();
        assertThat(rs.getInt(1)).as("row count after clear and execute.").isEqualTo(0);
        statement.close();
    }

    @Test
    void testAddBatch_andExecuteBatch_actuallyPersistsData() throws Exception
    {
        final SimpleStatement statement = new SimpleStatement(conn);
        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (42, 'Test')");
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_SIMPLE WHERE ID = 42");
        assertThat(rs.next()).as("row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("row name.").isEqualTo("Test");
    }

    @Test
    void testAddBatch_andExecuteBatch_multipleRoundsWithClear_onlyLastRoundPersists()
            throws Exception
    {
        final SimpleStatement statement = new SimpleStatement(conn);

        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (1, 'First')");
        statement.clearBatch();

        statement.addBatch("INSERT INTO TEST_SIMPLE VALUES (2, 'Second')");
        final int result = statement.executeBatch();
        assertThat(result).as("second round result.").isEqualTo(1);
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT COUNT(*) FROM TEST_SIMPLE");
        rs.next();
        assertThat(rs.getInt(1)).as("only second-round row exists.").isEqualTo(1);
    }
}
