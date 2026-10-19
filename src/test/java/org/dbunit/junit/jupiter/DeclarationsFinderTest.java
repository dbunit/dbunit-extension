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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;

class DeclarationsFinderTest
{
    @Test
    void testFindAll_annotationOnMethodAndClass_methodFirst() throws Exception
    {
        final DeclarationsFinder finder = finderFor(MethodAndClass.class);

        assertThat(operationsOf(finder)).as("The method's declaration is nearer than the class's.")
                .containsExactly(DbUnitOperation.TRUNCATE_TABLE, DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFindAll_annotationOnClassInterfaceAndSuperclass_inThatOrder()
    {
        final DeclarationsFinder finder = new DeclarationsFinder(Optional.empty(),
                OnClassInterfaceAndSuperclass.class, Collections.emptyList());

        assertThat(operationsOf(finder))
                .as("The class itself, then its interfaces, then its superclass, as JUnit looks.")
                .containsExactly(DbUnitOperation.REFRESH, DbUnitOperation.INSERT,
                        DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFindAll_directAndComposedOnOneClass_directFirst()
    {
        final DeclarationsFinder finder = new DeclarationsFinder(Optional.empty(),
                ComposedThenDirect.class, Collections.emptyList());

        assertThat(operationsOf(finder))
                .as("A declaration written on the class comes before one its composed annotation"
                        + " carries, whatever order they are written in.")
                .containsExactly(DbUnitOperation.DELETE_ALL, DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFindAll_nestedClass_closestEnclosingClassFirstAfterTheTestClass()
    {
        final DeclarationsFinder finder = new DeclarationsFinder(Optional.empty(),
                Outermost.Middle.Innermost.class,
                Arrays.<Class<?>>asList(Outermost.class, Outermost.Middle.class));

        assertThat(operationsOf(finder))
                .as("The enclosing class closest to the test class is nearer than a farther one.")
                .containsExactly(DbUnitOperation.TRUNCATE_TABLE, DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFindAll_overridingMethodDeclaresNothing_ignoresTheOverriddenMethodsAnnotation()
            throws Exception
    {
        final Method overriding =
                OverridingWithoutAnnotation.class.getDeclaredMethod("aTestMethod");
        final DeclarationsFinder finder = new DeclarationsFinder(Optional.of(overriding),
                OverridingWithoutAnnotation.class, Collections.emptyList());

        assertThat(finder.findAll(DbUnitExpected.class))
                .as("The method is searched on its own, not through the methods it overrides.")
                .isEmpty();
    }

    @Test
    void testFindAll_nothingDeclared_returnsEmpty()
    {
        final DeclarationsFinder finder = new DeclarationsFinder(Optional.empty(),
                NothingDeclared.class, Collections.emptyList());

        assertThat(finder.findAll(DbUnitTearDownOperation.class)).isEmpty();
    }

    private static DeclarationsFinder finderFor(final Class<?> testClass) throws Exception
    {
        final Method testMethod = testClass.getDeclaredMethod("aTestMethod");
        return new DeclarationsFinder(Optional.of(testMethod), testClass,
                Collections.emptyList());
    }

    private static List<DbUnitOperation> operationsOf(final DeclarationsFinder finder)
    {
        final List<DbUnitOperation> operations = new java.util.ArrayList<>();
        for (final DbUnitTearDownOperation found : finder.findAll(DbUnitTearDownOperation.class))
        {
            operations.add(found.value());
        }
        return operations;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
    @interface TruncatesTables
    {
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class MethodAndClass
    {
        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.INSERT)
    interface InsertsAfterwards
    {
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class DeletesAfterwards
    {
    }

    @DbUnitTearDownOperation(DbUnitOperation.REFRESH)
    static class OnClassInterfaceAndSuperclass extends DeletesAfterwards
            implements InsertsAfterwards
    {
    }

    @TruncatesTables
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class ComposedThenDirect
    {
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class Outermost
    {
        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        class Middle
        {
            class Innermost
            {
            }
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

    static class NothingDeclared
    {
    }
}
