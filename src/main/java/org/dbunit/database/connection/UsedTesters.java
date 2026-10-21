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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.IDatabaseTester;

/**
 * Counts how many times each {@link IDatabaseTester} was used, by identity, whatever its
 * {@code equals()} says, and without keeping any tester alive: a tester that nothing else
 * references is forgotten. It lets something be said only about a tester that serves more than
 * one test, as {@link ClosedFixedConnectionWarning} does, because a tester built for a single
 * test never shows up a second time.
 *
 * <p>Thread safe, as the tests that use one tester may run in parallel.
 *
 * @since 3.6.0
 */
final class UsedTesters
{
    private final ReferenceQueue<IDatabaseTester> collected = new ReferenceQueue<>();
    private final Map<TesterReference, AtomicInteger> usesByTester = new HashMap<>();

    /**
     * Records one use of the tester.
     *
     * @param tester The tester that is used; not {@code null}.
     * @return How many times the tester has been used, this use included: {@code 1} the first
     *         time, {@code 2} the second time.
     */
    synchronized int recordUse(final IDatabaseTester tester)
    {
        forgetCollectedTesters();
        final TesterReference probe = new TesterReference(tester, null);
        final AtomicInteger recordedUses = usesByTester.get(probe);
        if (recordedUses == null)
        {
            usesByTester.put(new TesterReference(tester, collected), new AtomicInteger(1));
            return 1;
        }
        return recordedUses.incrementAndGet();
    }

    private void forgetCollectedTesters()
    {
        Reference<? extends IDatabaseTester> collectedTester = collected.poll();
        while (collectedTester != null)
        {
            usesByTester.remove(collectedTester);
            collectedTester = collected.poll();
        }
    }

    /**
     * A weak reference to a tester that is equal to another one only when both refer to the very
     * same tester, which a tester's own {@code equals()} and {@code hashCode()} do not decide.
     */
    private static final class TesterReference extends WeakReference<IDatabaseTester>
    {
        private final int identityHashCode;

        private TesterReference(final IDatabaseTester tester,
                final ReferenceQueue<IDatabaseTester> queue)
        {
            super(tester, queue);
            this.identityHashCode = System.identityHashCode(tester);
        }

        @Override
        public int hashCode()
        {
            return identityHashCode;
        }

        @Override
        public boolean equals(final Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof TesterReference))
            {
                return false;
            }
            final IDatabaseTester tester = get();
            final TesterReference otherReference = (TesterReference) other;
            return tester != null && tester == otherReference.get();
        }
    }
}
