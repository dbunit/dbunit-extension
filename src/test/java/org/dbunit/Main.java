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

package org.dbunit;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.ForwardOnlyResultSetTableFactory;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.dbunit.dataset.stream.MockDataSetProducer;
import org.dbunit.dataset.stream.StreamingDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.dataset.xml.FlatXmlProducer;
import org.dbunit.dataset.xml.FlatXmlWriter;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.dataset.xml.XmlDataSetWriter;
import org.xml.sax.InputSource;

/**
 * This class is a scratchpad used to try new features.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 14, 2002
 */
public class Main
{
    public static void main(final String[] args) throws Exception
    {
        // System.setProperty("dbunit.qualified.table.names", "true");

        // testFlatXmlWriter();
        testXmlWriter();

        /*
         * IDatabaseConnection connection =
         * DatabaseEnvironment.getInstance().getConnection();
         * 
         * // IDataSet dataSet = new
         * XmlDataSet(TestUtils.getFileReader("xml/dataSetTest.xml"));
         * InputSource source = new
         * InputSource(TestUtils.getFile("xml/xmlTableTest.xml").toURL().
         * toString()); // InputSource source = new InputSource(new
         * File("writerTest.xml").toURL().toString()); FlatXmlProducer
         * flatXmlProducer = new FlatXmlProducer(source); XmlProducer
         * xmlProducer = new XmlProducer(source); MockDataSetProducer producer =
         * new MockDataSetProducer(); producer.setupColumnCount(4);
         * producer.setupRowCount(2); producer.setupTableCount(3); IDataSet
         * dataSet = new StreamingDataSet(xmlProducer); // IDataSet dataSet =
         * new StreamingDataSet(xmlProducer);
         * 
         * // System.out.println(connection.createDataSet());
         * 
         * // DatabaseOperation.INSERT.execute(connection, dataSet);
         * 
         * // IDataSet dataSet = connection.createDataSet(); // OutputStream out
         * = new FileOutputStream("c://writerTest.xml"); OutputStream out =
         * System.out; // FlatXmlWriter writer = new FlatXmlWriter(new
         * OutputStreamWriter(out, "UTF8")); XmlDataSetWriter writer = new
         * XmlDataSetWriter(new OutputStreamWriter(out, "UTF8"));
         * writer.write(dataSet);
         */

        // FileWriter writer = new FileWriter("writerTest.xml");
        // FlatXmlDataSet.write(connection.createDataSet(), writer);
        // new FlatXmlWriter().write(connection.createDataSet(), writer);
        // writer.close();
        // ITableIterator iterator = connection.createDataSet().iterator();
        // while(iterator.next())
        // {
        // System.out.println(iterator.getTableMetaData().getTableName());
        // }
        // oldMain();
        // testWrite();
        // writeXls();
        // newSheet();
        // createCells();
        // createDateCells();
        // readWriteWorkbook();
        // cellTypes();
    }

    private static void testFlatXmlWriter() throws Exception
    {
        final MockDataSetProducer mockProducer = new MockDataSetProducer();
        mockProducer.setupColumnCount(5);
        mockProducer.setupRowCount(100000);
        mockProducer.setupTableCount(10);
        final IDataSet dataSet = new StreamingDataSet(mockProducer);

        final OutputStream out = new FileOutputStream("flatXmlWriterTest.xml");
        final FlatXmlWriter writer = new FlatXmlWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(dataSet);
    }

    private static void testXmlWriter() throws Exception
    {
        final MockDataSetProducer mockProducer = new MockDataSetProducer();
        mockProducer.setupColumnCount(5);
        mockProducer.setupRowCount(100000);
        mockProducer.setupTableCount(10);
        final IDataSet dataSet = new StreamingDataSet(mockProducer);

        final OutputStream out = new FileOutputStream("xmlWriterTest.xml");
        final XmlDataSetWriter writer = new XmlDataSetWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(dataSet);
    }

    // private static void testWrite() throws Exception
    // {
    // Writer out = new databaseFileWriter("test.xml");
    //
    // Document document = new Document();
    // document.write(out);
    // out.flush();
    // }

    public void test() throws Exception
    {
        final Connection jdbcConnection = null;
        final IDatabaseConnection connection =
                new DatabaseConnection(jdbcConnection, "");
        final DatabaseConfig config = connection.getConfig();

        // Use the ForwardOnlyResultSetTableFactory to export very large
        // dataset.
        config.setProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY,
                new ForwardOnlyResultSetTableFactory());

