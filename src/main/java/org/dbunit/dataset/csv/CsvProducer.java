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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
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
 * {@link IDataSetProducer} that streams table and row events by parsing CSV files.
 *
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

    /**
     * Creates a producer that reads CSV files from the given directory.
     *
     * @param theDirectory the path of the directory containing the CSV files.
     */
    public CsvProducer(String theDirectory) {
        _theDirectory = theDirectory;
    }

    /**
     * Creates a producer that reads CSV files from the given directory.
     *
     * @param theDirectory the directory containing the CSV files.
     */
    public CsvProducer(File theDirectory) {
        _theDirectory = theDirectory.getAbsolutePath();
    }

    public void setConsumer(IDataSetConsumer consumer) throws DataSetException {
        logger.debug("setConsumer(consumer) - start");

        _consumer = consumer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void produce() throws DataSetException {
        logger.debug("produce() - start");

        File dir;
        try {
            dir = Paths.get(_theDirectory).toFile();
        } catch (final InvalidPathException e) {
            throw new DataSetException("'" + _theDirectory + "' should be a directory", e);
        }

        if (!dir.isDirectory()) {
            throw new DataSetException("'" + _theDirectory + "' should be a directory");
        }

        _consumer.startDataSet();
        try {
        	List tableSpecs = CsvProducer.getTables(dir.toURI().toURL(), CsvDataSet.TABLE_ORDERING_FILE);
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
            // Drop the reference once consumed: readData otherwise holds every row
            // in memory for as long as the CachedDataSet being built from it, even
            // though each row is redundant the moment _consumer.row() returns it.
            readData.set(0, null);

            String fileName = theDataFile.getName();
            String tableName = fileName.substring(0, fileName.lastIndexOf(".csv"));
            ITableMetaData metaData = new DefaultTableMetaData(tableName, columns);
            _consumer.startTable(metaData);
            for (int i = 1 ; i < readData.size(); i++) {
                List rowList = (List)readData.get(i);
                Object[] row = rowList.toArray();
                for(int col = 0; col < row.length; col++) {
                    row[col] = CsvDataSetWriter.NULL.equals(row[col]) ? null : row[col];
                }
                _consumer.row(row);
                readData.set(i, null);
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
	 * @param base the base URL the table list file is resolved against.
	 * @param tableList the name of the file, relative to base, listing the tables in load order.
	 * @return a list of Strings, where each item is a CSV file relative to the base URL
	 * @throws IOException when IO on the base URL has issues.
	 */
	public static List getTables(URL base, String tableList) throws IOException {
        logger.debug("getTables(base={}, tableList={}) - start", base, tableList);

		List orderedNames = new ArrayList();
		InputStream tableListStream = resolveRelative(base, tableList).openStream();
		BufferedReader reader = null;
		try {
    		reader = new BufferedReader(new InputStreamReader(tableListStream, StandardCharsets.UTF_8));
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

	/**
	 * Resolves a relative spec against a base URL without the deprecated
	 * {@code URL(URL, String)} constructor.
	 *
	 * <p>{@link URI#resolve(String)} handles this correctly for a hierarchical
	 * base (e.g. a plain {@code file:}/{@code http:} URL, whether it names a
	 * directory or a sibling file), but for an opaque base such as a
	 * {@code jar:...!/} URL it silently ignores the base and returns the spec
	 * as-is. For an opaque base, the spec is instead appended directly to the
	 * scheme-specific part, matching how the {@code jar:} protocol handler
	 * itself combines a root jar URL with an entry path.
	 *
	 * @param base the base URL.
	 * @param spec the relative spec to resolve against it.
	 * @return the resolved URL.
	 * @throws IOException if the base or the resolved URL is malformed.
	 */
	static URL resolveRelative(final URL base, final String spec) throws IOException {
		try {
			final URI baseUri = base.toURI();
			final URI resolved = baseUri.isOpaque()
					? new URI(baseUri.getScheme(),
							baseUri.getSchemeSpecificPart() + spec,
							baseUri.getFragment())
					: baseUri.resolve(spec);
			return resolved.toURL();
		} catch (final URISyntaxException e) {
			throw new IOException(e);
		}
	}

}
