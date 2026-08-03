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
package org.dbunit.dataset.xml;

import java.io.PrintWriter;
import java.io.Writer;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a flat DTD document describing a dataset's tables and columns.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Jun 13, 2003
 */
public class FlatDtdWriter //implements IDataSetConsumer
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(FlatDtdWriter.class);

    public static final ContentModel SEQUENCE = new SequenceModel();
    public static final ContentModel CHOICE   = new ChoiceModel();

    private Writer _writer;
    private ContentModel _contentModel;

    public FlatDtdWriter(Writer writer)
    {
        _writer = writer;
        _contentModel = SEQUENCE;
    }

    public void setContentModel(ContentModel contentModel)
    {
        logger.debug("setContentModel(contentModel={}) - start", contentModel);
        _contentModel = contentModel;
    }

    public void write(IDataSet dataSet) throws DataSetException
    {
        logger.debug("write(dataSet={}) - start", dataSet);

        PrintWriter printOut = new PrintWriter(_writer);
        String[] tableNames = dataSet.getTableNames();

        // dataset element
        if (tableNames.length > 0)
        {
            printOut.print("<!ELEMENT dataset (\n");
            for (int i = 0; i < tableNames.length; i++)
            {
                _contentModel.write(printOut, tableNames[i], i, tableNames.length);
            }
            printOut.print(")>\n");
        }
        else
        {
            // An empty parenthesized content model, e.g. "(\n)", is not
            // valid DTD syntax. ANY tolerates the whitespace that a
            // pretty-printed empty "<dataset></dataset>" typically contains,
            // unlike EMPTY which rejects any content at all.
            printOut.print("<!ELEMENT dataset ANY>\n");
        }
        printOut.print("\n");

        // tables
        for (int i = 0; i < tableNames.length; i++)
        {
            // table element
            String tableName = tableNames[i];
            printOut.print("<!ELEMENT ");
            printOut.print(tableName);
            printOut.print(" EMPTY>\n");

            // column attributes
            printOut.print("<!ATTLIST ");
            printOut.print(tableName);
            printOut.print("\n");
            // Add the columns
            Column[] columns = dataSet.getTableMetaData(tableName).getColumns();
            for (int j = 0; j < columns.length; j++)
            {
                Column column = columns[j];
                printOut.print("    ");
                printOut.print(column.getColumnName());
                if (column.getNullable() == Column.NO_NULLS  && column.getDefaultValue() == null)
                {
                    printOut.print(" CDATA " + FlatDtdProducer.REQUIRED + "\n");
                }
                else
                {
                    printOut.print(" CDATA " + FlatDtdProducer.IMPLIED + "\n");
                }
            }
            printOut.print(">\n");
            printOut.print("\n");
        }

        printOut.flush();
    }

    /**
     * Abstract strategy for rendering a DTD element's content model (the part
     * between the parentheses in an &lt;!ELEMENT&gt; declaration).
     *
     * @author Manuel Laflamme
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since Jun 13, 2003
     */
    public static abstract class ContentModel
    {
        private final String _name;

        private ContentModel(String name)
        {
            _name = name;
        }

        public String toString()
        {
            return _name;
        }

        public abstract void write(PrintWriter writer, String tableName,
                int tableIndex, int tableCount);
    }

    
    /**
     * {@link ContentModel} that renders table names as a comma-separated,
     * ordered sequence.
     *
     * @author Manuel Laflamme
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since Jun 13, 2003
     */
    public static class SequenceModel extends ContentModel
    {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(SequenceModel.class);

        private SequenceModel()
        {
            super("sequence");
        }

        public void write(PrintWriter writer, String tableName, int tableIndex, int tableCount)
        {
        	if (logger.isDebugEnabled())
        	{
        		logger.debug("write(writer={}, tableName={}, tableIndex={}, tableCount={}) - start",
        				new Object[]{ writer, tableName, String.valueOf(tableIndex), String.valueOf(tableCount)});
        	}

            boolean last = (tableIndex + 1) == tableCount;

            writer.print("    ");
            writer.print(tableName);
            writer.print("*");
            if (!last)
            {
                writer.print(",\n");
            }
        }
    }

    /**
     * {@link ContentModel} that renders table names as a pipe-separated choice group.
     *
     * @author Manuel Laflamme
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since Jun 13, 2003
     */
    public static class ChoiceModel extends ContentModel
    {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(ChoiceModel.class);

        private ChoiceModel()
        {
            super("sequence");
        }

        public void write(PrintWriter writer, String tableName, int tableIndex, int tableCount)
        {
        	if (logger.isDebugEnabled())
        	{
        		logger.debug("write(writer={}, tableName={}, tableIndex={}, tableCount={}) - start",
        				new Object[]{ writer, tableName, String.valueOf(tableIndex), String.valueOf(tableCount)});
        	}

            boolean first = tableIndex == 0;
            boolean last = (tableIndex + 1) == tableCount;

            if (first)
            {
                writer.print("   (");
            }
            else
            {
                writer.print("    ");
            }
            writer.print(tableName);

            if (!last)
            {
                writer.print("|\n");
            }
            else
            {
                writer.print(")*");
            }
        }
    }
}