        // Use the StreamingDataSet to import very large dataset.
        final IDataSetProducer producer =
                new FlatXmlProducer(new InputSource("dataset.xml"));
        final IDataSet dataSet = new StreamingDataSet(producer);
    }

    private static void oldMain() throws Exception
    {

        // System.setProperty("dbunit.name.escapePattern", "\"?\"");
        final IDatabaseConnection connection =
                DatabaseEnvironment.getInstance().getConnection();
        // IDataSet dataSet = new XmlDataSet(new FileReader("dataSetTest.xml"));
        // DatabaseOperation.CLEAN_INSERT.execute(connection, dataSet);

        // String[] tableNames = connection.createDataSet().getTableNames();
        // Arrays.sort(tableNames);
        // FlatXmlDataSet.writeDtd(new FilteredDataSet(tableNames,
        // connection.createDataSet()),
        // new FileOutputStream("test.dtd"));
        //
        //
        final Writer out = new FileWriter("test.xml");
        // FlatXmlDataSet.write(connection.createDataSet(), out, "ISO-8859-1");
        FlatXmlDataSet.write(connection.createDataSet(), out);
        // out.flush();
        // out.close();

        // ////////////////////////////////
        // Document document = new
        // Document(TestUtils.getFile("xml/flatXmlDataSetTest.xml"));
        // DocType docType = document.getDocType();
        // System.out.println(docType);
        //
        // // display children of DocType
        // for (Children decls = docType.getChildren();
        // decls.hasMoreElements();)
        // {
        // Child decl = decls.next();
        // String type = decl.getClass().getName();
        // System.out.println("decl = " + decl + ", class: " + type);
        // }

        // IDataSet dataSet = new FlatXmlDataSet(
        // new FileInputStream("flatXmlDataSetTest.xml"));
        // FlatDtdDataSet.write(new FlatXmlDataSet(
        // TestUtils.getFileInputStream("xml/flatXmlDataSetTest.xml")),
        // new FileOutputStream("src/dtd/flatXmlDataSetTest.dtd"));
    }

    private static void writeXls() throws IOException, DataSetException
    {
        final Reader in =
                new FileReader("P:/dbunit-cvs/dbunit/src/xml/dataSetTest.xml");
        final FileOutputStream out =
                new FileOutputStream("P:/dbunit-cvs/dbunit/dataSetTest.xls");
        XlsDataSet.write(new XmlDataSet(in), out);
        out.close();
    }

    /*
     * public static void newSheet() throws Exception { HSSFWorkbook wb = new
     * HSSFWorkbook(); HSSFSheet sheet1 = wb.createSheet("new sheet"); HSSFSheet
     * sheet2 = wb.createSheet("second sheet"); FileOutputStream fileOut = new
     * FileOutputStream("workbook.xls"); wb.write(fileOut); fileOut.close(); }
     * 
     * public static void readWriteWorkbook() throws Exception { POIFSFileSystem
     * fs = new POIFSFileSystem(new FileInputStream("workbook.xls"));
     * HSSFWorkbook wb = new HSSFWorkbook(fs); HSSFSheet sheet =
     * wb.getSheetAt(0); HSSFRow row = sheet.getRow(2); HSSFCell cell =
     * row.getCell((short)3); if (cell == null) cell = row.createCell((short)3);
     * cell.setCellType(HSSFCell.CELL_TYPE_STRING); cell.setCellValue("a test");
     * 
     * // Write the output to a file FileOutputStream fileOut = new
     * FileOutputStream("workbook.xls"); wb.write(fileOut); fileOut.close(); }
     * 
     * public static void cellTypes() throws Exception { HSSFWorkbook wb = new
     * HSSFWorkbook(); HSSFSheet sheet = wb.createSheet("new sheet"); HSSFRow
     * row = sheet.createRow((short)2); row.createCell((short)
     * 0).setCellValue(1.1); row.createCell((short) 1).setCellValue(new Date());
     * row.createCell((short) 2).setCellValue("a string");
     * row.createCell((short) 3).setCellValue(true); row.createCell((short)
     * 4).setCellType(HSSFCell.CELL_TYPE_ERROR);
     * 
     * // Write the output to a file FileOutputStream fileOut = new
     * FileOutputStream("workbook.xls"); wb.write(fileOut); fileOut.close(); }
     * 
     * public static void createCells() throws Exception { HSSFWorkbook wb = new
     * HSSFWorkbook(); HSSFSheet sheet = wb.createSheet("new sheet");
     * 
     * // Create a row and put some cells in it. Rows are 0 based. HSSFRow row =
     * sheet.createRow((short)0); // Create a cell and put a value in it.
     * HSSFCell cell = row.createCell((short)0); cell.setCellValue(1);
     * 
     * // Or do it on one line. row.createCell((short)1).setCellValue(1.2);
     * row.createCell((short)2).setCellValue("This is a string");
     * row.createCell((short)3).setCellValue(true);
     * 
     * // Write the output to a file FileOutputStream fileOut = new
     * FileOutputStream("workbook.xls"); wb.write(fileOut); fileOut.close(); }
     * 
     * public static void createDateCells() throws Exception { HSSFWorkbook wb =
     * new HSSFWorkbook(); HSSFSheet sheet = wb.createSheet("new sheet");
     * 
     * // Create a row and put some cells in it. Rows are 0 based. HSSFRow row =
     * sheet.createRow((short)0);
     * 
     * // Create a cell and put a date value in it. The first cell is not styled
     * as a date. HSSFCell cell = row.createCell((short)0);
     * cell.setCellValue(new Date());
     * 
     * // we style the second cell as a date (and time). It is important to
     * create a new cell style from the workbook // otherwise you can end up
     * modifying the built in style and effecting not only this cell but other
     * cells. HSSFCellStyle cellStyle = wb.createCellStyle();
     * cellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy h:mm"));
     * cell = row.createCell((short)1); cell.setCellValue(new Date());
     * cell.setCellStyle(cellStyle);
     * 
     * // Write the output to a file FileOutputStream fileOut = new
     * FileOutputStream("workbook.xls"); wb.write(fileOut); fileOut.close(); }
     */

}
