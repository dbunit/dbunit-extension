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
import java.util.Properties;

import org.dbunit.database.DatabaseConfig;

/**
 * One {@link DatabaseConfig} property name/value pair, nested inside
 * {@link DbUnitConfig#properties()}.
 *
 * <p>Named members are used instead of a {@code String[]} of {@code "name=value"} pairs so
 * each entry is self-documenting, IDE-completable, and cannot be broken by a value that
 * itself contains {@code =} (an escape pattern, for instance).
 *
 * <p>Every entry on a test element is collected into one {@link Properties} instance and
 * handed to {@link DatabaseConfig#setPropertiesByString(Properties)}, which accepts both the
 * long {@code http://www.dbunit.org/...} names and their short forms.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitConfig
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbUnitProperty {
    /**
     * The property name, in either its long or short form.
     *
     * @return The property name.
     */
    String name();

    /**
     * The property value, as accepted by
     * {@link DatabaseConfig#setPropertiesByString(Properties)}.
     *
     * @return The property value.
     */
    String value();
}
