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
package org.dbunit.dataset.excel;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dbunit.dataset.AbstractDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableIterator;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.OrderedTableNameMap;
import org.dbunit.dataset.RequiredLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This dataset implementation can read and write MS Excel documents. Each
 * sheet represents a table. The first row of a sheet defines the columns names
 * and remaining rows contains the data.
 *
 * @author Manuel Laflamme
 * @since Feb 21, 2003
 * @version $Revision$
 */
public class XlsDataSet extends AbstractDataSet
{
    
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(XlsDataSet.class);

    private final OrderedTableNameMap _tables;


    /**
     * Creates a new XlsDataSet object that loads the specified Excel document.
     *
     * @param file the Excel document to load.
     * @throws IOException if the file cannot be read.
     * @throws DataSetException if the document cannot be parsed, or Apache POI is not on the
     *         classpath.
     */
    public XlsDataSet(File file) throws IOException, DataSetException
    {
        RequiredLibrary.POI.require(XlsDataSet.class);
        _tables = super.createTableNameMap();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath())))
        {
            loadSheets(in);
        }
    }

    /**
     * Creates a new XlsDataSet object that loads the specified Excel document.
     *
     * @param in the Excel document to load.
     * @throws IOException if the stream cannot be read.
     * @throws DataSetException if the document cannot be parsed, or Apache POI is not on the
     *         classpath.
     */
    public XlsDataSet(InputStream in) throws IOException, DataSetException
    {
        RequiredLibrary.POI.require(XlsDataSet.class);
        _tables = super.createTableNameMap();

        loadSheets(in);
    }

    private void loadSheets(InputStream in) throws IOException, DataSetException
    {
        for (ITable table : PoiWorkbook.readTables(in))
        {
            _tables.add(table.getTableMetaData().getTableName(), table);
        }
    }

    /**
     * The only code in this dataset that uses Apache POI directly, kept in a class of its own
     * so that loading {@link XlsDataSet} does not itself need Apache POI and its constructors
     * can say what is missing.
     */
    private static class PoiWorkbook
    {
        static List<ITable> readTables(InputStream in) throws IOException, DataSetException
        {
            Workbook workbook = createWorkbook(in);
            List<ITable> tables = new ArrayList<>();
            int sheetCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++)
            {
                tables.add(new XlsTable(workbook.getSheetName(i), workbook.getSheetAt(i)));
            }
            return tables;
        }

        private static Workbook createWorkbook(InputStream in) throws IOException
        {
            try
            {
                return WorkbookFactory.create(in);
            } catch (EncryptedDocumentException e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * Write the specified dataset to the specified Excel document.
     *
     * @param dataSet the dataset to write.
     * @param out the stream to write the Excel document to.
     * @throws IOException if writing to the stream fails.
     * @throws DataSetException if the dataset cannot be read, or Apache POI is not on the
     *         classpath.
     */
    public static void write(IDataSet dataSet, OutputStream out)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, out={}) - start", dataSet, out);

        RequiredLibrary.POI.require(XlsDataSet.class);
        new XlsDataSetWriter().write(dataSet, out);
    }


    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
    	if(logger.isDebugEnabled())
    		logger.debug("createIterator(reversed={}) - start", String.valueOf(reversed));

    	ITable[] tables = (ITable[]) _tables.orderedValues().toArray(new ITable[0]);
        return new DefaultTableIterator(tables, reversed);
    }
}
