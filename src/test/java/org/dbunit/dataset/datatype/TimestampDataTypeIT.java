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
package org.dbunit.dataset.datatype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.TestFeature;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link TimestampDataType#setSqlValue} against whichever real database is
 * configured by the active Maven profile (see {@link DatabaseEnvironment}), proving issue #831's
 * fix for {@code TIMESTAMP WITH TIME ZONE} columns against a real JDBC driver, not just the H2
 * in-memory driver used by {@link TimestampDataTypeTest}.
 *
 * <p>
 * The zoned-column test skips itself (rather than failing) on any profile declaring
 * {@link TestFeature#TIMESTAMP_WITH_TIMEZONE} unsupported via
 * {@code dbunit.profile.unsupportedFeatures} - either because the SQL dialect rejects
 * {@code TIMESTAMP WITH TIME ZONE} column syntax outright (e.g. Derby, MySQL, SQL Server, which
 * has no equivalent type), or because the JDBC driver cannot reliably report
 * {@link Types#TIMESTAMP_WITH_TIMEZONE} from {@link java.sql.ParameterMetaData} for such a column
 * even though the database itself supports the type (a known limitation of Oracle's driver, not a
 * dbunit defect). Declaring the gap in the profile up front - rather than discovering it by
 * catching whatever exception a given driver happens to throw - means any other failure during
 * setup surfaces as a real test error instead of being silently treated as unsupported. The
 * plain-TIMESTAMP fallback test needs no such gating since every supported database accepts that
 * syntax, though it substitutes {@code DATETIME2} on SQL Server, whose {@code TIMESTAMP} type is
 * an unrelated, non-nullable row-versioning type rather than a date/time type.
 *
 * @since 3.4.0
 */
class TimestampDataTypeIT
{
    private static final String ZONED_TABLE = "TIMESTAMPTYPE_TZ_IT";

    private static final String PLAIN_TABLE = "TIMESTAMPTYPE_PLAIN_IT";

    private static final String DATASET_VALUE = "2026-07-22 15:00:00 -0200";

    private IDatabaseConnection connection;

    @BeforeEach
    void setUp() throws Exception
    {
        connection = DatabaseEnvironment.getInstance().getConnection();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (connection != null)
        {
            dropTableQuietly(ZONED_TABLE);
            dropTableQuietly(PLAIN_TABLE);
            connection.close();
            connection = null;
        }
    }

    private void dropTableQuietly(final String tableName)
    {
        try (Statement statement = connection.getConnection().createStatement())
        {
            statement.execute("DROP TABLE " + tableName);
        } catch (final SQLException e)
        {
            // Table may not exist: either a prior run never created it, or
            // the active profile does not support the column type under
            // test.
        }
    }

    @Test
    void testSetSqlValue_withOffsetDifferentFromLocalZone_writesCorrectInstantToTimestampWithTimeZoneColumn()
            throws Exception
    {
        assumeTrue(AbstractDatabaseIT.environmentHasFeature(TestFeature.TIMESTAMP_WITH_TIMEZONE),
                "Skipping: active database profile declares TIMESTAMP_WITH_TIMEZONE unsupported "
                        + "(see dbunit.profile.unsupportedFeatures).");

        final Connection jdbcConnection = connection.getConnection();
        dropTableQuietly(ZONED_TABLE);
        try (Statement statement = jdbcConnection.createStatement())
        {
            statement.execute("CREATE TABLE " + ZONED_TABLE
                    + " (ID INTEGER NOT NULL PRIMARY KEY, TIM TIMESTAMP WITH TIME ZONE)");
        }

        try (PreparedStatement insert = jdbcConnection
                .prepareStatement("INSERT INTO " + ZONED_TABLE + " VALUES (1, ?)"))
        {
            final int parameterType = insert.getParameterMetaData().getParameterType(1);
            assumeTrue(parameterType == Types.TIMESTAMP_WITH_TIMEZONE,
                    "Skipping: active database driver does not report TIMESTAMP_WITH_TIMEZONE "
                            + "parameter metadata for this column.");

            new TimestampDataType().setSqlValue(DATASET_VALUE, 1, insert);
            insert.executeUpdate();
        }

        try (PreparedStatement select = jdbcConnection
                .prepareStatement("SELECT TIM FROM " + ZONED_TABLE + " WHERE ID = 1");
                ResultSet resultSet = select.executeQuery())
        {
            resultSet.next();
            final OffsetDateTime stored = resultSet.getObject(1, OffsetDateTime.class);
            assertThat(stored)
                    .as("a TIMESTAMP WITH TIME ZONE column must store the offset implied by "
                            + "the dataset's own time zone, not the JVM's default time zone.")
                    .isEqualTo(OffsetDateTime.parse("2026-07-22T15:00:00-02:00"));
        }
    }

    @Test
    void testSetSqlValue_withOffsetDifferentFromLocalZone_writesLiteralDigitsToTimestampColumnWithoutTimeZone()
            throws Exception
    {
        final Connection jdbcConnection = connection.getConnection();
        dropTableQuietly(PLAIN_TABLE);
        final boolean isSqlServer = jdbcConnection.getMetaData().getDatabaseProductName()
                .startsWith("Microsoft SQL Server");
        final String plainTimestampType = isSqlServer ? "DATETIME2" : "TIMESTAMP";
        try (Statement statement = jdbcConnection.createStatement())
        {
            statement.execute("CREATE TABLE " + PLAIN_TABLE
                    + " (ID INTEGER NOT NULL PRIMARY KEY, TIM " + plainTimestampType + ")");
        }

        try (PreparedStatement insert = jdbcConnection
                .prepareStatement("INSERT INTO " + PLAIN_TABLE + " VALUES (1, ?)"))
        {
            new TimestampDataType().setSqlValue(DATASET_VALUE, 1, insert);
            insert.executeUpdate();
        }

        try (PreparedStatement select = jdbcConnection
                .prepareStatement("SELECT TIM FROM " + PLAIN_TABLE + " WHERE ID = 1");
                ResultSet resultSet = select.executeQuery())
        {
            resultSet.next();
            assertThat(resultSet.getTimestamp(1))
                    .as("issue #711: a column without a time zone must still receive the "
                            + "dataset's literal wall-clock digits, regardless of the JVM's "
                            + "default time zone.")
                    .isEqualTo(Timestamp.valueOf("2026-07-22 15:00:00"));
        }
    }
}
