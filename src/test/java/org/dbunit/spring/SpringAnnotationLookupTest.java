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

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;

class SpringAnnotationLookupTest
{
    @Test
    void testFind_annotationOnMethodOnly_returnsMethodAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new MethodLevelTearDown(), "aTestMethod");

        final DbUnitTearDown found = lookup.find(DbUnitTearDown.class);

        assertThat(found).as("An annotation on the test method must be found.").isNotNull();
        assertThat(found.operation()).as("The method's own operation must be returned.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_annotationOnMethodAndClass_methodWins() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ClassAndMethodLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A method-level annotation must win over a class-level one.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_annotationOnClassOnly_returnsClassAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new ClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A class-level annotation must be found when the method has none.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_annotationOnSuperclass_returnsInheritedAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new SubclassOfClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A class-level annotation must be found through the superclass.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_composedAnnotationOnClass_returnsMetaAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("An annotation meta-present on a composed class annotation must be found.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_composedAnnotationOnMethod_returnsMetaAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedMethodLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("An annotation meta-present on a composed method annotation must be found.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_overridingMethodDeclaresNothing_ignoresOverriddenMethodsAnnotation()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new OverridingWithoutAnnotation(), "aTestMethod");

        assertThat(lookup.find(DbUnitExpected.class))
                .as("The method is searched on its own, as under DbUnitExtension, so an"
                        + " overriding test method must not inherit the @DbUnitExpected of the"
                        + " method it overrides.")
                .isNull();
    }

    @Test
    void testFind_nestedClassWithoutAnnotation_returnsEnclosingClassesAnnotation()
            throws Exception
    {
        final EnclosingWithTearDown enclosing = new EnclosingWithTearDown();
        final SpringAnnotationLookup lookup = lookupFor(enclosing.new Nested(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class).operation())
                .as("A @Nested class inherits its enclosing class's annotations, as Spring"
                        + " inherits the rest of the enclosing configuration.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_nothingDeclared_returnsNull() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new NothingDeclared(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDown.class))
                .as("With no annotation on the method or class, there is nothing to find.")
                .isNull();
    }

    private static SpringAnnotationLookup lookupFor(final Object testInstance,
            final String testMethodName) throws Exception
    {
        final FakeTestContext context = new FakeTestContext(testInstance.getClass())
                .beginMethod(testInstance, testMethodName);
        return new SpringAnnotationLookup(context);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    @interface DeletesAllRows
    {
    }

    static class MethodLevelTearDown
    {
        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
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

    @DeletesAllRows
    static class ComposedClassLevelTearDown
    {
        void aTestMethod()
        {
        }
    }

    static class ComposedMethodLevelTearDown
    {
        @DeletesAllRows
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

    /**
     * The inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class EnclosingWithTearDown
    {
        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithTearDown.this;
            }

            void aTestMethod()
            {
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
