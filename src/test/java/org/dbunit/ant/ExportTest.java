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
package org.dbunit.ant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link Export}.
 */
@ExtendWith(MockitoExtension.class)
class ExportTest
{
    @Mock
    private IDatabaseConnection connection;

    @Test
    void testExecute_withUppercaseCsvFormat_writesCsvOutput(
            @TempDir final File tempDir) throws Exception
    {
        stubConnection();

        final File dest = new File(tempDir, "csv-out");
        final Export export = new Export();
        export.setFormat("CSV");
        export.setDest(dest);

        export.execute(connection);

        assertThat(new File(dest, "TEST_TABLE.csv"))
                .as("CSV export must create a per-table file inside the"
                        + " destination directory even when the format"
                        + " attribute is given in upper case.")
                .exists();
    }

    @Test
    void testExecute_withUnsupportedFormat_throwsWithoutCreatingDestinationFile(
            @TempDir final File tempDir) throws Exception
    {
        final File dest = new File(tempDir, "unsupported-out");
        final Export export = new Export();
        export.setDest(dest);
        setFormatField(export, "bogus");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .as("An unrecognized format must be rejected before any"
                        + " destination file is created.")
                .isThrownBy(() -> export.execute(connection));

        assertThat(dest)
                .as("No destination file must be left behind for an"
                        + " unsupported format.")
                .doesNotExist();
    }

    private void stubConnection() throws Exception
    {
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        when(connection.createDataSet()).thenReturn(buildDataSet());
    }

    private static IDataSet buildDataSet() throws Exception
    {
        final Column[] columns = {new Column("COL0", DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, "COL0", "value");
        return new DefaultDataSet(table);
    }

    private static void setFormatField(final Export export, final String format)
            throws Exception
    {
        final Field field = Export.class.getDeclaredField("_format");
        field.setAccessible(true);
        field.set(export, format);
    }
}
