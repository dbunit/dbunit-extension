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
package org.dbunit.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.stream.Collectors;

import org.dbunit.annotation.DbUnitPrep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Proves, through {@link EngineTestKit} and Spring's {@code SpringExtension}, that
 * {@link DbUnitTestExecutionListener} rejects a dbUnit annotation on a lifecycle method before
 * JUnit Jupiter runs the user's lifecycle methods, as a lifecycle method that carries the
 * annotation fails for lack of the data it was meant to load, and its own failure would hide the
 * message that names the annotation.
 */
class DbUnitTestExecutionListenerLifecycleMethodCheckTest
{
    @Test
    void testBeforeTestClass_annotatedBeforeAllMethodFails_classFailsWithTheMessageNamingTheAnnotation()
    {
        final EngineExecutionResults results = execute(PrepOnFailingBeforeAll.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("SpringExtension runs beforeTestClass() ahead of the @BeforeAll methods, so"
                        + " the message replaces the method's own failure.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method")
                .hasMessageContaining(PrepOnFailingBeforeAll.class.getName() + "#loadFixture()");
    }

    @Test
    void testBeforeTestClass_annotatedBeforeEachMethodFails_classFailsBeforeAnyTestRuns()
    {
        final EngineExecutionResults results = execute(PrepOnFailingBeforeEach.class);

        final List<Throwable> failures = failuresOf(results.containerEvents().failed().list());
        assertThat(failures).as("The class container fails, with one exception.").hasSize(1);
        assertThat(failures.get(0))
                .as("The message replaces the failure of the @BeforeEach method, which would"
                        + " have run first for lack of its data.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method")
                .hasMessageContaining(PrepOnFailingBeforeEach.class.getName() + "#loadFixture()");
        assertThat(results.testEvents().started().count())
                .as("A class with a misplaced annotation runs none of its tests.").isZero();
    }

    @Test
    void testBeforeTestClass_annotatedBeforeParameterizedClassInvocationMethod_classFailsBeforeAnyTestRuns()
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

    private static EngineExecutionResults execute(final Class<?> testClass)
    {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute();
    }

    private static List<Throwable> failuresOf(final List<Event> events)
    {
        return events.stream().map(event -> event.getRequiredPayload(TestExecutionResult.class))
                .map(result -> result.getThrowable().orElse(null)).collect(Collectors.toList());
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {})
    @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class,
            mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
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

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {})
    @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class,
            mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
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

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {})
    @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class,
            mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
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
}
