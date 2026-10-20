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
package org.dbunit.junit.jupiter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * One-line opt-in for {@link DbUnitExtension}: {@code @DbUnitTest} registers the extension, as
 * {@code @ExtendWith(DbUnitExtension.class)} does, and also opts the class into the
 * annotation-driven lifecycle, even when it declares no other dbUnit annotation. A bare
 * {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
 * {@link org.dbunit.IDatabaseTester} field keeps the 3.5.0 lifecycle instead.
 *
 * <p>The opt-in has these consequences for a test that declares nothing else: the test runs
 * through a {@link org.dbunit.PrepAndExpectedTestCase}, which reads the tester's connection and
 * operations, never its dataset, and sets up no data without a {@code @DbUnitPrep} file, so the
 * tester's own {@code onSetup()} and {@code onTearDown()} do not run; the connection warnings of
 * that test case are logged; and
 * {@code IDatabaseTester} and {@code IDatabaseConnection} test method parameters are resolved.
 * The Spring binding differs: {@code @DbUnitSpringTest} alone opts nothing in.
 *
 * <p>Also the natural carrier for a project's own composed annotation, bundling
 * {@code @DbUnitTest} with shared {@code @DbUnitConfig} and lifecycle annotations:
 * <pre>{@code
 * @Retention(RetentionPolicy.RUNTIME)
 * @Target(ElementType.TYPE)
 * @Inherited
 * @DbUnitTest
 * @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
 * @DbUnitSetUpOperation(DbUnitOperation.DELETE_ALL)
 * public @interface AppDatabaseTest {}
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitExtension
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(DbUnitExtension.class)
public @interface DbUnitTest
{
}
