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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;

class JupiterLifecycleMethodsTest
{
    @Test
    void testOf_classDeclaresAllFourLifecycleAnnotations_listsThemAndNotTestOrHelperMethods()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(Declares.class);

        assertThat(namesOf(methods))
                .as("Only the four lifecycle annotations make a method a lifecycle method.")
                .containsExactlyInAnyOrder("beforeEachMethod", "afterEachMethod",
                        "beforeAllMethod", "afterAllMethod");
    }

    @Test
    void testOf_lifecycleMethodsInheritedFromSuperclassAndInterface_listsThemToo()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(Inherits.class);

        assertThat(namesOf(methods))
                .as("JUnit runs the lifecycle methods of superclasses and the default methods of"
                        + " interfaces, those of a super-interface too.")
                .containsExactlyInAnyOrder("superclassBeforeEach", "interfaceAfterEach",
                        "superInterfaceBeforeEach");
    }

    @Test
    void testOf_lifecycleAnnotationAsMetaAnnotation_listsTheMethod()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(Composes.class);

        assertThat(namesOf(methods))
                .as("A composed annotation that carries @BeforeEach makes a lifecycle method.")
                .containsExactly("composedSetUp");
    }

    @Test
    void testOf_parameterizedClassInvocationMethods_listsThem()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(HasInvocationMethods.class);

        assertThat(namesOf(methods))
                .as("JUnit runs @BeforeParameterizedClassInvocation and"
                        + " @AfterParameterizedClassInvocation methods around each invocation of"
                        + " a @ParameterizedClass, and no dbUnit annotation is read on them.")
                .containsExactlyInAnyOrder("beforeInvocationMethod", "afterInvocationMethod");
    }

    @Test
    void testOf_invocationAnnotationAsMetaAnnotation_listsTheMethod()
    {
        final Collection<Method> methods =
                JupiterLifecycleMethods.of(ComposesBeforeInvocation.class);

        assertThat(namesOf(methods))
                .as("A composed annotation that carries @BeforeParameterizedClassInvocation makes"
                        + " a lifecycle method.")
                .containsExactly("composedSetUp");
    }

    @Test
    void testOf_classWithoutLifecycleMethods_returnsEmpty()
    {
        assertThat(JupiterLifecycleMethods.of(Plain.class))
                .as("A class with no lifecycle methods lists none.")
                .isEmpty();
    }

    @Test
    void testOf_superclassBeforeEachOverriddenByABeforeEach_listsTheOverriddenMethodToo()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(OverridesWithBeforeEach.class);

        assertThat(declaredNamesOf(methods))
                .as("JUnit runs only the overriding method, but an annotation of dbUnit on the"
                        + " overridden one is as unread, so both are listed.")
                .containsExactlyInAnyOrder("OverridesWithBeforeEach#verify",
                        "SuperWithBeforeEach#verify");
    }

    @Test
    void testOf_superclassBeforeEachOverriddenByAPlainMethod_listsTheSuperclassMethod()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(OverridesWithoutAnnotation.class);

        assertThat(declaredNamesOf(methods))
                .as("JUnit runs no verify() at all then, and the annotation on the superclass"
                        + " method is as unread.")
                .containsExactly("SuperWithBeforeEach#verify");
    }

    @Test
    void testOf_subclassBeforeEachOverridesAMethodWithoutTheAnnotation_listsTheOverridingMethodOnly()
    {
        final Collection<Method> methods = JupiterLifecycleMethods.of(AddsBeforeEachToAnOverride.class);

        assertThat(declaredNamesOf(methods))
                .as("A method counts where it is declared, and the superclass method is no"
                        + " lifecycle method.")
                .containsExactly("AddsBeforeEachToAnOverride#helper");
    }

    private static Collection<String> declaredNamesOf(final Collection<Method> methods)
    {
        return methods.stream()
                .map(method -> method.getDeclaringClass().getSimpleName() + "#" + method.getName())
                .collect(Collectors.toList());
    }

    private static Collection<String> namesOf(final Collection<Method> methods)
    {
        return methods.stream().map(Method::getName).collect(Collectors.toList());
    }

    static class Declares
    {
        @BeforeEach
        void beforeEachMethod()
        {
        }

        @AfterEach
        void afterEachMethod()
        {
        }

        @BeforeAll
        static void beforeAllMethod()
        {
        }

        @AfterAll
        static void afterAllMethod()
        {
        }

        void aTestMethod()
        {
        }

        void aHelperMethod()
        {
        }
    }

    static class Superclass
    {
        @BeforeEach
        void superclassBeforeEach()
        {
        }
    }

    interface SuperContract
    {
        @BeforeEach
        default void superInterfaceBeforeEach()
        {
        }
    }

    interface Contract extends SuperContract
    {
        @AfterEach
        default void interfaceAfterEach()
        {
        }
    }

    static class Inherits extends Superclass implements Contract
    {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @BeforeEach
    @interface ComposedBeforeEach
    {
    }

    static class HasInvocationMethods
    {
        @BeforeParameterizedClassInvocation
        static void beforeInvocationMethod()
        {
        }

        @AfterParameterizedClassInvocation
        static void afterInvocationMethod()
        {
        }

        void aHelperMethod()
        {
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @BeforeParameterizedClassInvocation
    @interface ComposedBeforeInvocation
    {
    }

    static class ComposesBeforeInvocation
    {
        @ComposedBeforeInvocation
        static void composedSetUp()
        {
        }
    }

    static class Composes
    {
        @ComposedBeforeEach
        void composedSetUp()
        {
        }
    }

    static class SuperWithBeforeEach
    {
        @BeforeEach
        void verify()
        {
        }
    }

    static class OverridesWithBeforeEach extends SuperWithBeforeEach
    {
        @Override
        @BeforeEach
        void verify()
        {
        }
    }

    static class OverridesWithoutAnnotation extends SuperWithBeforeEach
    {
        @Override
        void verify()
        {
        }
    }

    static class SuperWithHelper
    {
        void helper()
        {
        }
    }

    static class AddsBeforeEachToAnOverride extends SuperWithHelper
    {
        @Override
        @BeforeEach
        void helper()
        {
        }
    }

    static class Plain
    {
        void aTestMethod()
        {
        }
    }
}
