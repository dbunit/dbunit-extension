/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
class XlsDataFileLoaderTest
{
    XlsDataFileLoader loader = null;

    /**
     * {@inheritDoc}
     */
    @BeforeEach
    protected void setUp() throws Exception
    {
        loader = new XlsDataFileLoader();
    }

    /**
     * Test can load the specified file.
     */
    @Test
    void testLoad_withValidXlsFile_returnsNonEmptyDataSet() throws DataSetException
    {
        final String filename = "/xml/dataSetTest.xls";
        final IDataSet ds = loader.load(filename);
        assertThat(ds.getTableNames()).as("No tables found in dataset.")
                .hasSizeGreaterThan(0);
        // DataSet loading tests verify data accuracy
    }

    @ParameterizedTest
    @ValueSource(strings = {"/xml/dataSetTest.xls", "/xml/dataSetTest.xlsx"})
    void testLoadDataSet_validWorkbook_closesTheStreamItOpened(final String resource)
            throws Exception
    {
        final AtomicBoolean closed = new AtomicBoolean();
        final URL tracked = urlWhoseStreamRecordsClose(resource, closed);

        final IDataSet ds = loader.loadDataSet(tracked);

        assertThat(ds.getTableNames()).as("The workbook must still load.")
                .hasSizeGreaterThan(0);
        assertThat(closed.get())
                .as("The loader opened the stream, so it must close it once the workbook is read.")
                .isTrue();
    }

    @Test
    void testLoadDataSet_contentIsNotAWorkbook_closesTheStreamItOpenedAndFails()
            throws Exception
    {
        final AtomicBoolean closed = new AtomicBoolean();
        // not a workbook at all, so reading it fails before any sheet is loaded
        final URL tracked = urlWhoseStreamRecordsClose("/xml/flatXmlDataSetTest.xml", closed);

        assertThatThrownBy(() -> loader.loadDataSet(tracked))
                .as("Content that is not a workbook must fail to load.")
                .isInstanceOf(IOException.class);

        assertThat(closed.get())
                .as("The stream must be closed even when reading the workbook fails.")
                .isTrue();
    }

    private URL urlWhoseStreamRecordsClose(final String resource, final AtomicBoolean closed)
            throws Exception
    {
        final URL source = getClass().getResource(resource);
        return new URL(null, "tracked:" + resource, new URLStreamHandler()
        {
            @Override
            protected URLConnection openConnection(final URL url)
            {
                return new URLConnection(url)
                {
                    @Override
                    public void connect()
                    {
                    }

                    @Override
                    public InputStream getInputStream() throws IOException
                    {
                        return new FilterInputStream(source.openStream())
                        {
                            @Override
                            public void close() throws IOException
                            {
                                closed.set(true);
                                super.close();
                            }
                        };
                    }
                };
            }
        });
    }
}
