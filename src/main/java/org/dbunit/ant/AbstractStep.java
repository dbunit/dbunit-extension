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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.ProjectComponent;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.QueryDataSet;
import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ForwardOnlyDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.csv.CsvProducer;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.dbunit.dataset.stream.StreamingDataSet;
import org.dbunit.dataset.xml.FlatDtdProducer;
import org.dbunit.dataset.xml.FlatXmlProducer;
import org.dbunit.dataset.xml.XmlProducer;
import org.dbunit.dataset.yaml.YamlProducer;
import org.dbunit.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.1 (Apr 3, 2004)
 */
public abstract class AbstractStep extends ProjectComponent implements DbUnitTaskStep
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractStep.class);

    public static final String FORMAT_FLAT = "flat";
    public static final String FORMAT_XML = "xml";
    public static final String FORMAT_DTD = "dtd";
    public static final String FORMAT_CSV = "csv";
    public static final String FORMAT_XLS = "xls";
    public static final String FORMAT_YML = "yml";

    private boolean ordered = false;

    
    protected IDataSet getDatabaseDataSet(IDatabaseConnection connection,
            List tables) throws DatabaseUnitException
    {
    	if (logger.isDebugEnabled())
    	{
            logger.debug("getDatabaseDataSet(connection={}, tables={}) - start",
            	 connection, tables);
    	}

        try
        {
            DatabaseConfig config = connection.getConfig();

            // Retrieve the complete database if no tables or queries specified.
            if (tables.size() == 0)
            {
            	logger.debug("Retrieving the whole database because tables/queries have not been specified");
                return connection.createDataSet();
            }

            List queryDataSets = createQueryDataSet(tables, connection);

            IDataSet[] dataSetsArray = null;
            if (config.getProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY)
                    .getClass().getName().equals("org.dbunit.database.ForwardOnlyResultSetTableFactory")) {
                dataSetsArray = createForwardOnlyDataSetArray(queryDataSets);
            } else {
                dataSetsArray = (IDataSet[]) queryDataSets.toArray(new IDataSet[queryDataSets.size()]);
            }
            return new CompositeDataSet(dataSetsArray);
        }
        catch (SQLException e)
        {
            throw new DatabaseUnitException(e);
        }
    }


    private ForwardOnlyDataSet[] createForwardOnlyDataSetArray(List<QueryDataSet> dataSets) throws DataSetException, SQLException {
        ForwardOnlyDataSet[] forwardOnlyDataSets = new ForwardOnlyDataSet[dataSets.size()];

        for (int i = 0; i < dataSets.size(); i++) {
            forwardOnlyDataSets[i] = new ForwardOnlyDataSet(dataSets.get(i));
        }

        return forwardOnlyDataSets;
    }
   
	private List createQueryDataSet(List tables, IDatabaseConnection connection) 
	throws DataSetException, SQLException 
	{
		logger.debug("createQueryDataSet(tables={}, connection={})", tables, connection);
		
		List queryDataSets = new ArrayList();
		
        QueryDataSet queryDataSet = new QueryDataSet(connection);
        
        for (Iterator it = tables.iterator(); it.hasNext();)
        {
            Object item = it.next();
            
            if(item instanceof QuerySet) {
				if(queryDataSet.getTableNames().length > 0) 
            		queryDataSets.add(queryDataSet);
				
				QueryDataSet newQueryDataSet = (((QuerySet)item).getQueryDataSet(connection));
				queryDataSets.add(newQueryDataSet);
				queryDataSet = new QueryDataSet(connection);
            }
            else if (item instanceof Query)
            {
                Query queryItem = (Query)item;
                queryDataSet.addTable(queryItem.getName(), queryItem.getSql());
            }
            else if (item instanceof Table)
            {
                Table tableItem = (Table)item;
                queryDataSet.addTable(tableItem.getName());
            }
            else
            {
            	throw new IllegalArgumentException("Unsupported element type " + item.getClass().getName() + ".");
            }
        }
        
        if(queryDataSet.getTableNames().length > 0) 
        	queryDataSets.add(queryDataSet);
        
        return queryDataSets;
	}


	protected IDataSet getSrcDataSet(File src, String format,
            boolean forwardonly) throws DatabaseUnitException
    {
		if (logger.isDebugEnabled())
		{
			logger.debug("getSrcDataSet(src={}, format={}, forwardonly={}) - start",
					 src, format, forwardonly);
		}

        try
        {
            IDataSetProducer producer = null;
            if (format.equalsIgnoreCase(FORMAT_XML))
            {
                producer = new XmlProducer(getInputSource(src));
            }
            else if (format.equalsIgnoreCase(FORMAT_CSV))
            {
                producer = new CsvProducer(src);
            }
            else if (format.equalsIgnoreCase(FORMAT_FLAT))
            {
                producer = new FlatXmlProducer(getInputSource(src), true, true);
            }
            else if (format.equalsIgnoreCase(FORMAT_DTD))
            {
                producer = new FlatDtdProducer(getInputSource(src));
            }
            else if (format.equalsIgnoreCase(FORMAT_XLS))
            {
                return new CachedDataSet(new XlsDataSet(src));
            }
            else if (format.equalsIgnoreCase(FORMAT_YML))
            {
                return new CachedDataSet(new YamlProducer(src), true);
            }
            else
            {
                throw new IllegalArgumentException("Type must be either 'flat'(default), 'xml', 'csv', 'xls', 'yml' or 'dtd' but was: " + format);
            }

            if (forwardonly)
            {
                return new StreamingDataSet(producer);
            }
            return new CachedDataSet(producer);
        }
        catch (IOException e)
        {
            throw new DatabaseUnitException(e);
        }
    }
    

    /**
	 * Checks if the given format is a format which contains tabular data.
	 * @param format The format to check
	 * @return <code>true</code> if the given format is a data format. A data
	 * format is a format which holds tabular data that can be loaded via dbunit.
	 * An example for a data format is "xml" or "flat". A non-data format is "dtd" which
	 * holds only metadata information.
	 * @since 2.4
	 */
	public boolean isDataFormat(String format)
	{
        logger.debug("isDataFormat(format={}) - start", format);

        return format.equalsIgnoreCase(FORMAT_FLAT)
                || format.equalsIgnoreCase(FORMAT_XML)
                || format.equalsIgnoreCase(FORMAT_CSV)
                || format.equalsIgnoreCase(FORMAT_XLS)
                || format.equalsIgnoreCase(FORMAT_YML);
    }

    /**
     * Checks if the given data format is a valid one according to
     * the method {@link #isDataFormat(String)}. If it is not an
     * {@link IllegalArgumentException} is thrown.
     * @param format The format to check
     * @throws IllegalArgumentException If the given format is not
     * a valid data format
     * @since 2.4
     */
    protected void checkDataFormat(String format) 
    {
        logger.debug("checkDataFormat(format={}) - start", format);

        if (!isDataFormat(format))
        {
            throw new IllegalArgumentException("format must be either 'flat'(default), 'xml', 'csv', 'xls' or 'yml' but was: " + format);
        }
    }

	
	/**
	 * Creates and returns an {@link InputSource}
	 * @param file The file for which an {@link InputSource} should be created
	 * @return The input source for the given file
	 * @throws MalformedURLException
	 */
	public static InputSource getInputSource(File file) throws MalformedURLException
	{
        InputSource source = FileHelper.createInputSource(file);
        return source;
	}
	
    public boolean isOrdered() 
    {
        return ordered;
    }

    public void setOrdered(boolean ordered) 
    {
        this.ordered = ordered;
    }
    
    public String toString()
    {
        final StringBuilder result = new StringBuilder();
        result.append("AbstractStep: ");
        result.append("ordered=").append(this.ordered);
        return result.toString();
    }

}
