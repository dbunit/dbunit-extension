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
package org.dbunit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.assertion.comparer.value.ValueComparer;

/**
 * Binds one column to the {@link ValueComparer} used to compare its values, nested inside
 * {@link DbUnitVerifyTable#columnComparers()}.
 *
 * <p>{@link #comparer()} is reflectively instantiated with its no-arg constructor. This
 * reaches any custom {@link ValueComparer}, and the stateless {@code ValueComparers}
 * constants - those whose implementation class has a no-arg constructor. It does not reach a
 * {@code ValueComparers} constant that is a configured instance built with constructor
 * arguments - the timestamp-tolerance comparers, for example
 * {@code isActualWithinOneMinuteNewerOfExpectedTimestamp}; naming one of those here fails
 * fast with an {@link IllegalStateException} pointing at a
 * {@link org.dbunit.VerifyTableDefinitionsProvider} catalog as the fix, since a
 * configured comparer can only be expressed as a {@code VerifyTableDefinition}
 * constant written in Java.
 *
 * <p>The instantiated comparer is cached for the JVM's entire lifetime, keyed by class, and
 * reused by every later {@code @DbUnitColumnComparer}/{@link DbUnitVerifyTable#defaultComparer()}
 * naming the same class, rather than reflectively constructed anew for every test method - the
 * same once-and-reused-forever caching a {@link org.dbunit.VerifyTableDefinitionsProvider}
 * catalog gets. A custom {@link ValueComparer} named here must therefore be a pure function
 * with no dependency on mutable external state that could legitimately differ between the
 * tests reusing it.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitVerifyTable
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbUnitColumnComparer
{
    /**
     * The name of the column this comparer applies to.
     *
     * @return The column name.
     */
    String column();

    /**
     * The {@link ValueComparer} implementation to use for this column, instantiated with its
     * no-arg constructor.
     *
     * @return The comparer class.
     */
    Class<? extends ValueComparer> comparer();
}
