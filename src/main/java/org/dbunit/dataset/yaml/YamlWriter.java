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

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.RequiredLibrary;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Writes an {@link IDataSet} to a {@link Writer} in YAML format.
 *
 * <p>The writer leaves a null cell, or a cell with no value, out of the mapping of a row,
 * except in the first row of a table. That row carries every column of the table, with
 * {@code null} for such a cell, so a column that is null in every row still appears in
 * the output and survives a read-back.
 *
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */
class YamlWriter
{

    private Yaml _yaml;
    private Writer _out;
    private boolean _useFlowStyle;

    public YamlWriter(Writer out)
    {
        this(out, false);
    }

    public YamlWriter(Writer out, boolean useFlowStyle)
    {
        RequiredLibrary.SNAKEYAML.requireUnchecked(YamlWriter.class);
        this._out = out;
        this._useFlowStyle = useFlowStyle;
    }

    public void setUseFlowStyle(boolean useFlowStyle)
    {
        this._useFlowStyle = useFlowStyle;
    }

    public void write(IDataSet dataSet) throws DataSetException
    {
        LinkedHashMap<String, List<LinkedHashMap<String, Object>>> dataSetAsMap = dataSetAsMap(dataSet);
        DumperOptions options = new DumperOptions();
        if (_useFlowStyle)
        {
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
            options.setPrettyFlow(true);
        } else
        {
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(4);
            options.setIndicatorIndent(2);
        }
        _yaml = new Yaml(options);
        _yaml.dump(dataSetAsMap, _out);
    }

    private LinkedHashMap<String, List<LinkedHashMap<String, Object>>> dataSetAsMap(IDataSet dataSet) throws DataSetException
    {
        LinkedHashMap<String, List<LinkedHashMap<String, Object>>> result = new LinkedHashMap<>();
        ITableIterator iterator = dataSet.iterator();
        while (iterator.next())
        {
            ITableMetaData tableMetaData = iterator.getTableMetaData();
            List<LinkedHashMap<String, Object>> tableRows = new ArrayList<>();
            ITable table = iterator.getTable();
            for (int row = 0; row < table.getRowCount(); row++)
            {
                boolean firstRow = row == 0;
                LinkedHashMap<String, Object> rowMap = new LinkedHashMap<>();
                for (Column column : tableMetaData.getColumns())
                {
                    String columnName = column.getColumnName();
                    Object value = table.getValue(row, columnName);
                    boolean noValue = value == null || value == ITable.NO_VALUE;
                    if (noValue)
                    {
                        // The first row carries every column, so that a column that is null
                        // in every row still appears in the document and survives a read-back.
                        if (firstRow)
                        {
                            rowMap.put(columnName, null);
                        }
                    } else
                    {
                        rowMap.put(columnName, valueToWrite(value));
                    }
                }
                tableRows.add(rowMap);
            }
            result.put(tableMetaData.getTableName(), tableRows);
        }
        return result;
    }

    /**
     * Returns a date, a time or a timestamp as the text a column type parses, the JDBC escape
     * format of {@link java.sql.Timestamp#toString()} and its siblings: a {@code java.sql} or
     * {@code java.time} date, time or date and time as it is, a {@code java.time} time to the
     * second, and an instant, such as a {@link java.util.Date} or an
     * {@link java.time.OffsetDateTime}, in the time zone of the JVM. A date then reads back as
     * the same day, and a timestamp as the same date and time, in any time zone, as from a flat
     * XML file; SnakeYAML would write either as an instant in UTC. Returns any other
     * {@code java.time} value as its {@code toString()}, and any other value as it is.
     *
     * @param value The value of a cell; not {@code null}.
     * @return The value to write.
     */
    private static Object valueToWrite(Object value)
    {
        if (value instanceof java.sql.Date || value instanceof java.sql.Time
                || value instanceof java.sql.Timestamp)
        {
            return value.toString();
        }
        if (value instanceof java.util.Date)
        {
            long millis = ((java.util.Date) value).getTime();
            return new java.sql.Timestamp(millis).toString();
        }
        if (value instanceof LocalDate)
        {
            return java.sql.Date.valueOf((LocalDate) value).toString();
        }
        if (value instanceof LocalDateTime)
        {
            return java.sql.Timestamp.valueOf((LocalDateTime) value).toString();
        }
        if (value instanceof LocalTime)
        {
            return java.sql.Time.valueOf((LocalTime) value).toString();
        }
        if (value instanceof Instant || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime)
        {
            Instant instant = Instant.from((TemporalAccessor) value);
            return java.sql.Timestamp.from(instant).toString();
        }
        if (value instanceof TemporalAccessor)
        {
            return value.toString();
        }
        return value;
    }
}
