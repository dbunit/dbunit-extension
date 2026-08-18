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
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.database.DatabaseConfig;

/**
 * Turns the row count check (see {@code org.dbunit.database.rowcount.RowCountCheck}) on for a
 * class or a method, with an exclude list: before the test it snapshots every table's row
 * count, after teardown it snapshots again, and it fails the test when any count moved. That
 * catches a table the test forgot to list for teardown - whose rows survive and break a later
 * test - and equally a reference table it wrongly listed, whose count went down.
 *
 * <p>{@link #enabled()} defaults to {@code true} so that writing the bare annotation means
 * "on". It exists for the override direction: a class-level check with one method that
 * legitimately leaves rows behind - a test asserting that an audit trail persists, say -
 * writes {@code @DbUnitRowCountCheck(enabled = false)} on that method rather than losing the
 * check for the whole class.
 *
 * <p>Precedence, highest first:
 * <ol>
 *   <li>The {@code -Ddbunit.rowCountCheck} system property, when present - wins outright, in
 *   both directions, so it can force-enable across a CI run and force-disable locally without
 *   editing code.</li>
 *   <li>This annotation - method level, else class level.</li>
 *   <li>{@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK}.</li>
 *   <li>Default {@code false}.</li>
 * </ol>
 *
 * <p>A method-level annotation overrides a class-level one <em>wholesale</em>, {@link #exclude()}
 * included - the same rule as every other annotation in this family. A method that wants one
 * extra exclusion beyond the class-level ones has to repeat them; this is deliberate, since
 * merging would make "why is this table still excluded?" answerable only by reading two
 * declarations.
 *
 * <p>On the prep/expected path ({@link DbUnitExpected} declared), this annotation only
 * overrides the resolved check when the test case resolves to a
 * {@link DefaultPrepAndExpectedTestCase} - the only {@link PrepAndExpectedTestCase}
 * implementation exposing a setter for it; a {@link DbUnitTestCase} field injecting a
 * different implementation leaves this override silently unapplied there. On the simple path
 * (no {@link DbUnitExpected}), this restriction does not apply - the check runs directly
 * against the connection regardless of test case type.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitRowCountCheck {
    /**
     * Whether the check is enabled.
     *
     * @return True to enable the check; defaults to true.
     */
    boolean enabled() default true;

    /**
     * Table name patterns to exclude from the check.
     *
     * @return The excluded table name patterns; empty (the default) excludes none.
     */
    String[] exclude() default {};
}
