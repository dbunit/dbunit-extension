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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StatementFactory}.
 *
 * <p>Verifies that {@link StatementFactory} creates the correct {@link IBatchStatement}
 * and {@link IPreparedBatchStatement} implementations depending on whether the
 * {@link DatabaseConfig#FEATURE_BATCHED_STATEMENTS} feature is enabled.
 */
class StatementFactoryTest
{
    private IDatabaseConnection dbConn;

    @BeforeEach
    void setUp() throws Exception
    {
        dbConn = InMemoryDatabaseConnection.create();
        dbConn.getConnection().createStatement().execute(
                "CREATE TABLE TEST_SF (ID INTEGER, NAME VARCHAR(50))");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        dbConn.close();
    }

    @Test
    void testCreateBatchStatement_withBatchFeatureDisabled_returnsSimpleStatement()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
        final StatementFactory factory = new StatementFactory();

        final IBatchStatement statement = factory.createBatchStatement(dbConn);

        assertThat(statement).as("batch statement type with feature off.").isInstanceOf(SimpleStatement.class);
        statement.close();
    }

    @Test
    void testCreateBatchStatement_withBatchFeatureEnabled_returnsBatchStatement()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        final StatementFactory factory = new StatementFactory();

        final IBatchStatement statement = factory.createBatchStatement(dbConn);

        assertThat(statement).as("batch statement type with feature on.").isInstanceOf(BatchStatement.class);
        statement.close();
    }

    @Test
    void testCreatePreparedBatchStatement_withBatchFeatureDisabled_returnsBatchStatementDecorator()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
        final StatementFactory factory = new StatementFactory();
        final String sql = "INSERT INTO TEST_SF VALUES (?, ?)";

        final IPreparedBatchStatement statement =
                factory.createPreparedBatchStatement(sql, dbConn);

        assertThat(statement).as("prepared statement type with feature off.")
                .isInstanceOf(BatchStatementDecorator.class);
        statement.close();
    }

    @Test
    void testCreatePreparedBatchStatement_withBatchFeatureEnabled_returnsBatchStatementDecorator()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        final StatementFactory factory = new StatementFactory();
        final String sql = "INSERT INTO TEST_SF VALUES (?, ?)";

        final IPreparedBatchStatement statement =
                factory.createPreparedBatchStatement(sql, dbConn);

        assertThat(statement).as("prepared statement type with feature on.")
                .isInstanceOf(BatchStatementDecorator.class);
        statement.close();
    }

    @Test
    void testCreateBatchStatement_withBatchFeatureDisabled_canInsertRows() throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
        final StatementFactory factory = new StatementFactory();

        final IBatchStatement statement = factory.createBatchStatement(dbConn);
        statement.addBatch("INSERT INTO TEST_SF VALUES (1, 'Alpha')");
        final int result = statement.executeBatch();
        assertThat(result).as("insert result.").isEqualTo(1);
        statement.close();
    }
}
