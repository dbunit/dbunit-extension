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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link AnnotationLookup} that finds the annotations a test hands it, each with the class
 * that declares it, and records which annotation types were asked for. A type it was handed
 * nothing for is not found, and its declaring class is the test class, as for a real binding.
 * Handed several of one type, it finds them nearest first, in the order it was handed them.
 */
class FakeAnnotationLookup implements AnnotationLookup
{
    private final Class<?> testClass;
    private final Map<Class<? extends Annotation>, List<Annotation>> annotations;
    private final Map<Class<? extends Annotation>, Class<?>> declaringClasses;
    private final List<Class<? extends Annotation>> askedFor = new ArrayList<>();

    /**
     * Creates a lookup that finds no annotation.
     *
     * @param testClass The test class, reported as the declaring class of every annotation type
     *            the lookup was handed nothing for.
     */
    FakeAnnotationLookup(final Class<?> testClass)
    {
        this(testClass, Collections.emptyMap(), Collections.emptyMap());
    }

    private FakeAnnotationLookup(final Class<?> testClass,
            final Map<Class<? extends Annotation>, List<Annotation>> annotations,
            final Map<Class<? extends Annotation>, Class<?>> declaringClasses)
    {
        this.testClass = testClass;
        this.annotations = annotations;
        this.declaringClasses = declaringClasses;
    }

    /**
     * Returns a lookup that finds {@code annotation} as well as what this one finds. One of the
     * same type this lookup already finds is nearer than this one.
     *
     * @param annotation The annotation to find, declared by the test class.
     * @return The new lookup.
     */
    FakeAnnotationLookup finding(final Annotation annotation)
    {
        return finding(annotation, testClass);
    }

    /**
     * Returns a lookup that finds {@code annotation} as well as what this one finds. One of the
     * same type this lookup already finds is nearer than this one, and its declaring class is the
     * one reported.
     *
     * @param annotation The annotation to find.
     * @param declaringClass The class that declares it.
     * @return The new lookup.
     */
    FakeAnnotationLookup finding(final Annotation annotation, final Class<?> declaringClass)
    {
        final Class<? extends Annotation> annotationType = annotation.annotationType();
        final Map<Class<? extends Annotation>, List<Annotation>> moreAnnotations =
                new HashMap<>(annotations);
        final List<Annotation> declarations = new ArrayList<>(
                annotations.getOrDefault(annotationType, Collections.<Annotation>emptyList()));
        declarations.add(annotation);
        moreAnnotations.put(annotationType, declarations);
        final Map<Class<? extends Annotation>, Class<?>> moreDeclaringClasses =
                new HashMap<>(declaringClasses);
        moreDeclaringClasses.putIfAbsent(annotationType, declaringClass);
        return new FakeAnnotationLookup(testClass, moreAnnotations, moreDeclaringClasses);
    }

    @Override
    public <A extends Annotation> A find(final Class<A> annotationType)
    {
        askedFor.add(annotationType);
        final List<Annotation> declarations = annotations.get(annotationType);
        if (declarations == null)
        {
            return null;
        }
        return annotationType.cast(declarations.get(0));
    }

    @Override
    public <A extends Annotation> List<A> findAll(final Class<A> annotationType)
    {
        askedFor.add(annotationType);
        final List<A> found = new ArrayList<>();
        for (final Annotation declaration : annotations.getOrDefault(annotationType,
                Collections.<Annotation>emptyList()))
        {
            found.add(annotationType.cast(declaration));
        }
        return found;
    }

    @Override
    public Class<?> findDeclaringClass(final Class<? extends Annotation> annotationType)
    {
        return declaringClasses.getOrDefault(annotationType, testClass);
    }

    /**
     * Returns the annotation types {@link #find(Class)} or {@link #findAll(Class)} was asked
     * for, in order.
     *
     * @return The annotation types asked for.
     */
    List<Class<? extends Annotation>> getAskedFor()
    {
        return Collections.unmodifiableList(askedFor);
    }
}
