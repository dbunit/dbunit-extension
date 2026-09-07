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

import org.dbunit.IDatabaseTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;

/**
 * An immutable snapshot of the three pieces of an {@link IDatabaseTester} the annotation
 * runtime mutates for a test - its dataset, setup operation, and teardown operation - so they
 * can be put back after the test.
 *
 * <p>Both lifecycle paths take one at the start of a test and restore it at the end, so a
 * tester shared across methods - a {@code static @DbUnitTester} field, or any tester field
 * under {@code @TestInstance(Lifecycle.PER_CLASS)} - carries no per-method
 * {@code @DbUnitPrep}/{@code @DbUnitSetup}/{@code @DbUnitTearDown} onto the next method. A
 * value a {@code @BeforeEach} method set is the captured snapshot and is restored unchanged,
 * so configuring the tester in {@code @BeforeEach} still works.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class TesterStateSnapshot
{
    private final IDataSet dataSet;
    private final DatabaseOperation setUpOperation;
    private final DatabaseOperation tearDownOperation;

    private TesterStateSnapshot(final IDataSet dataSet, final DatabaseOperation setUpOperation,
            final DatabaseOperation tearDownOperation)
    {
        this.dataSet = dataSet;
        this.setUpOperation = setUpOperation;
        this.tearDownOperation = tearDownOperation;
    }

    /**
     * Captures {@code tester}'s current dataset and setup/teardown operations.
     *
     * @param tester The tester to snapshot.
     * @return The snapshot.
     */
    public static TesterStateSnapshot capture(final IDatabaseTester tester)
    {
        return new TesterStateSnapshot(tester.getDataSet(), tester.getSetUpOperation(),
                tester.getTearDownOperation());
    }

    /**
     * Restores this snapshot's dataset and setup/teardown operations onto {@code tester}.
     *
     * @param tester The tester to restore.
     */
    public void restoreTo(final IDatabaseTester tester)
    {
        tester.setDataSet(dataSet);
        tester.setSetUpOperation(setUpOperation);
        tester.setTearDownOperation(tearDownOperation);
    }

    /**
     * Restores {@code snapshot} onto {@code tester}, or does nothing when {@code snapshot} is
     * {@code null} - the case when a lifecycle threw before capturing one, or never reached the
     * capture at all.
     *
     * @param tester The tester to restore; untouched when {@code snapshot} is {@code null}.
     * @param snapshot The snapshot to restore, or {@code null} to do nothing.
     */
    public static void restore(final IDatabaseTester tester, final TesterStateSnapshot snapshot)
    {
        if (snapshot != null)
        {
            snapshot.restoreTo(tester);
        }
    }

    /**
     * Restores {@code snapshot} onto {@code tester} while {@code primary} is already being
     * thrown, attaching any failure from the restore itself to it via
     * {@link Throwable#addSuppressed(Throwable)} rather than letting that failure replace the
     * one already in flight. A {@code null} snapshot is a no-op.
     *
     * @param tester The tester to restore.
     * @param snapshot The snapshot to restore, or {@code null} to do nothing.
     * @param primary The failure already being thrown, to attach a restore failure to.
     */
    public static void restoreSuppressing(final IDatabaseTester tester,
            final TesterStateSnapshot snapshot, final Throwable primary)
    {
        try
        {
            restore(tester, snapshot);
        } catch (final RuntimeException restoreFailure)
        {
            primary.addSuppressed(restoreFailure);
        }
    }
}
