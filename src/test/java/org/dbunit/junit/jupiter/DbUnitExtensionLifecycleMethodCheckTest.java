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
package org.dbunit.junit.jupiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.stream.Collectors;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Proves, through {@link EngineTestKit}, that {@link DbUnitExtension} rejects a dbUnit annotation
 * on a lifecycle method before JUnit Jupiter runs the user's lifecycle methods, as a lifecycle
 * method that carries the annotation fails for lack of the data it was meant to load, and its
 * own failure would hide the message that names the annotation. An extension registered on the
 * test class rejects it for the whole class, before its first test; one registered on the test
 * method or on a field rejects it for each test it serves.
 */
class DbUnitExtensionLifecycleMethodCheckTest
{
    @Test
    void testBeforeEach_extensionRegisteredOnTheTestMethodAndAnnotatedBeforeEachFails_testFailsWithTheMessageNamingTheAnnotation()
    {
        final EngineExecutionResults results =
                execute(PrepOnFailingBeforeEachExtensionOnMethod.class);

        final List<Throwable> failures = failuresOf(results.testEvents().failed().list());
        assertThat(failures).as("One test fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("The check must run ahead of the @BeforeEach method, so its message replaces"
                        + " the method's own failure. An extension registered on the method alone"
                        + " gets beforeEach() but not beforeAll().")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method")
                .hasMessageContaining(PrepOnFailingBeforeEachExtensionOnMethod.class.getName()
                        + "#loadFixture()");
    }

    @Test
    void testBeforeEach_extensionRegisteredOnAFieldAndAnnotatedBeforeEachFails_testFailsWithTheMessageNamingTheAnnotation()
    {
        final EngineExecutionResults results =
                execute(PrepOnFailingBeforeEachExtensionOnField.class);

        final List<Throwable> failures = failuresOf(results.testEvents().failed().list());
        assertThat(failures).as("One test fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("An extension that a field registers gets beforeEach(), not beforeAll().")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method");
    }

    @Test
    void testBeforeAll_extensionRegisteredOnTheClassAndAnnotatedBeforeEachFails_classFailsBeforeAnyTestRuns()
    {
        final EngineExecutionResults results = execute(PrepOnFailingBeforeEach.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("The check runs before the @BeforeAll and @BeforeEach methods, so its message"
                        + " replaces their own failures.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method")
                .hasMessageContaining(PrepOnFailingBeforeEach.class.getName() + "#loadFixture()");
        assertThat(results.testEvents().started().count())
                .as("A class with a misplaced annotation runs none of its tests.").isZero();
    }

    @Test
    void testBeforeAll_annotatedBeforeAllMethodFails_classFailsWithTheMessageNamingTheAnnotation()
    {
        final EngineExecutionResults results = execute(PrepOnFailingBeforeAll.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("The check must run ahead of the @BeforeAll method, so its message replaces"
                        + " the method's own failure.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method")
                .hasMessageContaining(PrepOnFailingBeforeAll.class.getName() + "#loadFixture()");
    }

    @Test
    void testBeforeAll_annotatedBeforeParameterizedClassInvocationMethod_classFailsBeforeAnyTestRuns()
    {
        final EngineExecutionResults results =
                execute(PrepOnFailingBeforeParameterizedClassInvocation.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("The method of a @ParameterizedClass runs around each invocation, and nothing"
                        + " reads a dbUnit annotation on it.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method")
                .hasMessageContaining(
                        PrepOnFailingBeforeParameterizedClassInvocation.class.getName()
                                + "#loadFixture()");
        assertThat(results.testEvents().started().count())
                .as("A class with a misplaced annotation runs none of its tests.").isZero();
    }

    @Test
    void testBeforeAll_annotatedBeforeEachOfASuperclassOverriddenByABeforeEach_classFailsNamingTheSuperclassMethod()
    {
        final EngineExecutionResults results = execute(OverridesWithBeforeEach.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("JUnit runs only the overriding method, so a check that read it alone passed"
                        + " the tests with the annotation unread, a false pass.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SuperWithExpectedOnBeforeEach.class.getName() + "#verify()");
    }

    @Test
    void testBeforeAll_annotatedBeforeEachOfASuperclassOverriddenByAPlainMethod_classFailsNamingTheSuperclassMethod()
    {
        final EngineExecutionResults results = execute(OverridesWithoutAnnotation.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("JUnit then runs no verify() at all, which does not make the annotation"
                        + " read.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SuperWithExpectedOnBeforeEach.class.getName() + "#verify()");
    }

    private static EngineExecutionResults execute(final Class<?> testClass)
    {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute();
    }

    private static List<Throwable> failuresOf(final List<Event> events)
    {
        return events.stream().map(event -> event.getRequiredPayload(TestExecutionResult.class))
                .map(result -> result.getThrowable().orElse(null)).collect(Collectors.toList());
    }

    @ExtendWith(DbUnitExtension.class)
    static class PrepOnFailingBeforeEach
    {
        @BeforeEach
        @DbUnitPrep("/prep.xml")
        void loadFixture()
        {
            throw new IllegalArgumentException("the fixture lacks the prep data");
        }

        @Test
        void test()
        {
        }
    }

    static class PrepOnFailingBeforeEachExtensionOnMethod
    {
        @BeforeEach
        @DbUnitPrep("/prep.xml")
        void loadFixture()
        {
            throw new IllegalArgumentException("the fixture lacks the prep data");
        }

        @Test
        @ExtendWith(DbUnitExtension.class)
        void test()
        {
        }
    }

    static class PrepOnFailingBeforeEachExtensionOnField
    {
        @RegisterExtension
        DbUnitExtension extension = new DbUnitExtension();

        @BeforeEach
        @DbUnitPrep("/prep.xml")
        void loadFixture()
        {
            throw new IllegalArgumentException("the fixture lacks the prep data");
        }

        @Test
        void test()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class PrepOnFailingBeforeAll
    {
        @BeforeAll
        @DbUnitPrep("/prep.xml")
        static void loadFixture()
        {
            throw new IllegalArgumentException("the fixture lacks the prep data");
        }

        @Test
        void test()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ParameterizedClass
    @ValueSource(ints = {1, 2})
    static class PrepOnFailingBeforeParameterizedClassInvocation
    {
        @Parameter
        int value;

        @BeforeParameterizedClassInvocation
        @DbUnitPrep("/prep.xml")
        static void loadFixture()
        {
            throw new IllegalArgumentException("the fixture lacks the prep data");
        }

        @Test
        void test()
        {
        }
    }

    static class SuperWithExpectedOnBeforeEach
    {
        @BeforeEach
        @DbUnitExpected("/expected.xml")
        void verify()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class OverridesWithBeforeEach extends SuperWithExpectedOnBeforeEach
    {
        @Override
        @BeforeEach
        void verify()
        {
        }

        @Test
        void test()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class OverridesWithoutAnnotation extends SuperWithExpectedOnBeforeEach
    {
        @Override
        void verify()
        {
        }

        @Test
        void test()
        {
        }
    }
}
