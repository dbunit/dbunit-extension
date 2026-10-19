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

import java.util.Objects;
import java.util.concurrent.Callable;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;

/**
 * Resolves the {@link IDatabaseTester} for an already-injected {@code @DbUnitTestCase}
 * {@link DefaultPrepAndExpectedTestCase}: {@link DefaultPrepAndExpectedTestCase#getDatabaseTester()}
 * if it already has one, otherwise a caller-supplied fallback, wired back onto the test case and
 * round-trip-verified.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener} - can reach it too, sharing
 * the one round-trip-verification rule rather than each re-implementing it: a test case whose
 * {@code getDatabaseTester()} does not return the instance it was just given by
 * {@code setDatabaseTester()} is a bug (an operation applied to the resolved tester would
 * silently never reach the tester the test case actually uses) and fails fast.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class InjectedTestCaseTesterBinding
{
    private InjectedTestCaseTesterBinding()
    {
    }

    /**
     * Resolves and wires the tester for {@code testCase}.
     *
     * @param testCase The already-injected test case, resolved and type-checked by the caller.
     * @param fieldDescription Where {@code testCase} came from, for an exception or log message
     *            - e.g. {@code "field 'testCase' in com.example.MyTest"}.
     * @param fallbackTester Supplies a tester when {@code testCase.getDatabaseTester()} is
     *            {@code null}; not called otherwise.
     * @return The resolved tester: {@code testCase.getDatabaseTester()} if already set, otherwise
     *         {@code fallbackTester}'s value.
     * @throws Exception If {@code fallbackTester} fails.
     * @throws IllegalStateException If {@code testCase} does not round-trip
     *             {@code getDatabaseTester()} back to the instance it was just given.
     */
    public static IDatabaseTester resolveTester(final DefaultPrepAndExpectedTestCase testCase,
            final String fieldDescription, final Callable<IDatabaseTester> fallbackTester)
            throws Exception
    {
        final IDatabaseTester existing = testCase.getDatabaseTester();
        if (existing != null)
        {
            return existing;
        }

        final IDatabaseTester tester = Objects.requireNonNull(fallbackTester.call(),
                "fallbackTester must supply an IDatabaseTester.");

        // The executor drives this exact, already-injected instance directly rather than
        // constructing a fresh one, so the fallback tester must be wired onto it here.
        testCase.setDatabaseTester(tester);
        if (testCase.getDatabaseTester() == tester)
        {
            return tester;
        }

        throw new IllegalStateException(fieldDescription
                + " is annotated @DbUnitTestCase; its value's type ("
                + testCase.getClass().getName() + ") overrides setDatabaseTester(), but"
                + " getDatabaseTester() does not return the same instance right after being"
                + " given it. @DbUnitSetUpOperation/@DbUnitTearDownOperation operations set on"
                + " the resolved IDatabaseTester would silently never reach the operations this"
                + " test case actually runs. Fix setDatabaseTester()/getDatabaseTester() to"
                + " round-trip the same instance.");
    }
}
