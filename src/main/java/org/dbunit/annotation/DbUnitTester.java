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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.IDatabaseTester;

/**
 * Marks a test class field, of a type implementing {@link IDatabaseTester}, as the tester to
 * drive - the "an instance already exists and I want it used" case, ahead of a
 * {@link DbUnitConfig#databaseTesterFactory()} and the plain field auto-scan.
 *
 * <p>Accepts a static field, the way a tester shared across every method in a class is
 * typically declared. Declaring this alongside a {@link DbUnitTestCase} field, or more than
 * one field with either marker at the same class level, is rejected as ambiguous.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitTestCase
 * @see DbUnitConfig#databaseTesterFactory()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbUnitTester {
}
