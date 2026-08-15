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
package org.dbunit.database.rowcount;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.database.DatabaseConfig;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class or method as needing the {@code dbunit.rowCountCheck} and
 * {@code dbunit.rowCountCheckExcludeTables} system properties cleared for its duration, via
 * {@link ClearRowCountCheckSystemPropertiesExtension}. Without this, a test that builds a
 * {@link DatabaseConfig} and relies on the row count check defaulting to disabled is at the
 * mercy of whatever an ambient {@code -Ddbunit.rowCountCheck=true} (e.g. from the very field
 * test {@code row-count-check-plan.adoc}'s Verification section calls for) already set for the
 * whole JVM - the system property wins over DatabaseConfig in either direction by design.
 *
 * @since 3.6.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(ClearRowCountCheckSystemPropertiesExtension.class)
public @interface ClearRowCountCheckSystemProperties
{
}
