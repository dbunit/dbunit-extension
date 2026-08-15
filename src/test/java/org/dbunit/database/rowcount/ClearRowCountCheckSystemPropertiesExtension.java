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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Clears the {@code dbunit.rowCountCheck} and {@code dbunit.rowCountCheckExcludeTables}
 * system properties before each test and restores whatever they were afterward. See
 * {@link ClearRowCountCheckSystemProperties}.
 *
 * @since 3.6.0
 */
public class ClearRowCountCheckSystemPropertiesExtension
        implements BeforeEachCallback, AfterEachCallback
{
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ClearRowCountCheckSystemPropertiesExtension.class);

    /**
     * Saves the current values of both system properties, then clears them.
     *
     * @param context the extension context for the test about to run.
     */
    @Override
    public void beforeEach(final ExtensionContext context)
    {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK,
                System.getProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK));
        store.put(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES, System
                .getProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES));

        System.clearProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK);
        System.clearProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES);
    }

    /**
     * Restores both system properties to the values {@link #beforeEach(ExtensionContext)}
     * saved.
     *
     * @param context the extension context for the test that just ran.
     */
    @Override
    public void afterEach(final ExtensionContext context)
    {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        restore(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK,
                store.get(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK, String.class));
        restore(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                store.get(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                        String.class));
    }

    private static void restore(final String key, final String value)
    {
        if (value == null)
        {
            System.clearProperty(key);
        } else
        {
            System.setProperty(key, value);
        }
    }
}
