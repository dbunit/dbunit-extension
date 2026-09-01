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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.DataFileLoader;
import org.junit.jupiter.api.Test;

class VerifyTableDefinitionResolverTest
{
    private final VerifyTableDefinitionResolver resolver = new VerifyTableDefinitionResolver();

    @Test
    void testResolve_verifyTablesNamesOnly_buildsOneDefaultDefinitionPerName()
    {
        final DbUnitExpected expected =
                WithVerifyTables.class.getAnnotation(DbUnitExpected.class);

        final VerifyTableDefinition[] definitions =
                resolver.resolve(null, expected, mock(DataFileLoader.class), new String[0]);

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
    }

    @Test
    void testResolve_duplicateVerifyTablesName_throws()
    {
        final DbUnitExpected expected =
                WithDuplicateVerifyTables.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolver.resolve(null, expected, mock(DataFileLoader.class),
                new String[0]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testResolve_noVerifySpec_derivesOneDefinitionPerExpectedDatasetTable() throws Exception
    {
        final DbUnitExpected expected = Bare.class.getAnnotation(DbUnitExpected.class);
        final DataFileLoader loader = mock(DataFileLoader.class);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT", "LEDGER"});
        when(loader.load("expected.xml")).thenReturn(dataSet);

        final VerifyTableDefinition[] definitions =
                resolver.resolve(null, expected, loader, new String[] {"expected.xml"});

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
    }

    @Test
    void testResolve_expectedDatasetTableEnumerationFails_throwsNamingThePath() throws Exception
    {
        final DbUnitExpected expected = Bare.class.getAnnotation(DbUnitExpected.class);
        final DataFileLoader loader = mock(DataFileLoader.class);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenThrow(new DataSetException("corrupt"));
        when(loader.load("expected.xml")).thenReturn(dataSet);

        assertThatThrownBy(() -> resolver.resolve(null, expected, loader,
                new String[] {"expected.xml"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected.xml")
                .hasCauseInstanceOf(DataSetException.class);
    }

    @DbUnitExpected(value = "expected.xml", verifyTables = {"ACCOUNT", "LEDGER"})
    private static class WithVerifyTables
    {
    }

    @DbUnitExpected(value = "expected.xml", verifyTables = {"ACCOUNT", "ACCOUNT"})
    private static class WithDuplicateVerifyTables
    {
    }

    @DbUnitExpected("expected.xml")
    private static class Bare
    {
    }
}
