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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;

/**
 * Decides whether a test opts into the {@code org.dbunit.annotation} family, and so into the
 * annotation-driven lifecycle: any of the annotations that configure a test
 * ({@link DbUnitConfig}, {@link DbUnitPrep}, {@link DbUnitSetup}, {@link DbUnitExpected},
 * {@link DbUnitTearDown}, {@link DbUnitRowCountCheck}) on the test method or class, or a
 * {@link DbUnitTester @DbUnitTester}/{@link DbUnitTestCase @DbUnitTestCase} field in the test
 * class or one enclosing it. A plain, unannotated {@link org.dbunit.IDatabaseTester} field alone
 * is not an opt-in - that is the 3.5.0 lifecycle-only style, left exactly as it was.
 *
 * <p>A binding passes its own {@link AnnotationLookup} and the test classes in scope, and may
 * add opt-ins of its own, such as {@code DbUnitExtension}'s {@code @DbUnitTest}. The binding
 * decides what an opted-out test gets: {@code DbUnitExtension} keeps its 3.5.0 lifecycle for
 * it, while the Spring listener leaves it entirely alone.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener} - can reach it too.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class AnnotatedTestOptIn
{
    /** The annotations any one of which, on the test method or class, opts a test in. */
    private static final List<Class<? extends Annotation>> OPT_IN_ANNOTATIONS = Arrays.asList(
            DbUnitConfig.class, DbUnitPrep.class, DbUnitSetup.class, DbUnitExpected.class,
            DbUnitTearDown.class, DbUnitRowCountCheck.class);

    private AnnotatedTestOptIn()
    {
    }

    /**
     * Returns whether the test opts into the {@code org.dbunit.annotation} family.
     *
     * @param lookup Finds the annotations that apply to the test.
     * @param testClasses The test class and every class enclosing it whose fields count, for
     *            {@code @Nested} support.
     * @return {@code true} when the test carries an annotation that configures it, or a class
     *         in scope declares a {@code @DbUnitTester} or {@code @DbUnitTestCase} field.
     */
    public static boolean isOptedIn(final AnnotationLookup lookup,
            final Collection<Class<?>> testClasses)
    {
        for (final Class<? extends Annotation> annotationType : OPT_IN_ANNOTATIONS)
        {
            if (lookup.find(annotationType) != null)
            {
                return true;
            }
        }
        for (final Class<?> testClass : testClasses)
        {
            if (declaresMarkerField(testClass))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether {@code testClass} or a superclass declares a {@link DbUnitTester} or
     * {@link DbUnitTestCase} field.
     */
    private static boolean declaresMarkerField(final Class<?> testClass)
    {
        Class<?> current = testClass;
        while (current != null && current != Object.class)
        {
            for (final Field field : current.getDeclaredFields())
            {
                if (field.isAnnotationPresent(DbUnitTester.class)
                        || field.isAnnotationPresent(DbUnitTestCase.class))
                {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
