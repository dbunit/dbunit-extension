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

package org.dbunit.dataset.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.common.handlers.IllegalInputCharacterException;
import org.dbunit.dataset.common.handlers.PipelineException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Federico Spinazzi
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.5 (Sep 17, 2003)
 */
public class CsvProducer implements IDataSetProducer {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CsvProducer.class);

    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();
    private IDataSetConsumer _consumer = EMPTY_CONSUMER;
    private String _theDirectory;

    public CsvProducer(String theDirectory) {
        _theDirectory = theDirectory;
    }

    public CsvProducer(File theDirectory) {
        _theDirectory = theDirectory.getAbsolutePath();
    }

    public void setConsumer(IDataSetConsumer consumer) throws DataSetException {
        logger.debug("setConsumer(consumer) - start");

        _consumer = consumer;
    }

    public void produce() throws DataSetException {
        logger.debug("produce() - start");

        File dir = Paths.get(_theDirectory).toFile();

        if (!dir.isDirectory()) {
            throw new DataSetException("'" + _theDirectory + "' should be a directory");
        }

        _consumer.startDataSet();
        try {
        	List tableSpecs = CsvProducer.getTables(dir.toURL(), CsvDataSet.TABLE_ORDERING_FILE);
        	for (Iterator tableIter = tableSpecs.iterator(); tableIter.hasNext();) {
				String table = (String) tableIter.next();
	            try {
	                produceFromFile(dir.toPath().resolve(table + ".csv").toFile());
	            } catch (CsvParserException e) {
	                throw new DataSetException("error producing dataset for table '" + table + "'", e);
	            } catch (DataSetException e) {
	            	throw new DataSetException("error producing dataset for table '" + table + "'", e);
	            }

			}
            _consumer.endDataSet();
        } catch (IOException e) {
        	throw new DataSetException("error getting list of tables", e);
        }
    }

    private void produceFromFile(File theDataFile) throws DataSetException, CsvParserException {
        logger.debug("produceFromFile(theDataFile={}) - start", theDataFile);

        try {
            CsvParser parser = new CsvParserImpl();
            List readData = parser.parse(theDataFile);
            List readColumns = ((List) readData.get(0));
            Column[] columns = new Column[readColumns.size()];

            for (int i = 0; i < readColumns.size(); i++) {
                String columnName = (String) readColumns.get(i);
                columnName = columnName.trim();
                columns[i] = new Column(columnName, DataType.UNKNOWN);
            }

            String tableName = theDataFile.getName().substring(0, theDataFile.getName().indexOf(".csv"));
            ITableMetaData metaData = new DefaultTableMetaData(tableName, columns);
            _consumer.startTable(metaData);
            for (int i = 1 ; i < readData.size(); i++) {
                List rowList = (List)readData.get(i);
                Object[] row = rowList.toArray();
                for(int col = 0; col < row.length; col++) {
                    row[col] = row[col].equals(CsvDataSetWriter.NULL) ? null : row[col];
                }
                _consumer.row(row);
            }
            _consumer.endTable();
        } catch (PipelineException e) {
            throw new DataSetException(e);
        } catch (IllegalInputCharacterException e) {
            throw new DataSetException(e);
        } catch (IOException e) {
            throw new DataSetException(e);
        }
    }

	/**
	 * Get a list of tables that this producer will create
	 * @return a list of Strings, where each item is a CSV file relative to the base URL
	 * @throws IOException when IO on the base URL has issues.
	 */
	public static List getTables(URL base, String tableList) throws IOException {
        logger.debug("getTables(base={}, tableList={}) - start", base, tableList);

		List orderedNames = new ArrayList();
		InputStream tableListStream = new URL(base, tableList).openStream();
		BufferedReader reader = null;
		try {
    		reader = new BufferedReader(new InputStreamReader(tableListStream));
    		String line = null;
    		while((line = reader.readLine()) != null) {
    			String table = line.trim();
    			if (table.length() > 0) {
    				orderedNames.add(table);
    			}
    		}
		}
		finally {
		    if(reader != null)
		    {
		        reader.close();
		    }
		}
		return orderedNames;
	}

}
