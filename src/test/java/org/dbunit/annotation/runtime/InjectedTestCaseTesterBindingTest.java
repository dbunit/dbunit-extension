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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Callable;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.junit.jupiter.api.Test;

class InjectedTestCaseTesterBindingTest
{
    @Test
    void testResolveTester_testCaseAlreadyHasTester_returnsExistingWithoutCallingFallback()
            throws Exception
    {
        final IDatabaseTester existing = mock(IDatabaseTester.class);
        final DefaultPrepAndExpectedTestCase testCase = new DefaultPrepAndExpectedTestCase();
        testCase.setDatabaseTester(existing);
        final Callable<IDatabaseTester> fallback = () ->
        {
            throw new AssertionError("Fallback must not be called when a tester already exists.");
        };

        final IDatabaseTester resolved =
                InjectedTestCaseTesterBinding.resolveTester(testCase, "field 'x'", fallback);

        assertThat(resolved).as("The already-set tester must be returned unchanged.")
                .isSameAs(existing);
    }

    @Test
    void testResolveTester_testCaseHasNoTesterAndRoundTrips_setsAndReturnsFallbackTester()
            throws Exception
    {
        final IDatabaseTester fallbackTester = mock(IDatabaseTester.class);
        final DefaultPrepAndExpectedTestCase testCase = new DefaultPrepAndExpectedTestCase();

        final IDatabaseTester resolved = InjectedTestCaseTesterBinding.resolveTester(testCase,
                "field 'x'", () -> fallbackTester);

        assertThat(resolved).as("The fallback tester must be returned.")
                .isSameAs(fallbackTester);
        assertThat(testCase.getDatabaseTester())
                .as("The fallback tester must also be wired onto the test case.")
                .isSameAs(fallbackTester);
    }

    @Test
    void testResolveTester_setDatabaseTesterDoesNotRoundTrip_throwsIllegalStateException()
    {
        final IDatabaseTester fallbackTester = mock(IDatabaseTester.class);
        final BrokenRoundTripTestCase testCase = new BrokenRoundTripTestCase();

        assertThatThrownBy(() -> InjectedTestCaseTesterBinding.resolveTester(testCase,
                "field 'testCase' in com.example.MyTest", () -> fallbackTester))
                .as("A setDatabaseTester() override that does not round-trip must fail fast"
                        + " rather than silently leave operations targeting the wrong tester.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("field 'testCase' in com.example.MyTest")
                .hasMessageContaining("does not return the same instance");
    }

    /**
     * Overrides {@code setDatabaseTester()} but deliberately does not delegate to {@code super},
     * so the inherited {@code getDatabaseTester()} never reflects it.
     */
    private static class BrokenRoundTripTestCase extends DefaultPrepAndExpectedTestCase
    {
        @Override
        public void setDatabaseTester(final IDatabaseTester databaseTester)
        {
        }
    }
}
