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
 * Unit tests for {@link CompoundStatement}.
 *
 * <p>{@link CompoundStatement} concatenates all added SQL strings with semicolons
 * and executes the combined string as a single JDBC {@code executeUpdate} call.
 */
class CompoundStatementTest extends AbstractStatementTest
{
    @Override
    protected String createTestTableDdl()
    {
        return "CREATE TABLE TEST_COMPOUND (ID INTEGER, NAME VARCHAR(50))";
    }

    @Test
    void testAddBatch_andExecuteBatch_withSingleStatement_executesIt() throws Exception
    {
        final CompoundStatement statement = new CompoundStatement(conn);
        statement.addBatch("INSERT INTO TEST_COMPOUND VALUES (1, 'Alice')");
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_COMPOUND WHERE ID = 1");
        assertThat(rs.next()).as("row inserted.").isTrue();
        assertThat(rs.getString("NAME")).as("name value.").isEqualTo("Alice");
    }

    @Test
    void testClearBatch_withPendingStatements_resetsBuffer() throws Exception
    {
        final CompoundStatement statement = new CompoundStatement(conn);
        statement.addBatch("INSERT INTO TEST_COMPOUND VALUES (1, 'Alice')");
        statement.clearBatch();

        statement.addBatch("INSERT INTO TEST_COMPOUND VALUES (2, 'Bob')");
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet countRs =
                check.executeQuery("SELECT COUNT(*) FROM TEST_COMPOUND");
        countRs.next();
        assertThat(countRs.getInt(1)).as("only one row after clear.").isEqualTo(1);

        final ResultSet bobRs =
                check.executeQuery("SELECT ID FROM TEST_COMPOUND WHERE ID = 2");
        assertThat(bobRs.next()).as("bob row exists.").isTrue();
    }

    @Test
    void testClearBatch_afterExecute_allowsNewBatch() throws Exception
    {
        final CompoundStatement statement = new CompoundStatement(conn);
        statement.addBatch("INSERT INTO TEST_COMPOUND VALUES (1, 'First')");
        statement.executeBatch();
        statement.clearBatch();

        statement.addBatch("INSERT INTO TEST_COMPOUND VALUES (2, 'Second')");
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT COUNT(*) FROM TEST_COMPOUND");
        rs.next();
        assertThat(rs.getInt(1)).as("two rows after two executions.").isEqualTo(2);
    }

    @Test
    void testAddBatch_appendsSemicolonSeparator_producesValidSql() throws Exception
    {
        // CompoundStatement concatenates with ";". Test that a single-statement
        // string ending with ";" is accepted by the driver and inserts correctly.
        final CompoundStatement statement = new CompoundStatement(conn);
        statement.addBatch("INSERT INTO TEST_COMPOUND VALUES (10, 'X')");
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT ID FROM TEST_COMPOUND WHERE ID = 10");
        assertThat(rs.next()).as("row 10 exists.").isTrue();
    }
}
