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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Pins the one message shape every {@code value()}-or-{@code provider()} call site shares - so
 * a change to it is a change here, not four scattered edits.
 */
class ProvidedAttributeTest
{
    @Test
    void testRejectBothSet_bothSetWithNoNote_namesBothAttributesAndSaysSetOne()
    {
        assertThatThrownBy(() -> ProvidedAttribute.rejectBothSet(true, "@DbUnitConfig",
                "properties()", "propertiesProvider()", null))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("@DbUnitConfig sets both properties() and"
                                + " propertiesProvider(); set only one.");
    }

    @Test
    void testRejectBothSet_bothSetWithNote_appendsTheNoteAfterSetOnlyOne()
    {
        assertThatThrownBy(() -> ProvidedAttribute.rejectBothSet(true, "@DbUnitExpected",
                "verify()", "verifyTables()", "verifyTables() only narrows a catalog."))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("@DbUnitExpected sets both verify() and verifyTables(); set"
                                + " only one. verifyTables() only narrows a catalog.");
    }

    @Test
    void testRejectBothSet_notBothSet_doesNotThrow()
    {
        assertThatCode(() -> ProvidedAttribute.rejectBothSet(false, "@DbUnitConfig", "a()", "b()",
                null)).doesNotThrowAnyException();
    }

    @Test
    void testRequireProvided_nonNull_returnsItUnchanged()
    {
        final String[] value = {"a"};

        assertThat(ProvidedAttribute.requireProvided(value, "DataSetPathsProvider",
                ProvidedAttributeTest.class, "@DbUnitPrep.provider", "getDataSetPaths"))
                        .isSameAs(value);
    }

    @Test
    void testRequireProvided_null_throwsNamingProviderClassAttributeAndGetter()
    {
        assertThatThrownBy(() -> ProvidedAttribute.requireProvided(null, "DataSetPathsProvider",
                ProvidedAttributeTest.class, "@DbUnitPrep.provider", "getDataSetPaths"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("DataSetPathsProvider " + ProvidedAttributeTest.class.getName()
                                + ", named by @DbUnitPrep.provider, returned null from"
                                + " getDataSetPaths().");
    }

    @Test
    void testRejectEmptyProvider_empty_throwsWithTheNoteAppended()
    {
        assertThatThrownBy(() -> ProvidedAttribute.rejectEmptyProvider(true,
                "DataSetPathsProvider", ProvidedAttributeTest.class, "@DbUnitPrep.provider",
                "getDataSetPaths", "Drop the annotation instead."))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("DataSetPathsProvider " + ProvidedAttributeTest.class.getName()
                                + ", named by @DbUnitPrep.provider, returned nothing from"
                                + " getDataSetPaths(). Drop the annotation instead.");
    }

    @Test
    void testRejectEmptyProvider_notEmpty_doesNotThrow()
    {
        assertThatCode(() -> ProvidedAttribute.rejectEmptyProvider(false, "DataSetPathsProvider",
                ProvidedAttributeTest.class, "@DbUnitPrep.provider", "getDataSetPaths", "x."))
                        .doesNotThrowAnyException();
    }
}
