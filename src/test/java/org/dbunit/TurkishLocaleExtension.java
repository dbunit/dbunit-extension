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

package org.dbunit;

import java.util.Locale;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Temporarily switches the JVM default {@link Locale} to Turkish ({@code tr-TR}) for the duration
 * of a {@link TurkishDefaultLocale @TurkishDefaultLocale}-annotated test method, restoring the
 * original default afterward regardless of the test's outcome.
 *
 * <p>
 * Centralizes the save/mutate/restore steps needed to reproduce Turkish-locale-specific bugs -
 * e.g. an un-pinned {@code toUpperCase()}/{@code toLowerCase()} folding {@code i}/{@code I} to the
 * dotted/dotless variants {@code İ}/{@code ı} - instead of duplicating them in every affected test
 * class.
 *
 * @since 3.4.0
 */
public class TurkishLocaleExtension implements BeforeEachCallback, AfterEachCallback
{
    private static final String ORIGINAL_LOCALE_KEY = "originalLocale";

    @Override
    public void beforeEach(final ExtensionContext context)
    {
        getStore(context).put(ORIGINAL_LOCALE_KEY, Locale.getDefault());
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @Override
    public void afterEach(final ExtensionContext context)
    {
        final Locale original =
                getStore(context).get(ORIGINAL_LOCALE_KEY, Locale.class);
        Locale.setDefault(original);
    }

    private ExtensionContext.Store getStore(final ExtensionContext context)
    {
        return context.getStore(ExtensionContext.Namespace
                .create(TurkishLocaleExtension.class, context.getRequiredTestMethod()));
    }
}
