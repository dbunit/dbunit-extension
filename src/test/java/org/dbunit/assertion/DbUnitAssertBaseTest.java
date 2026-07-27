/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.dbunit.TurkishDefaultLocale;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DbUnitAssertBase}.
 */
@ExtendWith(MockitoExtension.class)
class DbUnitAssertBaseTest
{
    private final DbUnitAssertBase assertBase = new DbUnitAssertBase();

    @Mock
    private IDataSet dataSet;

    @Test
    @TurkishDefaultLocale
    void testGetSortedTableNames_turkishLocaleUppercaseI_sortsAsEnglish() throws Exception
    {
        when(dataSet.getTableNames())
                .thenReturn(new String[] {"island", "beta"});
        when(dataSet.isCaseSensitiveTableNames()).thenReturn(false);

        final String[] actual = assertBase.getSortedTableNames(dataSet);

        assertThat(actual)
                .as("Table-name folding must use Locale.ENGLISH so a Turkish"
                        + " default locale does not turn a lower-case 'i' into"
                        + " a dotted capital I (U+0130) instead of ASCII 'I'.")
                .containsExactly("BETA", "ISLAND");
    }

    @Test
    void testGetSortedTableNames_calledTwice_doesNotMutateDataSetArray() throws Exception
    {
        final String[] backing = {"beta", "island"};
        when(dataSet.getTableNames()).thenReturn(backing);
        when(dataSet.isCaseSensitiveTableNames()).thenReturn(false);

        assertBase.getSortedTableNames(dataSet);
        assertBase.getSortedTableNames(dataSet);

        assertThat(backing)
                .as("getSortedTableNames() must operate on a clone so repeated"
                        + " calls never mutate the array instance returned by"
                        + " IDataSet.getTableNames(), since the interface does"
                        + " not guarantee callers receive a fresh copy.")
                .containsExactly("beta", "island");
    }
}
