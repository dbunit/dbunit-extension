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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SimplePreparedStatement}.
 *
 * <p>{@link SimplePreparedStatement} is the non-batch fallback for drivers that do
 * not support JDBC batch updates. It executes the prepared statement immediately on
 * {@link SimplePreparedStatement#addBatch()} and accumulates the update count.
 */
class SimplePreparedStatementTest
{
    private IDatabaseConnection dbConn;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception
    {
        dbConn = InMemoryDatabaseConnection.create();
        conn = dbConn.getConnection();
        conn.createStatement().execute(
                "CREATE TABLE TEST_SIMPLE_PREP (ID INTEGER, NAME VARCHAR(50))");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        dbConn.close();
    }

    @Test
    void testAddValueAndAddBatch_andExecuteBatch_withSingleRow_returnsOne() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(1), DataType.INTEGER);
        statement.addValue("Alice", DataType.VARCHAR);
        statement.addBatch();

        final int result = statement.executeBatch();
        assertThat(result).as("single row result.").isEqualTo(1);
        statement.close();
    }

    @Test
    void testAddValueAndAddBatch_andExecuteBatch_withMultipleRows_returnsSumOfUpdates()
            throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(1), DataType.INTEGER);
        statement.addValue("Alice", DataType.VARCHAR);
        statement.addBatch();

        statement.addValue(Integer.valueOf(2), DataType.INTEGER);
        statement.addValue("Bob", DataType.VARCHAR);
        statement.addBatch();

        final int result = statement.executeBatch();
        assertThat(result).as("two-row batch result.").isEqualTo(2);
        statement.close();
    }

    @Test
    void testAddValue_withNullValue_setsNullInDatabase() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(5), DataType.INTEGER);
        statement.addValue(null, DataType.VARCHAR);
        statement.addBatch();
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_SIMPLE_PREP WHERE ID = 5");
        assertThat(rs.next()).as("row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("null name.").isNull();
    }

    @Test
    void testAddValue_withNoValue_setsNullInDatabase() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(6), DataType.INTEGER);
        statement.addValue(ITable.NO_VALUE, DataType.VARCHAR);
        statement.addBatch();
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_SIMPLE_PREP WHERE ID = 6");
        assertThat(rs.next()).as("row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("no-value becomes null.").isNull();
    }

    @Test
    void testExecuteBatch_calledTwice_returnsCountPerCallAndResets() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(1), DataType.INTEGER);
        statement.addValue("Alice", DataType.VARCHAR);
        statement.addBatch();

        statement.addValue(Integer.valueOf(2), DataType.INTEGER);
        statement.addValue("Bob", DataType.VARCHAR);
        statement.addBatch();

        final int firstResult = statement.executeBatch();
        assertThat(firstResult).as("first execute result.").isEqualTo(2);

        statement.addValue(Integer.valueOf(3), DataType.INTEGER);
        statement.addValue("Carol", DataType.VARCHAR);
        statement.addBatch();

        final int secondResult = statement.executeBatch();
        assertThat(secondResult).as("second execute result reset after first.").isEqualTo(1);
        statement.close();
    }

    @Test
    void testClearBatch_afterAddBatch_makesSubsequentExecuteBatchReturnZero() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(1), DataType.INTEGER);
        statement.addValue("Alice", DataType.VARCHAR);
        statement.addBatch();

        statement.clearBatch();

        final int result = statement.executeBatch();
        assertThat(result).as("result after clear returns zero.").isEqualTo(0);
        statement.close();
    }

    @Test
    void testAddBatch_executesImmediately_dataIsVisibleBeforeExecuteBatch() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(42), DataType.INTEGER);
        statement.addValue("Immediate", DataType.VARCHAR);
        statement.addBatch();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT COUNT(*) FROM TEST_SIMPLE_PREP WHERE ID = 42");
        rs.next();
        assertThat(rs.getInt(1)).as("row inserted immediately on addBatch.").isEqualTo(1);

        statement.executeBatch();
        statement.close();
    }

    @Test
    void testAddBatch_resetsParameterIndex_forNextRow() throws Exception
    {
        final String sql = "INSERT INTO TEST_SIMPLE_PREP VALUES (?, ?)";
        final SimplePreparedStatement statement = new SimplePreparedStatement(sql, conn);

        statement.addValue(Integer.valueOf(10), DataType.INTEGER);
        statement.addValue("First", DataType.VARCHAR);
        statement.addBatch();

        statement.addValue(Integer.valueOf(20), DataType.INTEGER);
        statement.addValue("Second", DataType.VARCHAR);
        statement.addBatch();

        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_SIMPLE_PREP WHERE ID = 20");
        assertThat(rs.next()).as("second row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("second row name.").isEqualTo("Second");
    }
}
