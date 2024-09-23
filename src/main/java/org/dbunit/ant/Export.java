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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tools.ant.Project;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseSequenceFilter;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.csv.CsvDataSetWriter;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.dataset.xml.FlatDtdDataSet;
import org.dbunit.dataset.xml.FlatXmlWriter;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.dataset.yaml.YamlDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>Export</code> class is the step that facilitates exporting
 * the contents of the database and/or it's corresponding DTD to a file.
 * The export can be performed on a full dataset or a partial one if
 * specific table names are identified.
 *
 * @author Timothy Ruppert
 * @author Ben Cox
 * @version $Revision$
 * @since Jun 10, 2002
 * @see DbUnitTaskStep
 */
public class Export extends AbstractStep
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Export.class);

    private File _dest;
    private String _format = FORMAT_FLAT;
    private String _doctype = null;
    private Charset _encoding = StandardCharsets.UTF_8; // if no encoding set by script than the default encoding (UTF-8) of the wrietr is used
    private List _tables = new ArrayList();

    public Export()
    {
    }

    private String getAbsolutePath(File filename)
    {
        return filename != null ? filename.getAbsolutePath() : "null";
    }

    public File getDest()
    {
        return _dest;
    }

    public String getFormat()
    {
        return _format;
    }

    public List getTables()
    {
        return _tables;
    }

    public void setDest(File dest)
    {
        logger.debug("setDest(dest={}) - start", dest);
        _dest = dest;
    }

    public void setFormat(String format)
    {
        logger.debug("setFormat(format={}) - start", format);

        if (format.equalsIgnoreCase(FORMAT_FLAT)
                || format.equalsIgnoreCase(FORMAT_XML)
                || format.equalsIgnoreCase(FORMAT_DTD)
                || format.equalsIgnoreCase(FORMAT_CSV)
                || format.equalsIgnoreCase(FORMAT_XLS)
                || format.equalsIgnoreCase(FORMAT_YML))
        {
            _format = format;
        }
        else
        {
            throw new IllegalArgumentException("Type must be one of: 'flat'(default), 'xml', 'dtd', 'xls' or 'yml' but was: " + format);
        }
    }

    /**
     * Encoding for XML-Output
     * @return Returns the encoding.
     */
    public Charset getEncoding()
    {
        return this._encoding;
    }

    public void setEncoding(String encoding)
    {
        setEncoding(Charset.forName(encoding));
    }

    public void setEncoding(Charset encoding)
    {
        this._encoding = encoding;
    }

    public void addTable(Table table)
    {
        logger.debug("addTable(table={}) - start", table);
        _tables.add(table);
    }

    public void addQuery(Query query)
    {
        logger.debug("addQuery(query={}) - start", query);
        _tables.add(query);
    }

	public void addQuerySet(QuerySet querySet) {
        logger.debug("addQuerySet(querySet={}) - start", querySet);
        _tables.add(querySet);
	}
	
    
	public String getDoctype()
    {
        return _doctype;
    }

    public void setDoctype(String doctype)
    {
        logger.debug("setDoctype(doctype={}) - start", doctype);
        _doctype = doctype;
    }

    public void execute(IDatabaseConnection connection) throws DatabaseUnitException
    {
        logger.debug("execute(connection={}) - start", connection);

        try
        {
            if (_dest == null)
            {
                throw new DatabaseUnitException("'_dest' is a required attribute of the <export> step.");
            }

            IDataSet dataset = getExportDataSet(connection);
			log("dataset tables: " + Arrays.asList(dataset.getTableNames()), Project.MSG_VERBOSE);

			
            // Write the dataset
            if (_format.equals(FORMAT_CSV))
            {
                CsvDataSetWriter.write(dataset, _dest);
            }
            else
            {
                OutputStream out = new FileOutputStream(_dest);
                try
                {
                    if (_format.equalsIgnoreCase(FORMAT_FLAT))
                    {
                        FlatXmlWriter writer = new FlatXmlWriter(out, getEncoding());
                        writer.setDocType(_doctype);
                        writer.write(dataset);
                    }
                    else if (_format.equalsIgnoreCase(FORMAT_XML))
                    {
                        XmlDataSet.write(dataset, out, getEncoding());
                    }
                    else if (_format.equalsIgnoreCase(FORMAT_DTD))
                    {
                        //TODO Should DTD also support encoding? It is basically an XML file...
                        FlatDtdDataSet.write(dataset, out);//, getEncoding());
                    }
                    else if (_format.equalsIgnoreCase(FORMAT_XLS))
                    {
                        XlsDataSet.write(dataset, out);
                    }
                    else if (_format.equalsIgnoreCase(FORMAT_YML))
                    {
                        YamlDataSet.write(dataset, out);
                    }
                    else
                    {
                        throw new IllegalArgumentException("The given format '"+_format+"' is not supported.");
                    }
                    
                }
                finally
                {
                    out.close();
                }
            }
            
            log("Successfully wrote file '" + _dest + "'", Project.MSG_INFO);
            
        }
        catch (SQLException e)
        {
        	throw new DatabaseUnitException(e);
        }
        catch (IOException e)
        {
            throw new DatabaseUnitException(e);
        }
    }

    /**
     * Creates the dataset that is finally used for the export
     * @param connection
     * @return The final dataset used for the export
     * @throws DatabaseUnitException
     * @throws SQLException
     */
    protected IDataSet getExportDataSet(IDatabaseConnection connection) 
    throws DatabaseUnitException, SQLException 
    {
        IDataSet dataset = getDatabaseDataSet(connection, this._tables);
        if (isOrdered()) 
        {
        	// Use topologically sorted database
        	ITableFilter filter = new DatabaseSequenceFilter(connection);  
        	dataset = new FilteredDataSet(filter, dataset);
        }
        return dataset;
	}

	public String getLogMessage()
    {
        return "Executing export: "
                + "\n      in format: " + _format
                + " to datafile: " + getAbsolutePath(_dest);
    }


    public String toString()
    {
        final StringBuilder result = new StringBuilder();
        result.append("Export: ");
        result.append(" dest=" + getAbsolutePath(_dest));
        result.append(", format= " + _format);
        result.append(", doctype= " + _doctype);
        result.append(", tables= " + _tables);

        return result.toString();
    }
}
