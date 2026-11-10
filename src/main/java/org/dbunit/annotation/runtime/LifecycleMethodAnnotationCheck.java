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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTearDownOperation;

/**
 * Rejects a method-level {@code org.dbunit.annotation} annotation on a lifecycle method of a test
 * class, such as a {@code @BeforeEach} or {@code @AfterEach} method, whether or not a subclass
 * overrides the method.
 *
 * <p>A binding reads {@link DbUnitPrep}, {@link DbUnitSetUpOperation}, {@link DbUnitExpected},
 * {@link DbUnitTearDownOperation} and {@link DbUnitRowCountCheck} on the test method and on the
 * classes in scope, and nowhere else. An annotation on a lifecycle method compiles, because the
 * annotations accept any method, yet nothing reads it: a {@code @DbUnitPrep} then loads no data,
 * and a {@code @DbUnitExpected} verifies nothing, so a test that should fail passes. This check
 * turns that silent mistake into an {@link IllegalStateException} that names the method and the
 * annotation.
 *
 * <p>A binding supplies the two parts that depend on its test framework: which methods of a
 * class are lifecycle methods, and whether a method carries an annotation, directly or as a
 * meta-annotation. Both parts look at each method as it is declared, so that every binding finds
 * the same method and the message names the method that carries the annotation, and not one it
 * overrides or that overrides it. The answer for a class does not change between its tests, so
 * the check computes it once for each class.
 *
 * <p>This type is internal to dbUnit's bindings; it is public only for use across
 * dbUnit's packages and may change in a minor release.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class LifecycleMethodAnnotationCheck
{
    /** The annotations a binding reads on the test method and nowhere else. */
    private static final List<Class<? extends Annotation>> METHOD_LEVEL_ANNOTATIONS =
            Arrays.asList(DbUnitPrep.class, DbUnitSetUpOperation.class, DbUnitExpected.class,
                    DbUnitTearDownOperation.class, DbUnitRowCountCheck.class);

    private final Function<Class<?>, Collection<Method>> lifecycleMethodsOf;
    private final BiPredicate<Method, Class<? extends Annotation>> carries;

    private final ClassValue<String> misplacementOf = new ClassValue<String>()
    {
        @Override
        protected String computeValue(final Class<?> type)
        {
            return describeFirstMisplacement(type);
        }
    };

    /**
     * Creates a check for a test framework.
     *
     * @param lifecycleMethodsOf Lists the lifecycle methods of a class, those it declares and
     *            those it inherits from superclasses and interfaces, an overridden one included.
     *            {@link DeclaredMethodsOfHierarchy} lists the methods to choose from.
     * @param carries Tells whether a method itself carries an annotation, directly or as a
     *            meta-annotation, and not whether a method it overrides does.
     */
    public LifecycleMethodAnnotationCheck(
            final Function<Class<?>, Collection<Method>> lifecycleMethodsOf,
            final BiPredicate<Method, Class<? extends Annotation>> carries)
    {
        this.lifecycleMethodsOf = lifecycleMethodsOf;
        this.carries = carries;
    }

    /**
     * Rejects a method-level dbUnit annotation on a lifecycle method of a class.
     *
     * @param classInScope A test class, or a class enclosing it, whose lifecycle methods the test
     *            runs.
     * @throws IllegalStateException If a lifecycle method of the class carries a method-level
     *             dbUnit annotation. The message names the method and the annotation.
     */
    public void requireNoneOn(final Class<?> classInScope)
    {
        final String misplacement = misplacementOf.get(classInScope);
        if (misplacement != null)
        {
            throw new IllegalStateException(misplacement);
        }
    }

    private String describeFirstMisplacement(final Class<?> type)
    {
        for (final Method lifecycleMethod : lifecycleMethodsOf.apply(type))
        {
            for (final Class<? extends Annotation> annotationType : METHOD_LEVEL_ANNOTATIONS)
            {
                if (carries.test(lifecycleMethod, annotationType))
                {
                    return "@" + annotationType.getSimpleName() + " is on the lifecycle method "
                            + lifecycleMethod.getDeclaringClass().getName() + "#"
                            + lifecycleMethod.getName() + "(), where dbUnit never reads it: a"
                            + " method-level dbUnit annotation counts on the test method only."
                            + " Move it to the test method, or to the test class to apply it to"
                            + " every test.";
                }
            }
        }
        return null;
    }
}
