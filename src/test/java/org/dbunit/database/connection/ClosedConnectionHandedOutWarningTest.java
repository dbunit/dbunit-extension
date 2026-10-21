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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ClosedConnectionHandedOutWarningTest
{
    private final Logger logger =
            (Logger) LoggerFactory.getLogger(ClosedConnectionHandedOutWarning.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final IDatabaseConnection connection = mock(IDatabaseConnection.class);
    private final Connection jdbcConnection = mock(Connection.class);
    private final IDatabaseTester tester = new DefaultDatabaseTester(connection);

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

    private List<ILoggingEvent> warnings()
    {
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    @Test
    void testAccept_openConnection_doesNotWarn() throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.isClosed()).thenReturn(false);
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(() -> tester);

        warning.accept(connection);

        assertThat(warnings()).as("An open connection is not warned about.").isEmpty();
    }

    @Test
    void testAccept_closedConnection_warnsOnceNamingTesterAndRemedy() throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.isClosed()).thenReturn(true);
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(() -> tester);

        warning.accept(connection);
        warning.accept(connection);

        assertThat(warnings())
                .as("A test handed a closed connection is warned once, also when the closed"
                        + " connection is handed out again.")
                .hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .as("The warning names the tester's class, what it handed out, and the remedy.")
                .contains("org.dbunit.DefaultDatabaseTester", "already closed",
                        "NO_OP_OPERATION_LISTENER", "getOperationListener()");
    }

    @Test
    void testAccept_testerReplacedAfterTheWarningWasCreated_namesTheTesterItHasWhenItWarns()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.isClosed()).thenReturn(true);
        final AtomicReference<IDatabaseTester> currentTester = new AtomicReference<>(tester);
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(currentTester::get);
        currentTester.set(new TesterOverOneConnection(connection));

        warning.accept(connection);

        assertThat(warnings()).as("The closed connection is warned about once.")
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .as("A test case can be given another tester between tests, so the warning"
                                + " names the tester it has when it warns.")
                        .contains(TesterOverOneConnection.class.getName())
                        .doesNotContain("org.dbunit.DefaultDatabaseTester"));
    }

    @Test
    void testAccept_closedConnectionAndNoTester_doesNotWarn() throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.isClosed()).thenReturn(true);
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(() -> null);

        warning.accept(connection);

        assertThat(warnings())
                .as("With no tester to name there is nothing to advise, so nothing is warned"
                        + " about.")
                .isEmpty();
    }

    @Test
    void testAccept_closedConnectionHandedToTheNextTest_warnsForThatTestToo() throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.isClosed()).thenReturn(true);

        new ClosedConnectionHandedOutWarning(() -> tester).accept(connection);
        new ClosedConnectionHandedOutWarning(() -> tester).accept(connection);

        assertThat(warnings())
                .as("Each test that is handed a closed connection fails on it, so each warns.")
                .hasSize(2);
    }

    @Test
    void testAccept_connectionCannotReportWhetherItIsClosed_doesNotWarn() throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.isClosed()).thenThrow(new SQLException("broken"));
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(() -> tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("A connection that cannot say whether it is closed is not known to be.")
                .isEmpty();
    }

    @Test
    void testAccept_connectionWithoutAJdbcConnection_doesNotWarn() throws Exception
    {
        when(connection.getConnection()).thenReturn(null);
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(() -> tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("A connection without a JDBC connection, such as a test double, is not known"
                        + " to be closed.")
                .isEmpty();
    }

    @Test
    void testAccept_noConnection_doesNotWarn()
    {
        final ClosedConnectionHandedOutWarning warning =
                new ClosedConnectionHandedOutWarning(() -> tester);

        warning.accept(null);

        assertThat(warnings())
                .as("A tester that hands out no connection, such as a test double, is not"
                        + " warned about.")
                .isEmpty();
    }

    /**
     * A tester over one fixed connection, of another kind than the {@link DefaultDatabaseTester}
     * the tests start with.
     */
    private static class TesterOverOneConnection extends AbstractDatabaseTester
    {
        private final IDatabaseConnection fixedConnection;

        TesterOverOneConnection(final IDatabaseConnection fixedConnection)
        {
            this.fixedConnection = fixedConnection;
        }

        @Override
        public IDatabaseConnection getConnection()
        {
            return fixedConnection;
        }
    }
}
