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

package org.dbunit.dataset.yaml;

import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */

public class YamlProducer implements IDataSetProducer
{

    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();

    /**
     * The consumer which is responsible for creating the datasets and tables
     */
    private IDataSetConsumer _consumer = EMPTY_CONSUMER;

    private InputStream _inputStream;

    private Yaml _yaml;

    public YamlProducer(File file) throws IOException
    {
        this(Files.newInputStream(file.toPath()));
    }

    public YamlProducer(InputStream inputStream)
    {
        this._inputStream = inputStream;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        _yaml = new Yaml(options);
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSetProducer interface

    public void setConsumer(IDataSetConsumer consumer)
    {
        _consumer = consumer;
    }

    public void produce() throws DataSetException
    {
        _consumer.startDataSet();
        LinkedHashMap<String, Object> dataset;
        // get the base object tree from the stream
        try
        {
            dataset = (LinkedHashMap<String, Object>) _yaml.load(_inputStream);
        }
        catch (DuplicateKeyException e)
        {
            String problem = e.getProblem();
            String duplicateTable = problem.replace("found duplicate key ", "");
            throw new AmbiguousTableNameException(duplicateTable, e);
        }
        // iterate over the tables in the object tree
        for (String tableName : dataset.keySet())
        {
            // get the rows for the table
            List<Map<String, Object>> rows = (List<Map<String, Object>>) dataset.get(tableName);
            ITableMetaData meta = getMetaData(tableName, rows);
            _consumer.startTable(meta);
            if (rows != null)
            {
                for (Map<String, Object> row : rows)
                {
                    _consumer.row(getRow(meta, row));
                }
            }
            _consumer.endTable();
        }
    }


    private ITableMetaData getMetaData(String tableName, List<Map<String, Object>> rows)
    {
        Set<String> columns = new LinkedHashSet<String>();
        if (rows != null)
        {
            // iterate through the dataset and add the column names to a set
            for (Map<String, Object> row : rows)
            {
                for (Map.Entry<String, Object> column : row.entrySet())
                {
                    columns.add(column.getKey());
                }
            }
            List<Column> list = new ArrayList<Column>(columns.size());
            // create a list of DBUnit columns based on the column name set
            for (String s : columns)
            {
                list.add(new Column(s, DataType.UNKNOWN));
            }
            return new DefaultTableMetaData(tableName, list.toArray(new Column[list.size()]));
        } else
        {
            return new DefaultTableMetaData(tableName, new Column[0]);
        }
    }

    private Object[] getRow(ITableMetaData meta, Map<String, Object> row) throws DataSetException
    {
        Object[] result = new Object[meta.getColumns().length];
        for (int i = 0; i < meta.getColumns().length; i++)
        {
            result[i] = row.get(meta.getColumns()[i].getColumnName());
        }
        return result;
    }

}
