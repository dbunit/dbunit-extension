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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * A Spring test instance and, for a JUnit Jupiter {@code @Nested} test class, the enclosing
 * instances Spring's {@code TestContext} does not hand over: the instance of the class enclosing
 * the {@code @Nested} class, the instance enclosing that, and so on outward.
 *
 * <p>Spring's {@code TestContext} holds only the innermost instance, but a {@code @Nested}
 * class's tester field may be declared on an enclosing class, as it may be under
 * {@code DbUnitExtension}. An enclosing instance is read from the compiler-generated reference
 * every inner class holds to it. Since JDK 18, {@code javac} omits that reference from an inner
 * class that never uses its enclosing instance, so the chain then stops there, and
 * {@link #getUnreachableEnclosingClasses()} names the classes it could not reach. The chain also
 * stops, by design, where Spring does not inherit an enclosing class's configuration:
 * for a {@code static} nested class, or one annotated
 * {@code @NestedTestConfiguration(OVERRIDE)} - see
 * {@link TestContextAnnotationUtils#searchEnclosingClass(Class)}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class TestInstanceChain
{
    private final List<Object> innermostFirst;
    private final List<Class<?>> unreachableEnclosingClasses;

    private TestInstanceChain(final List<Object> innermostFirst,
            final List<Class<?>> unreachableEnclosingClasses)
    {
        this.innermostFirst = Collections.unmodifiableList(innermostFirst);
        this.unreachableEnclosingClasses =
                Collections.unmodifiableList(unreachableEnclosingClasses);
    }

    /**
     * Builds the chain that starts at {@code testInstance}.
     *
     * @param testInstance The innermost test instance, the one Spring's {@code TestContext}
     *            holds.
     * @return The chain.
     */
    static TestInstanceChain of(final Object testInstance)
    {
        final List<Object> instances = new ArrayList<>();
        Object current = testInstance;
        while (true)
        {
            instances.add(current);
            final Class<?> currentClass = current.getClass();
            if (!TestContextAnnotationUtils.searchEnclosingClass(currentClass))
            {
                return new TestInstanceChain(instances, Collections.emptyList());
            }
            final Object enclosing = readEnclosingInstance(current);
            if (enclosing == null)
            {
                final List<Class<?>> unreachable = enclosingClassesOf(currentClass);
                return new TestInstanceChain(instances, unreachable);
            }
            current = enclosing;
        }
    }

    /**
     * Returns the test class and, for a {@code @Nested} class, each class enclosing it whose
     * configuration Spring inherits, innermost first. Unlike a chain, it needs no instance, so
     * it is the same whether or not the enclosing instances can be reached.
     *
     * @param testClass The innermost test class.
     * @return The classes in scope.
     */
    static List<Class<?>> classesInScope(final Class<?> testClass)
    {
        final List<Class<?>> classes = new ArrayList<>();
        classes.add(testClass);
        classes.addAll(enclosingClassesOf(testClass));
        return classes;
    }

    /**
     * Returns the test instances in scope.
     *
     * @return The innermost instance first, then each reachable enclosing instance outward.
     */
    List<Object> getInnermostFirst()
    {
        return innermostFirst;
    }

    /**
     * Returns the enclosing classes whose instances could not be reached although Spring
     * inherits their configuration.
     *
     * @return The classes outward from the last reachable instance; empty when the chain is
     *         complete.
     */
    List<Class<?>> getUnreachableEnclosingClasses()
    {
        return unreachableEnclosingClasses;
    }

    private static List<Class<?>> enclosingClassesOf(final Class<?> innerClass)
    {
        final List<Class<?>> enclosingClasses = new ArrayList<>();
        Class<?> current = innerClass;
        while (TestContextAnnotationUtils.searchEnclosingClass(current))
        {
            current = current.getEnclosingClass();
            enclosingClasses.add(current);
        }
        return enclosingClasses;
    }

    /**
     * Reads the enclosing instance from the synthetic instance field of type
     * {@link Class#getEnclosingClass()} that {@code javac} generates in an inner class.
     *
     * @return The enclosing instance, or {@code null} when the class holds no such reference.
     */
    private static Object readEnclosingInstance(final Object innerInstance)
    {
        final Class<?> innerClass = innerInstance.getClass();
        final Class<?> enclosingClass = innerClass.getEnclosingClass();
        for (final Field field : innerClass.getDeclaredFields())
        {
            if (field.isSynthetic() && !Modifier.isStatic(field.getModifiers())
                    && field.getType() == enclosingClass)
            {
                field.setAccessible(true);
                try
                {
                    return field.get(innerInstance);
                } catch (final IllegalAccessException e)
                {
                    throw new IllegalStateException("Cannot read the enclosing instance of "
                            + innerClass.getName() + " from field '" + field.getName() + "'.", e);
                }
            }
        }
        return null;
    }
}
