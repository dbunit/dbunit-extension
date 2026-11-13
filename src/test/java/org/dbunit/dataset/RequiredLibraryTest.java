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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.DatabaseUnitRuntimeException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link RequiredLibrary}, asking the class loader of a requester that cannot see
 * the library.
 */
class RequiredLibraryTest
{
    private static final String JSON_DATA_SET = "org.dbunit.dataset.json.JsonDataSet";
    private static final String YAML_DATA_SET = "org.dbunit.dataset.yaml.YamlDataSet";
    private static final String XLS_DATA_SET = "org.dbunit.dataset.excel.XlsDataSet";

    @Test
    void testRequire_jacksonMissing_failsSayingWhatToAdd() throws Exception
    {
        final Class<?> requester = requesterWithout("com.fasterxml.", JSON_DATA_SET);

        assertThatThrownBy(() -> RequiredLibrary.JACKSON.require(requester))
                .as("The message names the feature, the library, the coordinates and that the"
                        + " library is optional.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("JSON support needs Jackson, which is not on the classpath. Add"
                        + " com.fasterxml.jackson.core:jackson-databind to the classpath. Jackson"
                        + " is an optional dependency of dbUnit.");
    }

    @Test
    void testRequire_snakeYamlMissing_failsSayingWhatToAdd() throws Exception
    {
        final Class<?> requester = requesterWithout("org.yaml.", YAML_DATA_SET);

        assertThatThrownBy(() -> RequiredLibrary.SNAKEYAML.require(requester))
                .as("The message names the feature, the library, the coordinates and that the"
                        + " library is optional.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("YAML support needs SnakeYAML, which is not on the classpath. Add"
                        + " org.yaml:snakeyaml to the classpath. SnakeYAML is an optional"
                        + " dependency of dbUnit.");
    }

    @Test
    void testRequire_poiMissing_failsNamingPoiAndPoiOoxmlForXlsx() throws Exception
    {
        final Class<?> requester = requesterWithout("org.apache.poi.", XLS_DATA_SET);

        assertThatThrownBy(() -> RequiredLibrary.POI.require(requester))
                .as("The message names both artifacts, as an .xlsx file needs poi-ooxml too.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("Excel support needs Apache POI, which is not on the classpath. Add"
                        + " org.apache.poi:poi (and org.apache.poi:poi-ooxml for an .xlsx file) to"
                        + " the classpath. Apache POI is an optional dependency of dbUnit.");
    }

    @Test
    void testRequireUnchecked_poiMissing_failsWithTheSameMessageUnchecked() throws Exception
    {
        final Class<?> requester = requesterWithout("org.apache.poi.", XLS_DATA_SET);

        assertThatThrownBy(() -> RequiredLibrary.POI.requireUnchecked(requester))
                .isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining("Excel support needs Apache POI");
    }

    @Test
    void testRequire_libraryPresent_doesNotFail()
    {
        assertThatCode(() -> {
            RequiredLibrary.JACKSON.require(RequiredLibraryTest.class);
            RequiredLibrary.SNAKEYAML.require(RequiredLibraryTest.class);
            RequiredLibrary.POI.require(RequiredLibraryTest.class);
        }).as("Every optional library is on the test classpath.").doesNotThrowAnyException();
    }

    private static Class<?> requesterWithout(final String hiddenPrefix, final String className)
            throws ClassNotFoundException
    {
        final ClassLoader loader = new LibraryHidingClassLoader(hiddenPrefix, className);
        final Class<?> requester = loader.loadClass(className);
        assertThat(requester.getClassLoader()).as("The requester must be defined by the hiding loader.").isSameAs(loader);
        return requester;
    }
}
