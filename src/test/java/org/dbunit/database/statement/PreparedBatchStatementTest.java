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

import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PreparedBatchStatement}.
 *
 * <p>{@link PreparedBatchStatement} uses JDBC batch support on a
 * {@link java.sql.PreparedStatement}, accumulating rows and flushing them with
 * {@code executeBatch}.
 */
class PreparedBatchStatementTest extends AbstractStatementTest
{
    @Override
    protected String createTestTableDdl()
    {
        return "CREATE TABLE TEST_PREP (ID INTEGER, NAME VARCHAR(50))";
    }

    @Test
    void testAddValueAndAddBatch_andExecuteBatch_withSingleRow_returnsOne() throws Exception
    {
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);

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
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);

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
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);

        statement.addValue(Integer.valueOf(5), DataType.INTEGER);
        statement.addValue(null, DataType.VARCHAR);
        statement.addBatch();
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_PREP WHERE ID = 5");
        assertThat(rs.next()).as("row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("null name.").isNull();
    }

    @Test
    void testAddValue_withNoValue_setsNullInDatabase() throws Exception
    {
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);

        statement.addValue(Integer.valueOf(6), DataType.INTEGER);
        statement.addValue(ITable.NO_VALUE, DataType.VARCHAR);
        statement.addBatch();
        statement.executeBatch();
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT NAME FROM TEST_PREP WHERE ID = 6");
        assertThat(rs.next()).as("row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("no-value becomes null.").isNull();
    }

    @Test
    void testClearBatch_withPendingRows_preventsTheirExecution() throws Exception
    {
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);

        statement.addValue(Integer.valueOf(1), DataType.INTEGER);
        statement.addValue("Alice", DataType.VARCHAR);
        statement.addBatch();

        statement.clearBatch();

        final int result = statement.executeBatch();
        assertThat(result).as("result after clear.").isEqualTo(0);
        statement.close();

        final Statement check = conn.createStatement();
        final ResultSet rs =
                check.executeQuery("SELECT COUNT(*) FROM TEST_PREP");
        rs.next();
        assertThat(rs.getInt(1)).as("no rows after clear.").isEqualTo(0);
    }

    @Test
    void testExecuteBatch_withNoBatchAdded_returnsZero() throws Exception
    {
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);
        final int result = statement.executeBatch();
        assertThat(result).as("empty batch result.").isEqualTo(0);
        statement.close();
    }

    @Test
    void testAddBatch_resetsParameterIndex_forNextRow() throws Exception
    {
        final String sql = "INSERT INTO TEST_PREP VALUES (?, ?)";
        final PreparedBatchStatement statement = new PreparedBatchStatement(sql, conn);

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
                check.executeQuery("SELECT NAME FROM TEST_PREP WHERE ID = 20");
        assertThat(rs.next()).as("second row exists.").isTrue();
        assertThat(rs.getString("NAME")).as("second row name.").isEqualTo("Second");
    }
}
