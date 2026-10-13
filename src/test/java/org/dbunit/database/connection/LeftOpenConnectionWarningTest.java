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
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.stream.Collectors;

import org.dbunit.DefaultOperationListener;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LeftOpenConnectionWarningTest
{
    private static final String DRIVER_CLASS = "org.h2.Driver";
    private static final String CONNECTION_URL = "jdbc:h2:mem:left_open_connection_warning";

    private final Logger logger =
            (Logger) LoggerFactory.getLogger(LeftOpenConnectionWarning.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final IDatabaseConnection connection = mock(IDatabaseConnection.class);

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

    private static ConnectionOwnership ownership(final boolean closeConnectionAfterTest,
            final IOperationListener testerListener)
    {
        return new ConnectionOwnership(() -> closeConnectionAfterTest, () -> testerListener,
                () -> true);
    }

    private static ConnectionOwnership ownershipThatMustNotBeConsulted()
    {
        return new ConnectionOwnership(() -> {
            throw new AssertionError("The ownership must not be consulted.");
        }, () -> {
            throw new AssertionError("The ownership must not be consulted.");
        }, () -> {
            throw new AssertionError("The ownership must not be consulted.");
        });
    }

    private static JdbcDatabaseTester testerOpeningANewConnectionOnEachCall()
            throws ClassNotFoundException
    {
        return new JdbcDatabaseTester(DRIVER_CLASS, CONNECTION_URL);
    }

    private static JdbcDatabaseTester testerReusingItsConnection() throws ClassNotFoundException
    {
        return new JdbcDatabaseTester(DRIVER_CLASS, CONNECTION_URL, null, null, null,
                new CachingConnectionProvider());
    }

    private List<ILoggingEvent> warnings()
    {
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    @Test
    void testAccept_flagFalseAndTesterOpensNewConnectionOnEachCall_warnsOnceNamingTesterCauseAndRemedy()
            throws Exception
    {
        final JdbcDatabaseTester tester = testerOpeningANewConnectionOnEachCall();
        final LeftOpenConnectionWarning warning = new LeftOpenConnectionWarning(
                ownership(false, new DefaultOperationListener()), () -> tester);

        warning.accept(connection);
        warning.accept(connection);

        assertThat(warnings()).as("Warns at most once per instance.").hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .as("The warning names the tester's class, both ways the connection is left"
                        + " open, and the remedy.")
                .contains("JdbcDatabaseTester", "closeConnectionAfterTest", "no-op",
                        "CachingConnectionProvider");
    }

    @Test
    void testAccept_noOpListenerAndTesterOpensNewConnectionOnEachCall_warns() throws Exception
    {
        final JdbcDatabaseTester tester = testerOpeningANewConnectionOnEachCall();
        final LeftOpenConnectionWarning warning = new LeftOpenConnectionWarning(
                ownership(true, IOperationListener.NO_OP_OPERATION_LISTENER), () -> tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("A no-op listener leaves the connection open too, whatever the flag says.")
                .hasSize(1);
    }

    @Test
    void testAccept_ownershipClosesTheConnection_doesNotWarn() throws Exception
    {
        final JdbcDatabaseTester tester = testerOpeningANewConnectionOnEachCall();
        final LeftOpenConnectionWarning warning = new LeftOpenConnectionWarning(
                ownership(true, new DefaultOperationListener()), () -> tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("A connection dbUnit closes after the test is not left open, so nothing"
                        + " leaks.")
                .isEmpty();
    }

    @Test
    void testAccept_testerReusesItsConnection_doesNotWarnAndDoesNotConsultOwnership()
            throws Exception
    {
        final JdbcDatabaseTester tester = testerReusingItsConnection();
        final LeftOpenConnectionWarning warning =
                new LeftOpenConnectionWarning(ownershipThatMustNotBeConsulted(), () -> tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("A tester over a CachingConnectionProvider has an owner for the connection"
                        + " left open, so the tester is checked first and nothing is asked of"
                        + " the ownership.")
                .isEmpty();
    }

    @Test
    void testAccept_testerIsNotAJdbcDatabaseTester_doesNotWarnAndTouchesNothing()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final LeftOpenConnectionWarning warning =
                new LeftOpenConnectionWarning(ownershipThatMustNotBeConsulted(), () -> tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("Only a JdbcDatabaseTester is known to open a new connection on every call;"
                        + " a data source may return the same one.")
                .isEmpty();
        verifyNoInteractions(tester);
    }

    @Test
    void testAccept_noTester_doesNotWarn()
    {
        final LeftOpenConnectionWarning warning =
                new LeftOpenConnectionWarning(ownershipThatMustNotBeConsulted(), () -> null);

        warning.accept(connection);

        assertThat(warnings()).as("No tester to inspect means nothing to warn about.")
                .isEmpty();
    }
}
