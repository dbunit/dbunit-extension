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
 * <p>dbUnit also registers the listener as one of Spring's default test execution listeners, in
 * its {@code META-INF/spring.factories}, so a test class that keeps Spring's default listeners
 * gets it without this annotation. Spring merges this annotation with those defaults and drops
 * the duplicate, so Spring registers the listener once.
 *
 * <p>Spring honors only one {@code @TestExecutionListeners} declaration per class: the class's
 * own, or the first it finds among the annotations composed into it, going by declaration order.
 * When another one wins over this annotation, the listener still runs as a default listener, as
 * long as that declaration keeps the defaults with {@code mergeMode = MERGE_WITH_DEFAULTS}. A
 * declaration that replaces the defaults drops the listener together with Spring's own ones, and
 * then nothing acts on any {@code @DbUnit*} annotation: give it
 * {@code mergeMode = MERGE_WITH_DEFAULTS}, which keeps Spring's own listeners as well. Naming
 * {@link DbUnitTestExecutionListener} in a declaration that replaces the defaults registers it
 * too, but the declaration still drops dependency injection and transaction management. A
 * subclass's own declaration keeps the listeners it inherits, as Spring merges them.
 *
 * <p>Spring drops a duplicate listener only when it merges a declaration with the defaults. A
 * base class whose declaration replaces the defaults and names
 * {@link DbUnitTestExecutionListener} therefore gets a second instance from a subclass that
 * names it again or carries {@code @DbUnitSpringTest}. The instances share the test context, and
 * the listener guards itself: the first runs dbUnit for a test and the second leaves it alone.
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
 * @DbUnitSetUpOperation(DbUnitOperation.DELETE_ALL)
 * public @interface AppSpringDatabaseTest {}
 * }</pre>
 *
 * <p>A test that injects a {@code @DbUnitTestCase} cannot carry such a composed annotation: an
 * injected test case is used as its owner built it, so a {@code @DbUnitSetUpOperation},
 * {@code @DbUnitTearDownOperation} or {@code @DbUnitConfig} attribute that would configure it,
 * one a composed annotation or a base class carries included, is rejected with an
 * {@link IllegalStateException}, and a test class cannot take an inherited declaration back.
 * Use the composed annotation on the test classes that let dbUnit build their test case.
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
