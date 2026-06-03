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
package org.dbunit.dataset.sqlloader;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.common.handlers.IllegalInputCharacterException;
import org.dbunit.dataset.common.handlers.PipelineException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.dbunit.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that creates an {@link IDataSet} using SQLLoader style '.ctl' files.
 * 
 * @author Stephan Strittmatter (stritti AT users.sourceforge.net), gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class SqlLoaderControlProducer implements IDataSetProducer {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SqlLoaderControlProducer.class);

    private static final String TMP_TABLE_LIST_FILENAME = "table-list.txt";


    /** The Constant NULL. */
    public static final String NULL = "null";

    /** The Constant EMPTY_CONSUMER. */
    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();

    /** The consumer. */
    private IDataSetConsumer consumer = EMPTY_CONSUMER;

    /** The control files directory */
    private final File controlFilesDir;

    /**
     * String list of the ordered table names
     */
    private List orderedTableNames;


    /**
     * The Constructor.
     * 
     * @param controlFilesDir the control files directory
     * @param tableOrderFile the table order file
     * @throws DataSetException 
     */
    public SqlLoaderControlProducer(String controlFilesDir, String tableOrderFile) 
    throws DataSetException 
    {
        this(new File(controlFilesDir), new File(tableOrderFile));
    }

    /**
     * The Constructor.
     * 
     * @param controlFilesDir the control files directory
     * @param tableOrderFile the table order file
     * @throws DataSetException 
     */
    public SqlLoaderControlProducer(File controlFilesDir, File tableOrderFile) 
    throws DataSetException 
    {
        this.controlFilesDir = controlFilesDir;
        
        try {
            this.orderedTableNames = SqlLoaderControlProducer.getTables(controlFilesDir, tableOrderFile);
        }
        catch (IOException e) {
            throw new DataSetException("error getting list of tables from file '" + tableOrderFile + "'", e);
        }
    }

    /**
     * The Constructor.
     * 
     * @param controlFilesDir the control files directory
     * @param orderedTableNames a list of strings that contains the ordered table names
     */
    public SqlLoaderControlProducer(File controlFilesDir, List orderedTableNames) {
        this.controlFilesDir = controlFilesDir;
        this.orderedTableNames = orderedTableNames;
    }

    /**
     * @see org.dbunit.dataset.stream.IDataSetProducer#setConsumer(org.dbunit.dataset.stream.IDataSetConsumer)
     */
    public void setConsumer(IDataSetConsumer consumer) throws DataSetException {
        this.consumer = consumer;
    }

    /**
     * @see org.dbunit.dataset.stream.IDataSetProducer#produce()
     */
    public void produce() throws DataSetException {
        logger.debug("produce() - start");

        File dir = this.controlFilesDir;

        if (!this.controlFilesDir.isDirectory()) {
            throw new DataSetException("'"
                    + this.controlFilesDir + "' should be a directory of the control files");
        }

        this.consumer.startDataSet();
        
        for (Iterator tableIter = this.orderedTableNames.iterator(); tableIter.hasNext();) {
            String table = (String) tableIter.next();
            try {
                File ctlFile = new File(dir, table + ".ctl");
                produceFromControlFile(ctlFile);
            }
            catch (SqlLoaderControlParserException e) {
                throw new DataSetException("error producing dataset for table '" + table + "'", e);
            }
            catch (DataSetException e) {
                throw new DataSetException("error producing dataset for table '" + table + "'", e);
            }

        }
        this.consumer.endDataSet();
    }

    /**
     * Produce from control file.
     * 
     * @param controlFile the control file
     * 
     * @throws DataSetException the data set exception
     * @throws SqlLoaderControlParserException the oracle control parser exception
     */
    private void produceFromControlFile(File controlFile) throws DataSetException,
    SqlLoaderControlParserException 
    {
        logger.debug("produceFromControlFile(controlFile={}) - start", controlFile);

        try {
            SqlLoaderControlParser parser = new SqlLoaderControlParserImpl();
            List readData = parser.parse(controlFile);
            List readColumns = ((List) readData.get(0));
            Column[] columns = new Column[readColumns.size()];

            for (int i = 0; i < readColumns.size(); i++) {
                columns[i] = new Column((String) readColumns.get(i), DataType.UNKNOWN);
            }

            String tableName = parser.getTableName();
            ITableMetaData metaData = new DefaultTableMetaData(tableName, columns);
            this.consumer.startTable(metaData);
            for (int i = 1; i < readData.size(); i++) {
                List rowList = (List) readData.get(i);
                Object[] row = rowList.toArray();
                for (int col = 0; col < row.length; col++) {
                    row[col] = row[col].equals(NULL) ? null : row[col];
                }
                this.consumer.row(row);
            }
            this.consumer.endTable();
        }
        catch (PipelineException e) {
            throw new DataSetException(e);
        }
        catch (IllegalInputCharacterException e) {
            throw new DataSetException(e);
        }
        catch (IOException e) {
            throw new DataSetException(e);
        }
    }

    /**
     * Get a list of tables that this producer will create.
     * 
     * @param controlFilesDir the base directory
     * @param tableList the table list
     * 
     * @return a list of Strings, where each item is a CSV file relative to the base URL
     * 
     * @throws IOException when IO on the base URL has issues.
     */
    public static List getTables(File controlFilesDir, File tableList) throws IOException 
    {
        logger.debug("getTables(controlFilesDir={}, tableList={}) - start", controlFilesDir, tableList);

        // Copy file into the control directory
        File tmpTableList = new File(controlFilesDir, TMP_TABLE_LIST_FILENAME);
        FileHelper.copyFile(tableList, tmpTableList);

        List orderedNames;
        try {
            orderedNames = FileHelper.readLines(tmpTableList);
        }
        finally {
            boolean success = tmpTableList.delete();
            if (!success) {
                throw new IOException("Deletion of temorary file failed: " + tmpTableList);
            }
        }
        return orderedNames;
    }

}
