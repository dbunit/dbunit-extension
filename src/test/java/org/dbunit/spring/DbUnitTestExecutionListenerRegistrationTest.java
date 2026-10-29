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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * Pins how Spring registers {@link DbUnitTestExecutionListener} for a test class: as one of its
 * default listeners, named in {@code META-INF/spring.factories}, and explicitly through
 * {@link DbUnitSpringTest @DbUnitSpringTest}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class DbUnitTestExecutionListenerRegistrationTest
{
    @Test
    void testGetTestExecutionListeners_noRegistrationOfItsOwn_registersTheListenerOnce()
    {
        assertThat(dbUnitListenersOf(NoRegistration.class))
                .as("A class that keeps Spring's default listeners gets the dbUnit listener as"
                        + " one of them, with no annotation of its own.")
                .isEqualTo(1);
    }

    @Test
    void testGetTestExecutionListeners_ownDeclarationWinsOverDbUnitSpringTest_registersTheListenerOnce()
    {
        assertThat(dbUnitListenersOf(OwnDeclarationAndDbUnitSpringTest.class))
                .as("A class whose own @TestExecutionListeners declaration wins over the one"
                        + " @DbUnitSpringTest carries still gets the dbUnit listener, as long as"
                        + " the declaration merges with Spring's defaults.")
                .isEqualTo(1);
    }

    @Test
    void testGetTestExecutionListeners_dbUnitSpringTest_registersTheListenerOnce()
    {
        assertThat(dbUnitListenersOf(DbUnitSpringTestOnly.class))
                .as("The explicit registration and the default one make one listener, not two.")
                .isEqualTo(1);
    }

    @Test
    void testGetTestExecutionListeners_ownDeclarationReplacingTheDefaults_registersNoListener()
    {
        assertThat(dbUnitListenersOf(OwnDeclarationReplacingTheDefaults.class))
                .as("A declaration in REPLACE_DEFAULTS mode drops every default listener, the"
                        + " dbUnit listener included; such a class names it itself.")
                .isZero();
    }

    @Test
    void testGetTestExecutionListeners_replacingDeclarationNamingTheListener_registersTheListenerOnce()
    {
        assertThat(dbUnitListenersOf(ReplacingDefaultsAndNamingTheListener.class))
                .as("A declaration that replaces the defaults and names the listener once"
                        + " registers it once.")
                .isEqualTo(1);
        assertThat(dependencyInjectionListenersOf(ReplacingDefaultsAndNamingTheListener.class))
                .as("The declaration still drops Spring's own listeners, dependency injection"
                        + " included, which naming the dbUnit listener does not bring back.")
                .isZero();
    }

    @Test
    void testGetTestExecutionListeners_mergingDeclaration_keepsSpringsOwnListeners()
    {
        assertThat(dependencyInjectionListenersOf(DbUnitSpringTestOnly.class))
                .as("A declaration that merges with the defaults keeps Spring's own listeners.")
                .isEqualTo(1);
    }

    @Test
    void testGetTestExecutionListeners_subclassOfReplacingDeclarationWithoutOwn_registersTheListenerOnce()
    {
        assertThat(dbUnitListenersOf(InheritsReplacingDeclaration.class))
                .as("A subclass with no declaration of its own inherits the one declaration.")
                .isEqualTo(1);
    }

    @Test
    void testGetTestExecutionListeners_subclassNamingTheListenerAgain_registersTwoInstances()
    {
        assertThat(dbUnitListenersOf(NamesTheListenerAgain.class))
                .as("Spring removes a duplicate listener only when it merges with the defaults,"
                        + " so a subclass of a replacing declaration that names the listener"
                        + " again gets a second instance, which the listener copes with.")
                .isEqualTo(2);
    }

    @Test
    void testGetTestExecutionListeners_subclassWithDbUnitSpringTestUnderReplacingDeclaration_registersTwoInstances()
    {
        assertThat(dbUnitListenersOf(DbUnitSpringTestUnderReplacingDeclaration.class))
                .as("The default registration cannot merge in under a replacing declaration, so"
                        + " @DbUnitSpringTest on a subclass adds a second instance.")
                .isEqualTo(2);
    }

    private static long dbUnitListenersOf(final Class<?> testClass)
    {
        final TestContextManager manager = new TestContextManager(testClass);
        final List<TestExecutionListener> listeners = manager.getTestExecutionListeners();
        return listeners.stream()
                .filter(listener -> listener instanceof DbUnitTestExecutionListener).count();
    }

    private static long dependencyInjectionListenersOf(final Class<?> testClass)
    {
        final TestContextManager manager = new TestContextManager(testClass);
        final List<TestExecutionListener> listeners = manager.getTestExecutionListeners();
        return listeners.stream()
                .filter(listener -> listener instanceof DependencyInjectionTestExecutionListener)
                .count();
    }

    /**
     * A listener of a project or a library, such as the one Spring Batch's
     * {@code @SpringBatchTest} declares.
     */
    static class SomeOtherListener extends AbstractTestExecutionListener
    {
    }

    static class NoRegistration
    {
    }

    @DbUnitSpringTest
    @TestExecutionListeners(listeners = SomeOtherListener.class,
            mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
    static class OwnDeclarationAndDbUnitSpringTest
    {
    }

    @DbUnitSpringTest
    static class DbUnitSpringTestOnly
    {
    }

    @TestExecutionListeners(SomeOtherListener.class)
    static class OwnDeclarationReplacingTheDefaults
    {
    }

    @TestExecutionListeners(listeners = {SomeOtherListener.class,
            DbUnitTestExecutionListener.class})
    static class ReplacingDefaultsAndNamingTheListener
    {
    }

    static class InheritsReplacingDeclaration extends ReplacingDefaultsAndNamingTheListener
    {
    }

    @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class)
    static class NamesTheListenerAgain extends ReplacingDefaultsAndNamingTheListener
    {
    }

    @DbUnitSpringTest
    static class DbUnitSpringTestUnderReplacingDeclaration
            extends ReplacingDefaultsAndNamingTheListener
    {
    }
}
