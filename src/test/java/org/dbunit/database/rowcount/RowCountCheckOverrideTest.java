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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RowCountCheckOverrideTest
{
    @Test
    void testConstructor_enabledAndPatterns_exposesThem()
    {
        final RowCountCheckOverride override =
                new RowCountCheckOverride(true, new String[] {"AUDIT_*", "LOG"});

        assertThat(override.isEnabled()).as("The enabled flag is the one given.").isTrue();
        assertThat(override.getExclude()).as("The patterns are the ones given, in order.")
                .containsExactly("AUDIT_*", "LOG");
    }

    @Test
    void testConstructor_nullExclude_excludesNone()
    {
        final RowCountCheckOverride override = new RowCountCheckOverride(false, null);

        assertThat(override).as("A null pattern list is the same as an empty one.")
                .isEqualTo(new RowCountCheckOverride(false, new String[0]));
        assertThat(override.getExclude()).as("A null pattern list excludes no table.")
                .isEmpty();
    }

    @Test
    void testConstructor_callerChangesTheArrayAfterwards_keepsTheOriginalPatterns()
    {
        final String[] exclude = {"AUDIT_LOG"};
        final RowCountCheckOverride override = new RowCountCheckOverride(true, exclude);

        exclude[0] = "CHANGED";

        assertThat(override.getExclude())
                .as("The override copies the array, so the caller's later change must not show.")
                .containsExactly("AUDIT_LOG");
    }

    @Test
    void testGetExclude_callerChangesTheReturnedArray_keepsTheOriginalPatterns()
    {
        final RowCountCheckOverride override =
                new RowCountCheckOverride(true, new String[] {"AUDIT_LOG"});

        override.getExclude()[0] = "CHANGED";

        assertThat(override.getExclude())
                .as("The override hands out a copy, so changing it must not change the override.")
                .containsExactly("AUDIT_LOG");
    }

    @Test
    void testEquals_sameEnabledAndPatterns_equalWithTheSameHashCode()
    {
        final RowCountCheckOverride override =
                new RowCountCheckOverride(true, new String[] {"A", "B"});
        final RowCountCheckOverride same =
                new RowCountCheckOverride(true, new String[] {"A", "B"});

        assertThat(override).as("Equal values make equal overrides.").isEqualTo(same);
        assertThat(override.hashCode()).as("Equal overrides have the same hash code.")
                .isEqualTo(same.hashCode());
    }

    @Test
    void testEquals_differentEnabled_notEqual()
    {
        final RowCountCheckOverride enabled = new RowCountCheckOverride(true, new String[0]);
        final RowCountCheckOverride disabled = new RowCountCheckOverride(false, new String[0]);

        assertThat(enabled).as("The enabled flag takes part in equality.")
                .isNotEqualTo(disabled);
    }

    @Test
    void testEquals_differentPatterns_notEqual()
    {
        final RowCountCheckOverride one = new RowCountCheckOverride(true, new String[] {"A"});
        final RowCountCheckOverride other = new RowCountCheckOverride(true, new String[] {"B"});

        assertThat(one).as("The patterns take part in equality.").isNotEqualTo(other);
    }

    @Test
    void testToString_enabledAndPatterns_namesBoth()
    {
        final RowCountCheckOverride override =
                new RowCountCheckOverride(true, new String[] {"AUDIT_*", "LOG"});

        assertThat(override.toString())
                .as("A failed assertion on an override must show what it holds.")
                .isEqualTo("RowCountCheckOverride[enabled=true, exclude=[AUDIT_*, LOG]]");
    }
}
