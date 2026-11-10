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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.junit.jupiter.api.Test;

class LifecycleMethodAnnotationCheckTest
{
    @Test
    void testRequireNoneOn_lifecycleMethodCarriesExpected_throwsNamingMethodAndAnnotation()
            throws Exception
    {
        final Method misplaced = Host.class.getDeclaredMethod("verifyAfter");
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(
                type -> Arrays.asList(misplaced),
                (method, annotationType) -> method.isAnnotationPresent(annotationType));

        assertThatThrownBy(() -> check.requireNoneOn(Host.class))
                .as("The message must name the annotation, the method and where to move it.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitExpected")
                .hasMessageContaining(Host.class.getName() + "#verifyAfter()")
                .hasMessageContaining("test method");
    }

    @Test
    void testRequireNoneOn_lifecycleMethodCarriesPrep_throwsNamingTheAnnotation() throws Exception
    {
        final Method misplaced = Host.class.getDeclaredMethod("loadBefore");
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(
                type -> Arrays.asList(misplaced),
                (method, annotationType) -> method.isAnnotationPresent(annotationType));

        assertThatThrownBy(() -> check.requireNoneOn(Host.class))
                .as("Each of the five method-level annotations is rejected.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitPrep is on the lifecycle method");
    }

    @Test
    void testRequireNoneOn_lifecycleMethodsCarryNoDbUnitAnnotation_doesNotThrow()
            throws Exception
    {
        final Method plain = Host.class.getDeclaredMethod("plainSetUp");
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(
                type -> Arrays.asList(plain),
                (method, annotationType) -> method.isAnnotationPresent(annotationType));

        assertThatCode(() -> check.requireNoneOn(Host.class))
                .as("A lifecycle method without a dbUnit annotation is fine.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireNoneOn_noLifecycleMethods_doesNotThrow()
    {
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(
                type -> Collections.<Method> emptyList(), (method, annotationType) -> true);

        assertThatCode(() -> check.requireNoneOn(Host.class))
                .as("A class with no lifecycle methods has nothing to reject.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireNoneOn_sameClassTwice_looksUpItsLifecycleMethodsOnce() throws Exception
    {
        final AtomicInteger lookups = new AtomicInteger();
        final Method plain = Host.class.getDeclaredMethod("plainSetUp");
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(type -> {
            lookups.incrementAndGet();
            final Collection<Method> methods = Arrays.asList(plain);
            return methods;
        }, (method, annotationType) -> false);

        check.requireNoneOn(Host.class);
        check.requireNoneOn(Host.class);

        assertThat(lookups.get())
                .as("The answer for a class does not change between its tests, so it is"
                        + " computed once.")
                .isEqualTo(1);
    }

    @Test
    void testRequireNoneOn_misplacementCached_throwsOnEveryCall() throws Exception
    {
        final Method misplaced = Host.class.getDeclaredMethod("verifyAfter");
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(
                type -> Arrays.asList(misplaced),
                (method, annotationType) -> method.isAnnotationPresent(annotationType));

        assertThatThrownBy(() -> check.requireNoneOn(Host.class))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> check.requireNoneOn(Host.class))
                .as("Every test of the class must fail, not only the first.")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRequireNoneOn_annotatedSuperclassMethodAndItsOverride_throwsNamingTheSuperclassMethod()
            throws Exception
    {
        final Method overridden = SuperHost.class.getDeclaredMethod("verifyAfter");
        final Method overriding = SubHost.class.getDeclaredMethod("verifyAfter");
        final LifecycleMethodAnnotationCheck check = new LifecycleMethodAnnotationCheck(
                type -> Arrays.asList(overriding, overridden),
                (method, annotationType) -> method.isAnnotationPresent(annotationType));

        assertThatThrownBy(() -> check.requireNoneOn(SubHost.class))
                .as("A method that carries the annotation is the one named, whichever other"
                        + " lifecycle method overrides it.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SuperHost.class.getName() + "#verifyAfter()");
    }

    static class SuperHost
    {
        @DbUnitExpected("expected.xml")
        void verifyAfter()
        {
        }
    }

    static class SubHost extends SuperHost
    {
        @Override
        void verifyAfter()
        {
        }
    }

    static class Host
    {
        @DbUnitExpected("expected.xml")
        void verifyAfter()
        {
        }

        @DbUnitPrep("prep.xml")
        void loadBefore()
        {
        }

        void plainSetUp()
        {
        }
    }
}
