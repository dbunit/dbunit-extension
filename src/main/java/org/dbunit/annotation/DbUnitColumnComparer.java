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
 * reaches eleven of the sixteen {@code ValueComparers} constants - the stateless ones - and
 * any custom {@link ValueComparer}. It does not reach the other five, which are configured
 * instances built with constructor arguments (for example
 * {@code isActualWithinOneMinuteNewerOfExpectedTimestamp}); naming one of those here fails
 * fast with an {@link IllegalStateException} pointing at a
 * {@link org.dbunit.VerifyTableDefinitionsProvider} catalog as the fix, since a
 * configured comparer can only be expressed as a {@code VerifyTableDefinition}
 * constant written in Java.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitVerifyTable
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbUnitColumnComparer {
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
