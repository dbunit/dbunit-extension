/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.database.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AutoCommitOffWarningTest
{
    private final Logger logger =
            (Logger) LoggerFactory.getLogger(AutoCommitOffWarning.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender()
    {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender()
    {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static IDatabaseConnection connectionWithAutoCommit(final boolean autoCommit)
            throws SQLException
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        when(jdbc.getAutoCommit()).thenReturn(autoCommit);
        return connection;
    }

    private long warnings()
    {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("autocommit disabled"))
                .count();
    }

    @Test
    void testAccept_autoCommitOff_warnsOnce() throws SQLException
    {
        final AutoCommitOffWarning warning = new AutoCommitOffWarning();
        final IDatabaseConnection connection = connectionWithAutoCommit(false);

        warning.accept(connection);
        warning.accept(connection);

        assertThat(warnings()).as("Warns at most once per instance.").isEqualTo(1);
    }

    @Test
    void testAccept_autoCommitOn_doesNotWarn() throws SQLException
    {
        new AutoCommitOffWarning().accept(connectionWithAutoCommit(true));

        assertThat(warnings()).isZero();
    }

    @Test
    void testAccept_autoCommitStateUnreadable_doesNotWarn() throws SQLException
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        when(jdbc.getAutoCommit()).thenThrow(new SQLException("cannot read"));

        new AutoCommitOffWarning().accept(connection);

        assertThat(warnings()).as("An unreadable autocommit state is not warned about.")
                .isZero();
    }
}
