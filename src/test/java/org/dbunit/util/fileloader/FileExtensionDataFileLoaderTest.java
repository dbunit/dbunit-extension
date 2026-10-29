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

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
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
    void testLoad_uppercaseExtension_dispatchesTheSameAsLowercase() throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/test-uppercase.XML");

        assertThat(ds.getTableNames())
                .as("An uppercase extension must dispatch the same as its lowercase form,"
                        + " not be rejected as unsupported.")
                .containsExactly("TEST_TABLE");
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
    void testLoad_malformedFileOfSupportedExtension_surfacesTheParseFailureNotADispatchError()
    {
        assertThatThrownBy(
                () -> loader.load("/org/dbunit/util/fileloader/malformed.xml"))
                        .as("A malformed file of a dispatched extension must surface the"
                                + " delegate loader's parse failure (wrapped, naming the file),"
                                + " not be masked as an unsupported-extension"
                                + " IllegalArgumentException.")
                        .isInstanceOf(DatabaseUnitRuntimeException.class)
                        .hasMessageContaining("malformed.xml")
                        .isNotInstanceOf(IllegalArgumentException.class);
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
                        .hasMessageContaining("Supported extensions")
                        .as("The no-extension case must read as \"extension: (none)\", not the"
                                + " confusing \"extension: .\" a bare, empty extension would"
                                + " otherwise produce.")
                        .hasMessageContaining("extension: (none)")
                        .hasMessageNotContaining("extension: .");
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

    @Test
    void testLoad_xmlWithAColumnOnlyALaterRowHas_takesTheColumnsOfTheFirstRow()
            throws Exception
    {
        final IDataSet ds =
                loader.load("/org/dbunit/util/fileloader/column-sensing-test.xml");
        final Column[] columns = ds.getTable("TEST_TABLE").getTableMetaData().getColumns();

        assertThat(Columns.getColumnNames(columns))
                .as("Without a column sensing builder, flat XML takes the columns of a table"
                        + " from its first row.")
                .containsExactly("COL0");
    }

    @Test
    void testLoad_columnSensingBuilder_loadsAColumnOnlyALaterRowHas() throws Exception
    {
        final FlatXmlDataSetBuilder builder =
                new FlatXmlDataSetBuilder().setColumnSensing(true);
        final FileExtensionDataFileLoader columnSensingLoader =
                new FileExtensionDataFileLoader(builder);

        final IDataSet ds = columnSensingLoader
                .load("/org/dbunit/util/fileloader/column-sensing-test.xml");

        assertThat(ds.getTable("TEST_TABLE").getValue(1, "COL1"))
                .as("A flat XML builder with column sensing on must load the column that only"
                        + " the second row has.")
                .isEqualTo("[TOKEN]");
    }

    @Test
    void testLoad_replacementObjectsAndColumnSensingBuilder_appliesBoth() throws Exception
    {
        final Map<String, Object> replacementObjects = new HashMap<>();
        replacementObjects.put("[TOKEN]", "val 1");
        final FlatXmlDataSetBuilder builder =
                new FlatXmlDataSetBuilder().setColumnSensing(true);
        final FileExtensionDataFileLoader columnSensingLoader =
                new FileExtensionDataFileLoader(replacementObjects, new HashMap<>(), builder);

        final IDataSet ds = columnSensingLoader
                .load("/org/dbunit/util/fileloader/column-sensing-test.xml");

        assertThat(ds.getTable("TEST_TABLE").getValue(1, "COL1"))
                .as("The loader must sense the column of the second row and replace its"
                        + " token.")
                .isEqualTo("val 1");
    }

    @Test
    void testLoad_columnSensingBuilder_stillDispatchesTheOtherFormats() throws Exception
    {
        final FileExtensionDataFileLoader columnSensingLoader = new FileExtensionDataFileLoader(
                new FlatXmlDataSetBuilder().setColumnSensing(true));

        final IDataSet ds = columnSensingLoader.load("/org/dbunit/util/fileloader/test.json");

        assertThat(ds.getTableNames())
                .as("The flat XML builder must not change how the other formats load.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testConstructor_nullFlatXmlBuilder_throwsIllegalArgumentException()
    {
        assertThatThrownBy(() -> new FileExtensionDataFileLoader((FlatXmlDataSetBuilder) null))
                .as("A null flat XML builder must be rejected when the loader is built, not"
                        + " when it first loads a flat XML file.")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The flat XML builder is null.");
    }
}
