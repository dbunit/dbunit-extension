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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.dbunit.DefaultDatabaseTester;
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

class ClosedFixedConnectionWarningTest
{
    private static final String DRIVER_CLASS = "org.h2.Driver";
    private static final String CONNECTION_URL = "jdbc:h2:mem:closed_fixed_connection_warning";

    private final Logger logger =
            (Logger) LoggerFactory.getLogger(ClosedFixedConnectionWarning.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final IDatabaseConnection connection = mock(IDatabaseConnection.class);

    /** Fresh for every test, as the warning's own registry is shared by the whole JVM. */
    private final UsedTesters usedTesters = new UsedTesters();

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
        return new ConnectionOwnership(() -> closeConnectionAfterTest, () -> testerListener);
    }

    private static ConnectionOwnership ownershipThatMustNotBeConsulted()
    {
        return new ConnectionOwnership(() -> {
            throw new AssertionError("The ownership must not be consulted.");
        }, () -> {
            throw new AssertionError("The ownership must not be consulted.");
        });
    }

    /** A warning of a new lifecycle owner, such as the test case of the next test. */
    private ClosedFixedConnectionWarning warningOfANewOwner(final ConnectionOwnership ownership,
            final IDatabaseTester tester)
    {
        return new ClosedFixedConnectionWarning(ownership, () -> tester, usedTesters);
    }

    private List<ILoggingEvent> warnings()
    {
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    @Test
    void testAccept_defaultDatabaseTesterOnItsFirstUse_doesNotWarn()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final ClosedFixedConnectionWarning warning = warningOfANewOwner(
                ownership(true, new DefaultOperationListener()), tester);

        warning.accept(connection);

        assertThat(warnings())
                .as("A tester built for one test is right to have its connection closed, so a"
                        + " tester seen once is not warned about.")
                .isEmpty();
    }

    @Test
    void testAccept_defaultDatabaseTesterOnItsSecondUseAndOwnershipCloses_warnsOnceNamingTesterCauseAndRemedy()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final ConnectionOwnership closes = ownership(true, new DefaultOperationListener());

        warningOfANewOwner(closes, tester).accept(connection);
        final ClosedFixedConnectionWarning ownerOfTheSecondTest = warningOfANewOwner(closes, tester);
        ownerOfTheSecondTest.accept(connection);
        ownerOfTheSecondTest.accept(connection);
        warningOfANewOwner(closes, tester).accept(connection);

