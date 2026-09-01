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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;

class TesterStateSnapshotTest
{
    private static IDatabaseTester tester()
    {
        return new AbstractDatabaseTester()
        {
            @Override
            public IDatabaseConnection getConnection()
            {
                return null;
            }
        };
    }

    @Test
    void testRestoreTo_afterMutation_putsBackEveryCapturedValue() throws Exception
    {
        final IDatabaseTester tester = tester();
        final IDataSet incomingDataSet = new DefaultDataSet();
        tester.setDataSet(incomingDataSet);
        tester.setSetUpOperation(DatabaseOperation.REFRESH);
        tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final TesterStateSnapshot snapshot = TesterStateSnapshot.capture(tester);

        tester.setDataSet(new DefaultDataSet());
        tester.setSetUpOperation(DatabaseOperation.CLEAN_INSERT);
        tester.setTearDownOperation(DatabaseOperation.NONE);
        snapshot.restoreTo(tester);

        assertThat(tester.getDataSet()).isSameAs(incomingDataSet);
        assertThat(tester.getSetUpOperation()).isEqualTo(DatabaseOperation.REFRESH);
        assertThat(tester.getTearDownOperation()).isEqualTo(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testCapture_nullDataset_restoresNull() throws Exception
    {
        final IDatabaseTester tester = tester();
        final TesterStateSnapshot snapshot = TesterStateSnapshot.capture(tester);

        tester.setDataSet(new DefaultDataSet());
        snapshot.restoreTo(tester);

        assertThat(tester.getDataSet()).as("A tester that came in with no dataset is left with"
                + " none.").isNull();
    }

    @Test
    void testRestore_nullSnapshot_leavesTheTesterUntouched()
    {
        final IDatabaseTester tester = tester();
        tester.setSetUpOperation(DatabaseOperation.REFRESH);

        TesterStateSnapshot.restore(tester, null);

        assertThat(tester.getSetUpOperation()).as("A null snapshot - a lifecycle that threw"
                + " before capturing one - is a no-op.").isEqualTo(DatabaseOperation.REFRESH);
    }

    @Test
    void testRestore_nonNullSnapshot_putsItBack()
    {
        final IDatabaseTester tester = tester();
        tester.setSetUpOperation(DatabaseOperation.REFRESH);
        final TesterStateSnapshot snapshot = TesterStateSnapshot.capture(tester);
        tester.setSetUpOperation(DatabaseOperation.CLEAN_INSERT);

        TesterStateSnapshot.restore(tester, snapshot);

        assertThat(tester.getSetUpOperation()).isEqualTo(DatabaseOperation.REFRESH);
    }

    @Test
    void testRestoreSuppressing_restoreFails_attachesItToThePrimaryRatherThanReplacingIt()
    {
        final IDatabaseTester tester = failingToRestore();
        final TesterStateSnapshot snapshot = TesterStateSnapshot.capture(tester);
        final RuntimeException primary = new RuntimeException("the real failure");

        TesterStateSnapshot.restoreSuppressing(tester, snapshot, primary);

        assertThat(primary.getSuppressed())
                .as("The restore failure is attached, so the failure already in flight is still"
                        + " the one thrown.")
                .hasSize(1);
        assertThat(primary.getSuppressed()[0]).hasMessage("cannot restore teardown op");
    }

    @Test
    void testRestoreSuppressing_nullSnapshot_leavesThePrimaryClean()
    {
        final RuntimeException primary = new RuntimeException("the real failure");

        TesterStateSnapshot.restoreSuppressing(tester(), null, primary);

        assertThat(primary.getSuppressed()).isEmpty();
    }

    private static IDatabaseTester failingToRestore()
    {
        return new AbstractDatabaseTester()
        {
            @Override
            public IDatabaseConnection getConnection()
            {
                return null;
            }

            @Override
            public void setTearDownOperation(final DatabaseOperation tearDownOperation)
            {
                throw new IllegalStateException("cannot restore teardown op");
            }
        };
    }
}
