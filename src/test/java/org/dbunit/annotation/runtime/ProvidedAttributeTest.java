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
 * Pins the one message shape its call sites share - so a change to it is a change here, not
 * scattered edits.
 */
class ProvidedAttributeTest
{
    @Test
    void testRejectBothSet_bothSetWithNoNote_namesBothAttributesAndSaysSetOne()
    {
        assertThatThrownBy(() -> ProvidedAttribute.rejectBothSet(true, "@DbUnitExpected",
                "verify()", "verifyDefinitions()", null))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("@DbUnitExpected sets both verify() and"
                                + " verifyDefinitions(); set only one.");
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

        assertThat(ProvidedAttribute.requireProvided(value, "DatabaseTesterFactory",
                ProvidedAttributeTest.class, "DbUnitConfig.databaseTesterFactory",
                "getDatabaseTester")).isSameAs(value);
    }

    @Test
    void testRequireProvided_null_throwsNamingProviderClassAttributeAndGetter()
    {
        assertThatThrownBy(() -> ProvidedAttribute.requireProvided(null, "DatabaseTesterFactory",
                ProvidedAttributeTest.class, "DbUnitConfig.databaseTesterFactory",
                "getDatabaseTester"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("DatabaseTesterFactory " + ProvidedAttributeTest.class.getName()
                                + ", named by DbUnitConfig.databaseTesterFactory, returned null"
                                + " from getDatabaseTester().");
    }

}
