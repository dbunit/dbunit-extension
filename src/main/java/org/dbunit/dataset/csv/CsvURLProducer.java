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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;

/**
 * A {@link IDataSetProducer Data Set Producer} that produces datasets from 
 * CVS files found at a base URL.
 * 
 * Based HEAVILY on {@link org.dbunit.dataset.csv.CsvProducer}.
 *  
 * @author Dion Gillard
 * @author Federico Spinazzi
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Sep 12, 2004 (pre 2.3)
 */
public class CsvURLProducer implements IDataSetProducer {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CsvURLProducer.class);

	/** the default consumer - does nothing */
    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();

    /**
     * the consumer of the produced datasets, by default a 
     * {@link DefaultConsumer}
     */
    private IDataSetConsumer _consumer = EMPTY_CONSUMER;
    
    /** the base url to retrieve data from */
    private URL base;

    /** the offset from the base url where the list of tables can be found */
    private String tableList;
    
    /**
     * Create a CSV Data Set Producer which uses the base URL to retrieve 
     * a list of tables and the data.
     * @param base the URL where the tableList and data can be found. 
     * @param tableList the relative location of the list of tables.
     */
    public CsvURLProducer(URL base, String tableList)
    {
    	this.base = base;
    	this.tableList = tableList;
    }
    
    /*
	 * @see IDataSetProducer#setConsumer(org.dbunit.dataset.stream.IDataSetConsumer)
	 */
	public void setConsumer(IDataSetConsumer consumer) throws DataSetException {
        logger.debug("setConsumer(consumer) - start");

		_consumer = consumer;
	}

	/*
	 * @see IDataSetProducer#produce()
	 */
	public void produce() throws DataSetException {
        logger.debug("produce() - start");

        _consumer.startDataSet();
        try {
        	List tableSpecs = CsvProducer.getTables(base, tableList);
        	for (Iterator tableIter = tableSpecs.iterator(); tableIter.hasNext();) {
				String table = (String) tableIter.next();
	            try {
	                produceFromURL(CsvProducer.resolveRelative(base, table + ".csv"));
	            } catch (CsvParserException e) {
	                throw new DataSetException("error producing dataset for table '" + table + "'", e);
	            }

			}
            _consumer.endDataSet();
        } catch (IOException e) {
        	throw new DataSetException("error getting list of tables", e);
        }
	}

	/**
	 * Produce a dataset from a URL. 
	 * The URL is assumed to contain data in CSV format.
	 * @param url a url containing CSV data.
	 */
	private void produceFromURL(URL url) throws DataSetException {
        logger.debug("produceFromURL(url=" + url + ") - start");

        try {
            CsvParser parser = new CsvParserImpl();
            List readData = parser.parse(url);
            List readColumns = (List) readData.get(0);
            Column[] columns = new Column[readColumns.size()];

            for (int i = 0; i < readColumns.size(); i++) {
                columns[i] = new Column((String) readColumns.get(i), DataType.UNKNOWN);
            }

            String tableName = url.getFile();
            tableName = tableName.substring(tableName.lastIndexOf("/")+1, tableName.indexOf(".csv"));
            ITableMetaData metaData = new DefaultTableMetaData(tableName, columns);
            _consumer.startTable(metaData);
            for (int i = 1 ; i < readData.size(); i++) {
                List rowList = (List)readData.get(i);
                Object[] row = rowList.toArray();
                for(int col = 0; col < row.length; col++) {
                	if (CsvDataSetWriter.NULL.equals(row[col])) {
                		row[col] = null;
                	}
                }
                _consumer.row(row);
            }
            _consumer.endTable();
        } catch (CsvParserException e) {
        	throw new DataSetException("error parsing CSV for URL: '" + url + "'", e);
		} catch (IOException e) {
        	throw new DataSetException("I/O error parsing CSV for URL: '" + url + "'", e);
		}
	}
}
