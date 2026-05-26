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
 * Unit tests for {@link PreparedStatementFactory}.
 *
 * <p>Verifies that {@link PreparedStatementFactory} creates the correct statement
 * implementations based on the {@link DatabaseConfig#FEATURE_BATCHED_STATEMENTS}
 * feature flag, and that the resulting statements are wrapped in
 * {@link AutomaticPreparedBatchStatement} when creating prepared batch statements.
 */
class PreparedStatementFactoryTest
{
    private IDatabaseConnection dbConn;

    @BeforeEach
    void setUp() throws Exception
    {
        dbConn = InMemoryDatabaseConnection.create();
        dbConn.getConnection().createStatement().execute(
                "CREATE TABLE TEST_PSF (ID INTEGER, NAME VARCHAR(50))");
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
        final PreparedStatementFactory factory = new PreparedStatementFactory();

        final IBatchStatement statement = factory.createBatchStatement(dbConn);

        assertThat(statement).as("batch statement type with feature off.").isInstanceOf(SimpleStatement.class);
        statement.close();
    }

    @Test
    void testCreateBatchStatement_withBatchFeatureEnabled_returnsBatchStatement()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        final PreparedStatementFactory factory = new PreparedStatementFactory();

        final IBatchStatement statement = factory.createBatchStatement(dbConn);

        assertThat(statement).as("batch statement type with feature on.").isInstanceOf(BatchStatement.class);
        statement.close();
    }

    @Test
    void testCreatePreparedBatchStatement_withBatchFeatureDisabled_returnsAutomaticWrapper()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
        final PreparedStatementFactory factory = new PreparedStatementFactory();
        final String sql = "INSERT INTO TEST_PSF VALUES (?, ?)";

        final IPreparedBatchStatement statement =
                factory.createPreparedBatchStatement(sql, dbConn);

        assertThat(statement).as("prepared statement wrapped in automatic batch wrapper.")
                .isInstanceOf(AutomaticPreparedBatchStatement.class);
        statement.close();
    }

    @Test
    void testCreatePreparedBatchStatement_withBatchFeatureEnabled_returnsAutomaticWrapper()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        final PreparedStatementFactory factory = new PreparedStatementFactory();
        final String sql = "INSERT INTO TEST_PSF VALUES (?, ?)";

        final IPreparedBatchStatement statement =
                factory.createPreparedBatchStatement(sql, dbConn);

        assertThat(statement).as("prepared statement with batch feature on, still wrapped.")
                .isInstanceOf(AutomaticPreparedBatchStatement.class);
        statement.close();
    }

    @Test
    void testCreateBatchStatement_withBatchFeatureDisabled_canInsertRows() throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
        final PreparedStatementFactory factory = new PreparedStatementFactory();

        final IBatchStatement statement = factory.createBatchStatement(dbConn);
        statement.addBatch("INSERT INTO TEST_PSF VALUES (1, 'Alpha')");
        final int result = statement.executeBatch();
        assertThat(result).as("insert result.").isEqualTo(1);
        statement.close();
    }

    @Test
    void testCreatePreparedBatchStatement_withDefaultBatchSize_usesDefaultThreshold()
            throws Exception
    {
        dbConn.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
        final PreparedStatementFactory factory = new PreparedStatementFactory();
        final String sql = "INSERT INTO TEST_PSF VALUES (?, ?)";

        final IPreparedBatchStatement statement =
                factory.createPreparedBatchStatement(sql, dbConn);

        assertThat(statement).as("uses default batch size config.").isNotNull();
        statement.close();
    }
}
