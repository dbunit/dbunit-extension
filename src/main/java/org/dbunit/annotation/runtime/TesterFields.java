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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;

/**
 * Which fields of a test class are tester fields, and how to find them in its class hierarchy:
 * the one place the rules live that the opt-in check ({@link AnnotatedTestOptIn}), the
 * {@link TestInstanceTesterResolver} shared by the JUnit Jupiter and Spring bindings, and the
 * Spring binding's own checks for an unreachable enclosing instance all apply, so a change to
 * what counts as a tester field changes it for all of them at once.
 *
 * <p>A field is <em>marked</em> when it carries {@link DbUnitTester} or {@link DbUnitTestCase},
 * and <em>unmarked</em> when it is a non-static field assignable to {@link IDatabaseTester} - the
 * 3.5.0 auto-scan's rule.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as a Spring
 * {@code TestExecutionListener} - can reach it too.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class TesterFields
{
    /**
     * Returns whether the field is marked {@link DbUnitTester} or {@link DbUnitTestCase}.
     *
     * @param field The field to check.
     * @return {@code true} for a marked field.
     */
    public boolean isMarked(final Field field)
    {
        return field.isAnnotationPresent(DbUnitTester.class)
                || field.isAnnotationPresent(DbUnitTestCase.class);
    }

    /**
     * Returns whether the field is one the 3.5.0 auto-scan uses: non-static, and holding an
     * {@link IDatabaseTester}.
     *
     * @param field The field to check.
     * @return {@code true} for an unmarked tester field.
     */
    public boolean isUnmarkedTester(final Field field)
    {
        return !Modifier.isStatic(field.getModifiers())
                && IDatabaseTester.class.isAssignableFrom(field.getType());
    }

    /**
     * Returns whether {@code clazz} or a superclass declares a marked field.
     *
     * @param clazz The class to search, with its superclasses.
     * @return {@code true} when a {@link DbUnitTester} or {@link DbUnitTestCase} field is
     *         declared anywhere in the hierarchy.
     */
    public boolean declaresMarkedField(final Class<?> clazz)
    {
        return findFirst(clazz, this::isMarked) != null;
    }

    /**
     * Finds the first field in {@code clazz}'s whole class hierarchy that {@code matches}
     * accepts, most-derived class first, stopping before {@link Object}.
     *
     * @param clazz The class to search, with its superclasses.
     * @param matches Which fields to accept.
     * @return The first matching field, or {@code null} when none is.
     */
    public Field findFirst(final Class<?> clazz, final Predicate<Field> matches)
    {
        Class<?> current = clazz;
        while (current != null && current != Object.class)
        {
            for (final Field field : current.getDeclaredFields())
            {
                if (matches.test(field))
                {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Finds every field in {@code clazz}'s whole class hierarchy that {@code matches} accepts,
     * most-derived class first, stopping before {@link Object}.
     *
     * @param clazz The class to search, with its superclasses.
     * @param matches Which fields to accept.
     * @return The matching fields; empty when none is.
     */
    public List<Field> findAll(final Class<?> clazz, final Predicate<Field> matches)
    {
        final List<Field> found = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class)
        {
            found.addAll(declaredBy(current, matches));
            current = current.getSuperclass();
        }
        return found;
    }

    /**
     * Finds the fields {@code clazz} itself declares, leaving out its superclasses', that
     * {@code matches} accepts.
     *
     * @param clazz The class to search.
     * @param matches Which fields to accept.
     * @return The matching fields; empty when none is.
     */
    public List<Field> declaredBy(final Class<?> clazz, final Predicate<Field> matches)
    {
        final List<Field> found = new ArrayList<>();
        for (final Field field : clazz.getDeclaredFields())
        {
            if (matches.test(field))
            {
                found.add(field);
            }
        }
        return found;
    }
}
