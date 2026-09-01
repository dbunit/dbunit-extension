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

import org.dbunit.VerifyTableDefinition;
import org.dbunit.assertion.comparer.value.ValueComparer;

/**
 * Full per-table verification rules for one table, nested inside
 * {@link DbUnitExpected#verify()}: column include/exclude filters, value comparers, and sort
 * mode. Maps directly onto a {@link VerifyTableDefinition}.
 *
 * <p>An empty {@link #include()} (the default) means include every column, since an
 * annotation cannot express {@code null} and {@code VerifyTableDefinition} distinguishes
 * {@code null} (include all) from an empty array (include nothing) - the latter being a
 * degenerate setting nobody wants, so it is not expressible here.
 *
 * <p>For a shared, reusable table definition - especially one needing a configured
 * {@link ValueComparer} instance that has no no-arg constructor - prefer a
 * {@link org.dbunit.VerifyTableDefinitionsProvider} catalog over this inline form;
 * see {@link DbUnitExpected#verifyDefinitions()}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitExpected
 * @see DbUnitColumnComparer
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbUnitVerifyTable
{
    /**
     * The name of the table to verify.
     *
     * @return The table name.
     */
    String value();

    /**
     * Column names to exclude from comparison. Composes with {@link #include()} when both are
     * set: the included set, minus these.
     *
     * @return The column names to exclude; empty (the default) excludes none.
     */
    String[] exclude() default {};

    /**
     * Column names to include in comparison, to the exclusion of all others.
     *
     * @return The column names to include; empty (the default) includes every column.
     */
    String[] include() default {};

    /**
     * The {@link ValueComparer} to use for columns not named in {@link #columnComparers()},
     * instantiated with its no-arg constructor and cached for the JVM's entire lifetime, keyed
     * by class - see {@link DbUnitColumnComparer} for the resulting purity requirement on a
     * custom {@link ValueComparer}.
     *
     * @return The default comparer class; the interface itself (the default) means "not set",
     *         leaving dbUnit's own default comparer in effect.
     */
    Class<? extends ValueComparer> defaultComparer() default ValueComparer.class;

    /**
     * Per-column {@link ValueComparer} overrides.
     *
     * @return The column comparer bindings; empty (the default) applies
     *         {@link #defaultComparer()} to every column.
     */
    DbUnitColumnComparer[] columnComparers() default {};

    /**
     * Whether to sort the expected and actual tables by only this table's filtered columns
     * (post exclude/include) instead of by all of the actual table's native columns.
     *
     * @return True to sort by only the filtered columns; defaults to false.
     * @see VerifyTableDefinition#isSortOnFilteredColumnsOnly()
     */
    boolean sortOnFilteredColumnsOnly() default false;
}
