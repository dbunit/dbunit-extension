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

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@link IDatabaseTester} for an already-injected {@code @DbUnitTestCase} instance:
 * {@link PrepAndExpectedTestCase#getDatabaseTester()} if it already has one, otherwise a
 * caller-supplied fallback, wired back onto the test case and round-trip-verified.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener} - can reach it too, sharing
 * the one round-trip-verification rule rather than each re-implementing it: a test case whose
 * type overrides {@code setDatabaseTester()} but does not round-trip
 * {@code getDatabaseTester()} back to the same instance is a bug (an operation applied to the
 * resolved tester would silently never reach the tester the test case actually uses) and fails
 * fast, while one that never overrides {@code setDatabaseTester()} at all is the documented
 * self-managed-connection pattern and only logged.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class InjectedTestCaseTesterBinding
{
    private static final Logger log = LoggerFactory.getLogger(InjectedTestCaseTesterBinding.class);

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
     * @throws IllegalStateException If {@code testCase}'s type overrides
     *             {@code setDatabaseTester()} but does not round-trip {@code getDatabaseTester()}
     *             back to the same instance.
     */
    public static IDatabaseTester resolveTester(final PrepAndExpectedTestCase testCase,
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
        // constructing a fresh one, so the fallback tester must be wired onto it here -
        // otherwise it keeps whatever databaseTester it reports for an implementation that does
        // not override setDatabaseTester().
        testCase.setDatabaseTester(tester);
        if (testCase.getDatabaseTester() == tester)
        {
            return tester;
        }

        if (overridesSetDatabaseTester(testCase))
        {
            // Opted into automatic wiring by overriding setDatabaseTester(), so a round-trip
            // failure means the override itself is broken - fail fast the same way every other
            // @DbUnitConfig-driven setter does for a silent no-op, rather than let
            // @DbUnitSetup/@DbUnitTearDown operations quietly target a tester this test case
            // never actually uses.
            throw new IllegalStateException(fieldDescription
                    + " is annotated @DbUnitTestCase; its value's type ("
                    + testCase.getClass().getName() + ") overrides setDatabaseTester(), but"
                    + " getDatabaseTester() does not return the same instance right after being"
                    + " given it. @DbUnitSetup/@DbUnitTearDown operations set on the resolved"
                    + " IDatabaseTester would silently never reach the operations this test case"
                    + " actually runs. Fix setDatabaseTester()/getDatabaseTester() to round-trip"
                    + " the same instance.");
        }
        // A test case that never overrides setDatabaseTester() at all is the documented
        // self-managed-connection pattern, not a bug - see annotations.adoc - so this stays a
        // diagnostic log, not a failure.
        log.debug("PrepAndExpectedTestCase {} does not round-trip getDatabaseTester()/"
                + "setDatabaseTester(); @DbUnitSetup/@DbUnitTearDown operations set on the"
                + " resolved IDatabaseTester may not reach the operations this test case actually"
                + " runs unless it independently uses the same tester instance.",
                testCase.getClass().getName());
        return tester;
    }

    /**
     * Returns whether {@code testCase}'s runtime type overrides
     * {@link PrepAndExpectedTestCase#setDatabaseTester(IDatabaseTester)}, rather than inheriting
     * the interface's own no-op default body. See
     * {@link DefaultMethodOverrideCheck#overridesDefaultMethod} for this check's own known
     * limitation.
     */
    private static boolean overridesSetDatabaseTester(final PrepAndExpectedTestCase testCase)
    {
        return DefaultMethodOverrideCheck.overridesDefaultMethod(testCase.getClass(),
                PrepAndExpectedTestCase.class, "setDatabaseTester", IDatabaseTester.class);
    }
}
