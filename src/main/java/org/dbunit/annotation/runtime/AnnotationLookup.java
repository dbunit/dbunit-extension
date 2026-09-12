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

/**
 * A binding's way of finding the {@code org.dbunit.annotation} annotation that applies to the
 * test it is running: the one search strategy that differs between test frameworks, since each
 * has its own rules for meta-annotations, inheritance and enclosing classes. Everything else
 * that turns annotations into a configuration is shared - see
 * {@link AnnotatedTestConfiguration#from(Class, AnnotationLookup)}.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener} - can implement it.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public interface AnnotationLookup
{
    /**
     * Finds the annotation of {@code annotationType} that applies to the test. A binding looks
     * on the test method first and on the test class second, so a method-level annotation wins
     * over a class-level one.
     *
     * @param <A> The annotation type.
     * @param annotationType The annotation to look for.
     * @return The annotation, or {@code null} when the test has none.
     */
    <A extends Annotation> A find(Class<A> annotationType);
}
