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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

/**
 * One-line opt-in for {@link DbUnitTestExecutionListener}: {@code @DbUnitSpringTest} is exactly
 * {@code @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class, mergeMode =
 * MergeMode.MERGE_WITH_DEFAULTS)}.
 *
 * <p>The {@code mergeMode} is why this annotation exists rather than registering
 * {@link DbUnitTestExecutionListener} with a bare {@code @TestExecutionListeners}: that
 * annotation's own default, {@link MergeMode#REPLACE_DEFAULTS}, replaces Spring's default
 * listeners entirely, silently dropping dependency injection and transaction management for
 * every test class that uses it without also re-listing every default listener by hand.
 *
 * <p>Also the natural carrier for a project's own composed annotation, bundling
 * {@code @DbUnitSpringTest} with shared {@code @DbUnitConfig} and lifecycle annotations, the same
 * way {@code org.dbunit.junit.jupiter.DbUnitTest} is:
 * <pre>{@code
 * @Retention(RetentionPolicy.RUNTIME)
 * @Target(ElementType.TYPE)
 * @Inherited
 * @DbUnitSpringTest
 * @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
 * @DbUnitSetup(operation = DbUnitOperation.DELETE_ALL)
 * public @interface AppSpringDatabaseTest {}
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitTestExecutionListener
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@TestExecutionListeners(listeners = DbUnitTestExecutionListener.class,
        mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
public @interface DbUnitSpringTest
{
}
