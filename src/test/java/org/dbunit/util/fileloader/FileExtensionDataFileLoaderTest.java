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

package org.dbunit.util.fileloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Jeff Jensen
 * @since 3.6.0
 */
class FileExtensionDataFileLoaderTest
{
    FileExtensionDataFileLoader loader = null;

    @BeforeEach
    protected void setUp() throws Exception
    {
        loader = new FileExtensionDataFileLoader();
    }

    @Test
    void testLoad_xmlExtension_dispatchesToFlatXmlDataFileLoader() throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/test.xml");

        assertThat(ds.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testLoad_jsonExtension_dispatchesToJsonDataFileLoader() throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/test.json");

        assertThat(ds.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testLoad_yamlExtension_dispatchesToYamlDataFileLoader() throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/test.yaml");

        assertThat(ds.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testLoad_ymlExtension_dispatchesToYamlDataFileLoader() throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/test.yml");

        assertThat(ds.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testLoad_xlsExtension_dispatchesToXlsDataFileLoader() throws Exception
    {
        final IDataSet ds = loader.load("/xml/dataSetTest.xls");

        assertThat(ds.getTableNames()).as("No tables found in dataset.")
                .hasSizeGreaterThan(0);
    }

    @Test
    void testLoad_xlsxExtension_dispatchesToXlsDataFileLoader() throws Exception
    {
        final IDataSet ds =
                loader.load("/excel/XlsDataSetWriterCellStyleCaching.xlsx");

        assertThat(ds.getTableNames()).as("No tables found in dataset.")
                .hasSizeGreaterThan(0);
    }

    @Test
    void testLoad_dotInDirectoryNameBeforeExtension_usesTheFileNamesExtension()
            throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/v1.2/test.xml");

        assertThat(ds.getTableNames())
                .as("The extension must come from the file name, not the last '.' anywhere"
                        + " in the path - a directory named 'v1.2' must not be mistaken for"
                        + " a '.2' extension.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testLoad_unsupportedExtension_throwsWithSupportedExtensionsHint()
    {
        assertThatThrownBy(() -> loader
                .load("/org/dbunit/util/fileloader/test.unsupported"))
                        .as("Unsupported extension must throw naming it and"
                                + " listing the supported ones.")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(".unsupported")
                        .hasMessageContaining("Supported extensions");
    }

    @Test
    void testLoad_noExtensionAtAll_throwsWithSupportedExtensionsHint()
    {
        assertThatThrownBy(() -> loader
                .load("/org/dbunit/util/fileloader/testnoextension"))
                        .as("A file name with no '.' at all must be rejected the same as an"
                                + " unsupported extension, not throw a different, confusing"
                                + " error.")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Supported extensions");
    }

    @Test
    void testConstructor_withReplacementObjectsArgument_appliesThemSameAsNoArgConstructor()
            throws Exception
    {
        final Map<String, Object> replacements = new HashMap<>();
        replacements.put("[FIRST]", "[SECOND]");
        replacements.put("[SECOND]", null);
        final FileExtensionDataFileLoader loaderWithReplacements =
                new FileExtensionDataFileLoader(replacements);

        final IDataSet ds = loaderWithReplacements.load(
                "/org/dbunit/util/fileloader/replacement-token-test.xml");

        assertThat(ds.getTable("TOKEN_TABLE").getValue(0, "COL0"))
                .as("The (Map ro) constructor must apply its replacement objects the same way"
                        + " addReplacementObjects() does on a no-arg-constructed instance.")
                .isEqualTo("[SECOND]");
    }

    @Test
    void testLoad_replacementTokensConfigured_appliesSubstitutionExactlyOnce()
            throws Exception
    {
        final Map<String, Object> replacements = new HashMap<>();
        replacements.put("[FIRST]", "[SECOND]");
        replacements.put("[SECOND]", null);
        loader.addReplacementObjects(replacements);

        final IDataSet ds = loader.load(
                "/org/dbunit/util/fileloader/replacement-token-test.xml");
        final ITable table = ds.getTable("TOKEN_TABLE");

        assertThat(table.getValue(0, "COL0"))
                .as("A second replacement pass would resolve [SECOND] to null; the loaded"
                        + " value must stay [SECOND], proving substitution ran exactly once.")
                .isEqualTo("[SECOND]");
    }
}
