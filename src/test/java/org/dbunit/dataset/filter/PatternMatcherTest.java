/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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
package org.dbunit.dataset.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.TurkishDefaultLocale;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PatternMatcher}.
 */
class PatternMatcherTest
{
    @Test
    @TurkishDefaultLocale
    void testAccept_turkishLocaleDottedIName_matches()
    {
        final PatternMatcher matcher = new PatternMatcher();
        matcher.addPattern("ISLAND_TABLE");

        assertThat(matcher.accept("island_table"))
                .as("accept() must fold the candidate name with"
                        + " Locale.ENGLISH so a Turkish default locale's"
                        + " dotted capital I does not break matching"
                        + " against an already-uppercase-ASCII accepted"
                        + " name.")
                .isTrue();
    }
}
