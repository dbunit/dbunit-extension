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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

class UsedTestersTest
{
    private final UsedTesters usedTesters = new UsedTesters();

    @Test
    void testRecordUse_testerNeverUsedBefore_returnsOne()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);

        final int uses = usedTesters.recordUse(tester);

        assertThat(uses).as("The first use of a tester is use number one.").isEqualTo(1);
    }

    @Test
    void testRecordUse_sameTesterAgain_countsUp()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);

        usedTesters.recordUse(tester);
        final int secondUse = usedTesters.recordUse(tester);
        final int thirdUse = usedTesters.recordUse(tester);

        assertThat(secondUse).as("The second use of the same tester.").isEqualTo(2);
        assertThat(thirdUse).as("The third use of the same tester.").isEqualTo(3);
    }

    @Test
    void testRecordUse_anotherTester_countsItOnItsOwn()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final IDatabaseTester anotherTester = mock(IDatabaseTester.class);
        usedTesters.recordUse(tester);

        final int uses = usedTesters.recordUse(anotherTester);

        assertThat(uses).as("A tester is counted on its own.").isEqualTo(1);
    }

    @Test
    void testRecordUse_testersThatAreEqualButNotTheSameInstance_countsEachOnItsOwn()
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final IDatabaseTester tester = new EqualToAnyTester(connection);
        final IDatabaseTester equalTester = new EqualToAnyTester(connection);
        usedTesters.recordUse(tester);

        final int uses = usedTesters.recordUse(equalTester);

        assertThat(uses)
                .as("Testers are told apart by identity, whatever their equals() says.")
                .isEqualTo(1);
    }

    @Test
    void testRecordUse_manyThreadsUsingTheSameTester_countsEveryUseOnce() throws Exception
    {
        final int threads = 8;
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        try
        {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Integer>> results = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++)
            {
                results.add(executor.submit(() ->
                {
                    start.await();
                    return usedTesters.recordUse(tester);
                }));
            }
            start.countDown();

            final List<Integer> counts = new ArrayList<>();
            for (final Future<Integer> result : results)
            {
                counts.add(result.get(10, TimeUnit.SECONDS));
            }

            assertThat(counts)
                    .as("Each use gets a count of its own, so exactly one of them is the second.")
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        } finally
        {
            executor.shutdownNow();
        }
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
