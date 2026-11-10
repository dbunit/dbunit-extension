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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.stream.Collectors;

import org.dbunit.annotation.DbUnitExpected;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.transaction.AfterTransaction;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.springframework.test.context.transaction.BeforeTransaction;

class SpringLifecycleMethodsTest
{
    @Test
    void testOf_classDeclaresAllFourLifecycleAnnotations_listsThemAndNotTestOrHelperMethods()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(Declares.class);

        assertThat(namesOf(methods))
                .as("Only the four lifecycle annotations make a method a lifecycle method.")
                .containsExactlyInAnyOrder("beforeEachMethod", "afterEachMethod",
                        "beforeAllMethod", "afterAllMethod");
    }

    @Test
    void testOf_lifecycleMethodsInheritedFromSuperclassAndInterfaces_listsThemToo()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(Inherits.class);

        assertThat(namesOf(methods))
                .as("JUnit runs the lifecycle methods of superclasses and the default methods of"
                        + " interfaces, those of a super-interface too.")
                .containsExactlyInAnyOrder("superclassBeforeEach", "interfaceAfterEach",
                        "superInterfaceBeforeEach");
    }

    @Test
    void testOf_lifecycleAnnotationAsMetaAnnotation_listsTheMethod()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(Composes.class);

        assertThat(namesOf(methods))
                .as("A composed annotation that carries @BeforeEach makes a lifecycle method.")
                .containsExactly("composedSetUp");
    }

    @Test
    void testOf_classWithoutLifecycleMethods_returnsEmpty()
    {
        assertThat(SpringLifecycleMethods.of(Plain.class))
                .as("A class with no lifecycle methods lists none.")
                .isEmpty();
    }

    @Test
    void testOf_superclassBeforeEachOverriddenByABeforeEach_listsTheOverriddenMethodToo()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(OverridesWithBeforeEach.class);

        assertThat(declaredNamesOf(methods))
                .as("JUnit runs only the overriding method, but an annotation of dbUnit on the"
                        + " overridden one is as unread, so both are listed.")
                .containsExactlyInAnyOrder("OverridesWithBeforeEach#verify",
                        "SuperWithBeforeEach#verify");
    }

    @Test
    void testOf_superclassBeforeEachOverriddenByAPlainMethod_listsTheSuperclassMethod()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(OverridesWithoutAnnotation.class);

        assertThat(declaredNamesOf(methods))
                .as("JUnit runs no verify() at all then, and the annotation on the superclass"
                        + " method is as unread.")
                .containsExactly("SuperWithBeforeEach#verify");
    }

    @Test
    void testOf_subclassBeforeEachOverridesAMethodWithoutTheAnnotation_listsTheOverridingMethodOnly()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(AddsBeforeEachToAnOverride.class);

        assertThat(declaredNamesOf(methods))
                .as("A method counts where it is declared, and the superclass method is no"
                        + " lifecycle method.")
                .containsExactly("AddsBeforeEachToAnOverride#helper");
    }

    @Test
    void testOf_classDeclaresTransactionMethods_listsThem()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(HasTransactionMethods.class);

        assertThat(namesOf(methods))
                .as("Spring runs @BeforeTransaction and @AfterTransaction methods around the"
                        + " transaction of a test, and no dbUnit annotation is read on them.")
                .containsExactlyInAnyOrder("beforeTransactionMethod", "afterTransactionMethod");
    }

    @Test
    void testOf_parameterizedClassInvocationMethods_listsThem()
    {
        final Collection<Method> methods = SpringLifecycleMethods.of(HasInvocationMethods.class);

        assertThat(namesOf(methods))
                .as("JUnit runs @BeforeParameterizedClassInvocation and"
                        + " @AfterParameterizedClassInvocation methods around each invocation of"
                        + " a @ParameterizedClass, and no dbUnit annotation is read on them.")
                .containsExactlyInAnyOrder("beforeInvocationMethod", "afterInvocationMethod");
    }

    @Test
    void testDeclares_methodOverridesAnAnnotatedMethod_onlyTheDeclaringMethodCarriesIt()
            throws Exception
    {
        final Method overridden = AnnotatedSuper.class.getDeclaredMethod("verify");
        final Method overriding = OverridesAnnotatedSuper.class.getDeclaredMethod("verify");

        assertThat(SpringLifecycleMethods.declares(overridden, DbUnitExpected.class))
                .as("The superclass method declares the annotation.").isTrue();
        assertThat(SpringLifecycleMethods.declares(overriding, DbUnitExpected.class))
                .as("The overriding method does not declare it, although it overrides a method"
                        + " that does, so the message names the method that carries it.")
                .isFalse();
    }

    @Test
    void testDeclares_annotationAsMetaAnnotation_isTrue() throws Exception
    {
        final Method composed = Composes.class.getDeclaredMethod("composedSetUp");

        assertThat(SpringLifecycleMethods.declares(composed, BeforeEach.class))
                .as("A composed annotation that carries the annotation counts.").isTrue();
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

    static class HasTransactionMethods
    {
        @BeforeTransaction
        void beforeTransactionMethod()
        {
        }

        @AfterTransaction
        void afterTransactionMethod()
        {
        }
    }

    static class AnnotatedSuper
    {
        @DbUnitExpected("expected.xml")
        void verify()
        {
        }
    }

    static class OverridesAnnotatedSuper extends AnnotatedSuper
    {
        @Override
        void verify()
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
