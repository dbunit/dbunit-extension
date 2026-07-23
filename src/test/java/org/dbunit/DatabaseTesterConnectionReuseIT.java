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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests proving that a {@link CachingConnectionProvider}, shared across the fresh
 * {@link IDatabaseTester} instances a test harness builds for each test method (exactly as
 * {@link DatabaseTestCase} already does - its {@code tester} field is reset to <code>null</code>
 * after every {@link DatabaseTestCase#tearDown()}, so {@link DatabaseTestCase#newDatabaseTester()}
 * runs again on the next test), delivers the cross-test-method connection reuse issue #799 asks
 * for - and only when paired with a non-closing {@link IOperationListener}, staying fully
 * backward compatible otherwise.
 * <p>
 * Also proves {@link DefaultPrepAndExpectedTestCase} - which manages its own connection lifecycle
 * rather than delegating to a listener (see issue #800) - only joins that cross-test reuse when
 * {@link DefaultPrepAndExpectedTestCase#setCloseConnectionAfterTest(boolean)} is set to false;
 * left at its default, it must not close a connection a shared provider is still using for
 * other test methods (issue #801 code review feedback).
 *
 * @since 3.4.0
 */
class DatabaseTesterConnectionReuseIT
{
    private DatabaseProfile profile;

    @BeforeEach
    void setUp() throws Exception
    {
        profile = DatabaseEnvironment.getInstance().getProfile();
    }

    @Test
    void testOnSetupAndOnTearDown_acrossFreshTesterInstancesSharingAProviderAndNoOpListener_reuseOneConnection()
            throws Exception
    {
        final CachingConnectionProvider sharedProvider = new CachingConnectionProvider();
        final List<IDatabaseConnection> connectionsUsed = new ArrayList<>();
        try
        {
            for (int simulatedTestMethod = 0; simulatedTestMethod < 3; simulatedTestMethod++)
            {
                final IDatabaseTester tester = newSharedProviderTester(sharedProvider);
                tester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
                tester.setSetUpOperation(capturingOperation(connectionsUsed));
                tester.setTearDownOperation(capturingOperation(connectionsUsed));

                tester.onSetup();
                tester.onTearDown();
            }

            assertThat(connectionsUsed)
                    .as("Precondition: setUp+tearDown across 3 simulated test methods must have "
                            + "executed an operation, and therefore captured a connection, 6 times.")
                    .hasSize(6);
            assertThat(new HashSet<>(connectionsUsed))
                    .as("Every onSetup()/onTearDown() call across 3 simulated test methods - each "
                            + "building its own fresh IDatabaseTester, exactly as DatabaseTestCase "
                            + "does per test - must share the one connection cached by the "
                            + "CachingConnectionProvider they all point at, since a "
                            + "NO_OP_OPERATION_LISTENER keeps it from ever being closed between "
                            + "calls.")
                    .hasSize(1);
        } finally
        {
            sharedProvider.close();
        }
    }

    @Test
    void testOnSetupAndOnTearDown_acrossFreshTesterInstancesSharingAProviderWithDefaultListener_staysOptInAndCreatesFreshConnectionsEveryTime()
            throws Exception
    {
        final CachingConnectionProvider sharedProvider = new CachingConnectionProvider();
        final List<IDatabaseConnection> connectionsUsed = new ArrayList<>();
        try
        {
            for (int simulatedTestMethod = 0; simulatedTestMethod < 2; simulatedTestMethod++)
            {
                final IDatabaseTester tester = newSharedProviderTester(sharedProvider);
                // Deliberately not overriding the default DefaultOperationListener, which closes
                // the connection after every onSetup()/onTearDown() call.
                tester.setSetUpOperation(capturingOperation(connectionsUsed));
                tester.setTearDownOperation(capturingOperation(connectionsUsed));

                tester.onSetup();
                tester.onTearDown();
            }

            assertThat(connectionsUsed)
                    .as("Precondition: setUp+tearDown across 2 simulated test methods must have "
                            + "captured a connection 4 times.")
                    .hasSize(4);
            assertThat(new HashSet<>(connectionsUsed))
                    .as("Opting into a CachingConnectionProvider without also switching away from "
                            + "the library's default closing IOperationListener must stay fully "
                            + "backward compatible: every call still observes a freshly "
                            + "(re)created connection, exactly like not using a provider at all.")
                    .hasSize(4);
        } finally
        {
            sharedProvider.close();
        }
    }

    @Test
    void testDefaultPrepAndExpectedTestCase_acrossFreshInstancesSharingAProviderWithCloseDisabled_reusesOneConnection()
            throws Exception
    {
        final CachingConnectionProvider sharedProvider = new CachingConnectionProvider();
        final List<IDatabaseConnection> connectionsUsed = new ArrayList<>();
        try
        {
            for (int simulatedTestMethod = 0; simulatedTestMethod < 3; simulatedTestMethod++)
            {
                final IDatabaseTester tester = newSharedProviderTester(sharedProvider);
                final DefaultPrepAndExpectedTestCase tc = newTestCase(tester);
                tc.setCloseConnectionAfterTest(false);

                tc.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                        new String[] {});
                tc.preTest();
                tc.postTest();

                connectionsUsed.add(tester.getConnection());
            }

            assertThat(new HashSet<>(connectionsUsed))
                    .as("With closing disabled, DefaultPrepAndExpectedTestCase must not close the "
                            + "connection a shared CachingConnectionProvider still has cached, so "
                            + "3 simulated test methods - each building its own fresh tester and "
                            + "test case pointed at the same provider - must all observe the same "
                            + "underlying connection (issue #800/#801).")
                    .hasSize(1);
        } finally
        {
            sharedProvider.close();
        }
    }

    @Test
    void testDefaultPrepAndExpectedTestCase_acrossFreshInstancesSharingAProviderWithDefaultConfiguration_createsFreshConnectionsEveryTime()
            throws Exception
    {
        final CachingConnectionProvider sharedProvider = new CachingConnectionProvider();
        final List<IDatabaseConnection> connectionsUsed = new ArrayList<>();
        try
        {
            for (int simulatedTestMethod = 0; simulatedTestMethod < 2; simulatedTestMethod++)
            {
                final IDatabaseTester tester = newSharedProviderTester(sharedProvider);
                final DefaultPrepAndExpectedTestCase tc = newTestCase(tester);
                // Deliberately not calling setCloseConnectionAfterTest(false).

                tc.configureTest(new VerifyTableDefinition[] {}, new String[] {},
                        new String[] {});
                tc.preTest();
                tc.postTest();

                connectionsUsed.add(tester.getConnection());
            }

            assertThat(new HashSet<>(connectionsUsed))
                    .as("Left at its default, DefaultPrepAndExpectedTestCase must stay fully "
                            + "backward compatible: cleanupData() closes the connection it used, "
                            + "so the CachingConnectionProvider must hand back a freshly "
                            + "(re)created one to the next simulated test method, exactly like not "
                            + "sharing a provider at all.")
                    .hasSize(2);
        } finally
        {
            sharedProvider.close();
        }
    }

    private DefaultPrepAndExpectedTestCase newTestCase(final IDatabaseTester tester)
    {
        final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();
        return new DefaultPrepAndExpectedTestCase(dataFileLoader, tester);
    }

    private IDatabaseTester newSharedProviderTester(final CachingConnectionProvider sharedProvider)
            throws Exception
    {
        return new JdbcDatabaseTester(profile.getDriverClass(), profile.getConnectionUrl(),
                profile.getUser(), profile.getPassword(), profile.getSchema(), sharedProvider);
    }

    /**
     * Records the connection passed to it by {@code AbstractDatabaseTester.executeOperation()},
     * which only invokes {@link IDatabaseTester#getConnection()} - and therefore only calls this -
     * when the configured operation is not {@link DatabaseOperation#NONE}.
     */
    private static DatabaseOperation capturingOperation(final List<IDatabaseConnection> capturedInto)
    {
        return new DatabaseOperation()
        {
            @Override
            public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
            {
                capturedInto.add(connection);
            }
        };
    }
}