        assertThat(warnings())
                .as("The tester is warned about when the second test uses it, and not again for"
                        + " a connection acquired again or by the owner of a third test.")
                .hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .as("The warning names the tester's class, what the closing does to the next"
                        + " test, and both remedies.")
                .contains("DefaultDatabaseTester", "finds it closed", "NO_OP_OPERATION_LISTENER",
                        "setCloseConnectionAfterTest(false)");
    }

    @Test
    void testAccept_sameOwnerAcquiresAgainWithinOneLifecycle_doesNotWarn()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final ClosedFixedConnectionWarning warning = warningOfANewOwner(
                ownership(true, new DefaultOperationListener()), tester);

        warning.accept(connection);
        warning.accept(connection);
        warning.accept(connection);

        assertThat(warnings())
                .as("A lifecycle that acquires the connection again, such as the clean up after"
                        + " a failed step, is still one test, so the tester is used once.")
                .isEmpty();
    }

    @Test
    void testAccept_sameOwnerAcquiresAgainAfterItsLifecycleEnded_warnsOnce()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final ClosedFixedConnectionWarning warning = warningOfANewOwner(
                ownership(true, new DefaultOperationListener()), tester);

        warning.accept(connection);
        warning.lifecycleEnded();
        warning.accept(connection);

        assertThat(warnings())
                .as("A lifecycle owner reused across tests, such as a test case in a static"
                        + " field, uses the tester again in its next lifecycle.")
                .hasSize(1);

        warning.lifecycleEnded();
        warning.accept(connection);

        assertThat(warnings()).as("The tester is warned about once.").hasSize(1);
    }

    @Test
    void testAccept_defaultDatabaseTesterOverCachingProviderOnItsSecondUseAndOwnershipCloses_warns()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(
                new CachingConnectionProvider(), () -> connection);
        final ConnectionOwnership closes = ownership(true, new DefaultOperationListener());

        warningOfANewOwner(closes, tester).accept(connection);
        warningOfANewOwner(closes, tester).accept(connection);

        assertThat(warnings())
                .as("A DefaultDatabaseTester hands out the connection it holds again whichever"
                        + " way it was built, and closing a cached one costs a connection for"
                        + " each test.")
                .hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .as("The warning says what the provider does.")
                .contains("CachingConnectionProvider");
    }

    @Test
    void testAccept_testerReportsNoListenerOnItsSecondUseAndOwnershipCloses_warnsOnceNamingTesterCauseAndRemedy()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final ConnectionOwnership closes = ownership(true, new DefaultOperationListener());

        warningOfANewOwner(closes, tester).accept(connection);
        warningOfANewOwner(closes, tester).accept(connection);
        warningOfANewOwner(closes, tester).accept(connection);

        assertThat(warnings()).as("The tester is warned about once.").hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .as("The warning names why dbUnit cannot tell, what the closing does to the next"
                        + " test, and both remedies.")
                .contains("does not report its operation listener", "finds the connection closed",
                        "getOperationListener()", "NO_OP_OPERATION_LISTENER",
                        "setCloseConnectionAfterTest(false)");
    }

    @Test
    void testAccept_aNewTesterForEveryTest_neverWarns()
    {
        for (int test = 0; test < 3; test++)
        {
            final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
            warningOfANewOwner(ownership(true, new DefaultOperationListener()), tester)
                    .accept(connection);
        }

        assertThat(warnings())
                .as("Each test has a tester of its own, so none of them serves a second test.")
                .isEmpty();
    }

    @Test
    void testAccept_twoTestersThatAreOnlyEqual_areCountedSeparately()
    {
        final DefaultDatabaseTester first = new EqualToAnyTester(connection);
        final DefaultDatabaseTester second = new EqualToAnyTester(connection);
        final ConnectionOwnership closes = ownership(true, new DefaultOperationListener());

        warningOfANewOwner(closes, first).accept(connection);
        warningOfANewOwner(closes, second).accept(connection);

        assertThat(warnings())
                .as("Testers are told apart by identity, so a tester class with a value"
                        + " equals() does not make two testers one.")
                .isEmpty();
    }

    @Test
    void testAccept_acquisitionsThatDoNotQualifyBeforeOneThatDoes_doNotCount()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final AtomicBoolean closeAfterTest = new AtomicBoolean(false);
        final ConnectionOwnership ownership =
                new ConnectionOwnership(closeAfterTest::get, DefaultOperationListener::new);
        final ClosedFixedConnectionWarning firstOwner = warningOfANewOwner(ownership, tester);
        final ClosedFixedConnectionWarning secondOwner = warningOfANewOwner(ownership, tester);
        final ClosedFixedConnectionWarning thirdOwner = warningOfANewOwner(ownership, tester);

        firstOwner.accept(connection);
        secondOwner.accept(connection);
        closeAfterTest.set(true);
        thirdOwner.accept(connection);

        assertThat(warnings())
                .as("A connection that is left open is not closed, so it is not a use that"
                        + " counts: the connection acquired while it is going to be closed is the"
                        + " first.")
                .isEmpty();

        warningOfANewOwner(ownership, tester).accept(connection);

        assertThat(warnings()).as("The second connection that is going to be closed warns.")
                .hasSize(1);
    }

    @Test
    void testAccept_defaultDatabaseTesterAndFlagFalse_doesNotWarn()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final ConnectionOwnership leavesItOpen = ownership(false, new DefaultOperationListener());

        warningOfANewOwner(leavesItOpen, tester).accept(connection);
        warningOfANewOwner(leavesItOpen, tester).accept(connection);

        assertThat(warnings())
                .as("A connection left open for its owner is not closed, so nothing is closed"
                        + " that the next test needs.")
                .isEmpty();
    }

    @Test
    void testAccept_defaultDatabaseTesterAndNoOpListener_doesNotWarn()
    {
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);
        final ConnectionOwnership leavesItOpen =
                ownership(true, IOperationListener.NO_OP_OPERATION_LISTENER);

        warningOfANewOwner(leavesItOpen, tester).accept(connection);
        warningOfANewOwner(leavesItOpen, tester).accept(connection);

        assertThat(warnings())
                .as("The no-op listener leaves the connection open, whatever the flag says.")
                .isEmpty();
    }

    @Test
    void testAccept_testerOpensANewConnectionOnEachCall_doesNotWarnAndDoesNotConsultOwnership()
            throws Exception
    {
        final JdbcDatabaseTester tester = new JdbcDatabaseTester(DRIVER_CLASS, CONNECTION_URL);
        final ConnectionOwnership mustNotBeConsulted = ownershipThatMustNotBeConsulted();

        warningOfANewOwner(mustNotBeConsulted, tester).accept(connection);
        warningOfANewOwner(mustNotBeConsulted, tester).accept(connection);

        assertThat(warnings())
                .as("Closing a connection nothing else hands out again is right, so the tester"
                        + " is checked first and nothing is asked of the ownership.")
                .isEmpty();
    }

    @Test
    void testAccept_noTester_doesNotWarn()
    {
        final ClosedFixedConnectionWarning warning =
                new ClosedFixedConnectionWarning(ownershipThatMustNotBeConsulted(), () -> null,
                        usedTesters);

        warning.accept(connection);
        warning.accept(connection);

        assertThat(warnings()).as("No tester to inspect means nothing to warn about.")
                .isEmpty();
    }

    /** A tester that is equal to every other one of its class, as a value class would be. */
    private static final class EqualToAnyTester extends DefaultDatabaseTester
    {
        private EqualToAnyTester(final IDatabaseConnection connection)
        {
            super(connection);
        }

        @Override
        public boolean equals(final Object other)
        {
            return other instanceof EqualToAnyTester;
        }

        @Override
        public int hashCode()
        {
            return 1;
        }
    }
}
