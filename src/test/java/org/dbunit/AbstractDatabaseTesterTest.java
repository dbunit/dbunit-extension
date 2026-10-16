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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for how {@link AbstractDatabaseTester} drives its {@link IOperationListener},
 * run against a real H2 in-memory connection (via {@link InMemoryDatabaseConnection}).
 *
 * @since 3.6.0
 */
class AbstractDatabaseTesterTest
{
    private IDatabaseConnection connection;
    private DefaultDatabaseTester tester;
    private IOperationListener listener;

    @BeforeEach
    void setUp() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        tester = new DefaultDatabaseTester(connection);
        tester.setDataSet(new DefaultDataSet());
        listener = mock(IOperationListener.class);
        tester.setOperationListener(listener);
    }

    @AfterEach
    void closeConnection() throws Exception
    {
        connection.close();
    }

    @Test
    void testOnSetup_connectionRetrievedThrows_stillNotifiesOperationSetUpFinished()
            throws Exception
    {
        final IllegalStateException failure = new IllegalStateException("connectionRetrieved");
        doThrow(failure).when(listener).connectionRetrieved(connection);

        assertThatThrownBy(() -> tester.onSetup())
                .as("The listener's own failure must propagate unchanged.")
                .isSameAs(failure);

        verify(listener).operationSetUpFinished(connection);
    }

    @Test
    void testOnTearDown_connectionRetrievedThrows_stillNotifiesOperationTearDownFinished()
            throws Exception
    {
        tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final IllegalStateException failure = new IllegalStateException("connectionRetrieved");
        doThrow(failure).when(listener).connectionRetrieved(connection);

        assertThatThrownBy(() -> tester.onTearDown())
                .as("The listener's own failure must propagate unchanged.")
                .isSameAs(failure);

        verify(listener).operationTearDownFinished(connection);
    }

    // The order below is relied on by org.dbunit.annotation.runtime.AnnotatedTestExecutor: it
    // takes the row count check baseline from the connection the listener is told about, before
    // the operation changes any data, and finishes combining several @DbUnitPrep files, setting
    // the tester's dataset, once the listener has told it that connection's case sensitivity.

    @Test
    void testOnSetup_operationIsNotNone_notifiesTheListenerBeforeReadingTheDataSetAndRunningTheOperation()
            throws Exception
    {
        final List<String> events = new ArrayList<>();
        final DefaultDatabaseTester recordingTester = recordingTester(events);
        recordingTester.setSetUpOperation(recordingOperation(events));

        recordingTester.onSetup();

        assertThat(events)
                .as("The listener must be told about the retrieved connection before the tester"
                        + " reads its dataset and before the operation runs, and told when the"
                        + " operation is finished last.")
                .isEqualTo(Arrays.asList("getConnection", "connectionRetrieved", "getDataSet",
                        "execute", "operationSetUpFinished"));
    }

    @Test
    void testOnTearDown_operationIsNotNone_notifiesTheListenerBeforeReadingTheDataSetAndRunningTheOperation()
            throws Exception
    {
        final List<String> events = new ArrayList<>();
        final DefaultDatabaseTester recordingTester = recordingTester(events);
        recordingTester.setTearDownOperation(recordingOperation(events));

        recordingTester.onTearDown();

        assertThat(events)
                .as("The listener must be told about the retrieved connection before the tester"
                        + " reads its dataset and before the operation runs, and told when the"
                        + " operation is finished last.")
                .isEqualTo(Arrays.asList("getConnection", "connectionRetrieved", "getDataSet",
                        "execute", "operationTearDownFinished"));
    }

    @Test
    void testOnSetup_operationIsNone_retrievesNoConnectionAndNotifiesNoListener()
            throws Exception
    {
        final List<String> events = new ArrayList<>();
        final DefaultDatabaseTester recordingTester = recordingTester(events);
        recordingTester.setSetUpOperation(DatabaseOperation.NONE);

        recordingTester.onSetup();

        assertThat(events)
                .as("A NONE operation needs no connection, so it must neither be retrieved nor"
                        + " reported to the listener, which the annotation runtime resolves a"
                        + " connection for itself in that case.")
                .isEmpty();
    }

    private DefaultDatabaseTester recordingTester(final List<String> events)
    {
        final DefaultDatabaseTester recordingTester = new DefaultDatabaseTester(connection)
        {
            @Override
            public IDatabaseConnection getConnection() throws Exception
            {
                events.add("getConnection");
                return super.getConnection();
            }

            @Override
            public IDataSet getDataSet()
            {
                events.add("getDataSet");
                return super.getDataSet();
            }
        };
        recordingTester.setDataSet(new DefaultDataSet());
        recordingTester.setOperationListener(new IOperationListener()
        {
            @Override
            public void connectionRetrieved(final IDatabaseConnection retrieved)
            {
                events.add("connectionRetrieved");
            }

            @Override
            public void operationSetUpFinished(final IDatabaseConnection finished)
            {
                events.add("operationSetUpFinished");
            }

            @Override
            public void operationTearDownFinished(final IDatabaseConnection finished)
            {
                events.add("operationTearDownFinished");
            }
        });
        return recordingTester;
    }

    private static DatabaseOperation recordingOperation(final List<String> events)
    {
        return new DatabaseOperation()
        {
            @Override
            public void execute(final IDatabaseConnection operatedOn, final IDataSet dataSet)
            {
                events.add("execute");
            }
        };
    }
}
