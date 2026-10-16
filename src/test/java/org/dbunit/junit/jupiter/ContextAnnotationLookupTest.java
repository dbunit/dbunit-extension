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
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Unit tests of {@link ContextAnnotationLookup}. How the declaring class is found is
 * {@code DeclaringClassFinderTest}'s; these tests cover that the lookup hands the finder the
 * context's method, test class and enclosing classes, and the search order of {@code find}.
 *
 * <p>The contexts are {@link NestedContextChain} stubs, so the enclosing classes come only from
 * the parent contexts: {@link ExtensionContext#getEnclosingTestClasses()} is not available before
 * JUnit Jupiter 5.13.3 and fails here as it would on an older runtime.
 */
class ContextAnnotationLookupTest
{
    @Test
    void testFind_annotationOnMethodOnly_returnsMethodAnnotation() throws Exception
    {
        final ContextAnnotationLookup lookup =
                lookupFor(MethodLevelTearDown.class, "aTestMethod");

        final DbUnitTearDown found = lookup.find(DbUnitTearDown.class);

        assertThat(found).as("An annotation on the test method must be found.").isNotNull();
        assertThat(found.operation()).as("The method's own operation must be returned.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_annotationOnMethodAndClass_methodWins() throws Exception
    {
        final ContextAnnotationLookup lookup =
                lookupFor(ClassAndMethodLevelTearDown.class, "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A method-level annotation must win over a class-level one.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_annotationOnSuperclass_returnsInheritedAnnotation() throws Exception
    {
        final ContextAnnotationLookup lookup =
                lookupFor(SubclassOfClassLevelTearDown.class, "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A class-level annotation must be found through the superclass.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_nestedClassWithoutAnnotation_returnsEnclosingClassesAnnotation()
            throws Exception
    {
        final ContextAnnotationLookup lookup = lookupFor(EnclosingWithTearDown.Nested.class,
                "aTestMethod", EnclosingWithTearDown.class);

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A @Nested class must find its enclosing class's annotations.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_nestedTwiceWithBothEnclosingClassesAnnotated_returnsTheClosestOnesAnnotation()
            throws Exception
    {
        final ContextAnnotationLookup lookup = lookupFor(Outermost.Middle.Innermost.class,
                "aTestMethod", Outermost.class, Outermost.Middle.class);

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("The annotation of the enclosing class closest to the test class must win"
                        + " over that of a farther one.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_overridingMethodDeclaresNothing_ignoresOverriddenMethodsAnnotation()
            throws Exception
    {
        final ContextAnnotationLookup lookup =
                lookupFor(OverridingWithoutAnnotation.class, "aTestMethod");

        assertThat(lookup.find(DbUnitExpected.class))
                .as("The method is searched on its own, so an overriding test method must not"
                        + " inherit the @DbUnitExpected of the method it overrides.")
                .isNull();
    }

    @Test
    void testFind_nothingDeclared_returnsNull() throws Exception
    {
        final ContextAnnotationLookup lookup = lookupFor(NothingDeclared.class, "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class))
                .as("With no annotation on the method or class, there is nothing to find.")
                .isNull();
    }

    @Test
    void testFindDeclaringClass_annotationOnInheritedTestMethod_returnsTheSuperclassDeclaringIt()
            throws Exception
    {
        final Method inheritedMethod = MethodLevelTearDown.class.getDeclaredMethod("aTestMethod");
        final ContextAnnotationLookup lookup =
                lookupFor(InheritsMethodLevelTearDown.class, inheritedMethod);

        assertThat(lookup.findDeclaringClass(DbUnitTearDown.class))
                .as("The lookup must hand the finder the context's test method.")
                .isEqualTo(MethodLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnSuperclass_returnsTheSuperclass() throws Exception
    {
        final ContextAnnotationLookup lookup =
                lookupFor(SubclassOfClassLevelTearDown.class, "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDown.class))
                .as("The lookup must hand the finder the context's test class.")
                .isEqualTo(ClassLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnEnclosingClass_returnsTheEnclosingClass()
            throws Exception
    {
        final ContextAnnotationLookup lookup = lookupFor(EnclosingWithTearDown.Nested.class,
                "aTestMethod", EnclosingWithTearDown.class);

        assertThat(lookup.findDeclaringClass(DbUnitTearDown.class))
                .as("The lookup must hand the finder the context's enclosing classes.")
                .isEqualTo(EnclosingWithTearDown.class);
    }

    @Test
    void testFindDeclaringClass_nestedTwiceWithBothEnclosingClassesAnnotated_returnsTheClosestOne()
            throws Exception
    {
        final ContextAnnotationLookup lookup = lookupFor(Outermost.Middle.Innermost.class,
                "aTestMethod", Outermost.class, Outermost.Middle.class);

        assertThat(lookup.findDeclaringClass(DbUnitTearDown.class))
                .as("The declaring class must be the closest enclosing class declaring it, the"
                        + " one whose annotation find() returns.")
                .isEqualTo(Outermost.Middle.class);
    }

    @Test
    void testFindDeclaringClass_nothingDeclared_returnsTheTestClass() throws Exception
    {
        final ContextAnnotationLookup lookup = lookupFor(NothingDeclared.class, "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDown.class))
                .as("With no such annotation, the declaring class is the test class.")
                .isEqualTo(NothingDeclared.class);
    }

    private static ContextAnnotationLookup lookupFor(final Class<?> testClass,
            final String testMethodName, final Class<?>... enclosingClassesOutermostFirst)
            throws Exception
    {
        final Method testMethod = testClass.getDeclaredMethod(testMethodName);
        return lookupFor(testClass, testMethod, enclosingClassesOutermostFirst);
    }

    private static ContextAnnotationLookup lookupFor(final Class<?> testClass,
            final Method testMethod, final Class<?>... enclosingClassesOutermostFirst)
    {
        final ExtensionContext context =
                NestedContextChain.methodContextOf(testClass, enclosingClassesOutermostFirst);
        when(context.getTestMethod()).thenReturn(Optional.of(testMethod));
        return new ContextAnnotationLookup(context);
    }

    static class MethodLevelTearDown
    {
        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    static class InheritsMethodLevelTearDown extends MethodLevelTearDown
    {
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class ClassAndMethodLevelTearDown
    {
        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class ClassLevelTearDown
    {
        void aTestMethod()
        {
        }
    }

    static class SubclassOfClassLevelTearDown extends ClassLevelTearDown
    {
        @Override
        void aTestMethod()
        {
        }
    }

    static class ExpectedOnMethod
    {
        @DbUnitExpected("expected.xml")
        void aTestMethod()
        {
        }
    }

    static class OverridingWithoutAnnotation extends ExpectedOnMethod
    {
        @Override
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class EnclosingWithTearDown
    {
        class Nested
        {
            void aTestMethod()
            {
            }
        }
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class Outermost
    {
        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
        class Middle
        {
            class Innermost
            {
                void aTestMethod()
                {
                }
            }
        }
    }

    static class NothingDeclared
    {
        void aTestMethod()
        {
        }
    }
}
