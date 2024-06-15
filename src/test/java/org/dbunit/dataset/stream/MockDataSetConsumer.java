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
package org.dbunit.dataset.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dbunit.database.statement.Verifiable;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;

/**
 * @author Manuel Laflamme
 * @since Apr 29, 2003
 * @version $Revision$
 */
public class MockDataSetConsumer implements Verifiable, IDataSetConsumer
{
    private static final ProducerEvent START_DATASET_EVENT =
            new ProducerEvent("startDataSet()");
    private static final ProducerEvent END_DATASET_EVENT =
            new ProducerEvent("endDataSet()");

    private final List<ProducerEvent> _expectedList = new ArrayList<>();
    private final List<ProducerEvent> _actualList = new ArrayList<>();
    private String _actualTableName;

    public void addExpectedStartDataSet() throws Exception
    {
        _expectedList.add(START_DATASET_EVENT);
    }

    public void addExpectedEndDataSet() throws Exception
    {
        _expectedList.add(END_DATASET_EVENT);
    }

    public void addExpectedStartTable(final ITableMetaData metaData)
            throws Exception
    {
        _expectedList.add(new StartTableEvent(metaData, false));
    }

    public void addExpectedStartTable(final String tableName,
            final Column[] columns) throws Exception
    {
        addExpectedStartTable(new DefaultTableMetaData(tableName, columns));
    }

    public void addExpectedStartTableIgnoreColumns(final String tableName)
            throws Exception
    {
        _expectedList.add(new StartTableEvent(tableName, true));
    }

    public void addExpectedEmptyTable(final String tableName,
            final Column[] columns) throws Exception
    {
        addExpectedStartTable(tableName, columns);
        addExpectedEndTable(tableName);
    }

    public void addExpectedEmptyTableIgnoreColumns(final String tableName)
            throws Exception
    {
        addExpectedStartTableIgnoreColumns(tableName);
        addExpectedEndTable(tableName);
    }

    public void addExpectedEndTable(final String tableName) throws Exception
    {
        _expectedList.add(new EndTableEvent(tableName));
    }

    public void addExpectedRow(final String tableName, final Object[] values)
            throws Exception
    {
        _expectedList.add(new RowEvent(tableName, values));
    }

    ////////////////////////////////////////////////////////////////////////////
    // Verifiable interface

    @Override
    public void verify()
    {
        assertThat(_actualList).isEqualTo(_expectedList);
        // _expectedList.verify();
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSetConsumer interface

    @Override
    public void startDataSet() throws DataSetException
    {
        _actualList.add(START_DATASET_EVENT);
    }

    @Override
    public void endDataSet() throws DataSetException
    {
        _actualList.add(END_DATASET_EVENT);
    }

    @Override
    public void startTable(final ITableMetaData metaData)
            throws DataSetException
    {
        _actualList.add(new StartTableEvent(metaData, false));
        _actualTableName = metaData.getTableName();
    }

    @Override
    public void endTable() throws DataSetException
    {
        _actualList.add(new EndTableEvent(_actualTableName));
        _actualTableName = null;
    }

    @Override
    public void row(final Object[] values) throws DataSetException
    {
        _actualList.add(new RowEvent(_actualTableName, values));
    }

    ////////////////////////////////////////////////////////////////////////////
    //

    private static class ProducerEvent
    {
        protected final String _name;

        public ProducerEvent(final String name)
        {
            _name = name;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
                return true;
            if (!(o instanceof ProducerEvent))
                return false;

            final ProducerEvent item = (ProducerEvent) o;

            if (!_name.equals(item._name))
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            return _name.hashCode();
        }

        @Override
        public String toString()
        {
            return _name;
        }
    }

    private static class StartTableEvent extends ProducerEvent
    {
        private final String _tableName;
        private final Column[] _columns;
        private final boolean _ignoreColumns;

        public StartTableEvent(final ITableMetaData metaData,
                final boolean ignoreColumns) throws DataSetException
        {
            super("startTable()");
            _tableName = metaData.getTableName();
            _columns = metaData.getColumns();
            _ignoreColumns = ignoreColumns;
        }

        public StartTableEvent(final String tableName,
                final boolean ignoreColumns) throws DataSetException
        {
            super("startTable()");
            _tableName = tableName;
            _columns = new Column[0];
            _ignoreColumns = ignoreColumns;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
                return true;
            if (!(o instanceof StartTableEvent))
                return false;
            if (!super.equals(o))
                return false;

            final StartTableEvent startTableItem = (StartTableEvent) o;

            if (!_tableName.equals(startTableItem._tableName))
                return false;
            if (!_ignoreColumns)
            {
                if (!Arrays.equals(_columns, startTableItem._columns))
                    return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();
            result = 29 * result + _tableName.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            String string = _name + ": table=" + _tableName;
            if (!_ignoreColumns)
            {
                string += ", columns=" + Arrays.asList(_columns);
            }
            return string;
        }
    }

    private static class EndTableEvent extends ProducerEvent
    {
        private final String _tableName;

        public EndTableEvent(final String tableName)
        {
            super("endTable()");
            _tableName = tableName;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
                return true;
            if (!(o instanceof EndTableEvent))
                return false;
            if (!super.equals(o))
                return false;

            final EndTableEvent endTableItem = (EndTableEvent) o;

            if (!_tableName.equals(endTableItem._tableName))
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();
            result = 29 * result + _tableName.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return _name + ": table=" + _tableName;
        }
    }

    private static class RowEvent extends ProducerEvent
    {
        private final String _tableName;
        private final Object[] _values;

        public RowEvent(final String tableName, final Object[] values)
        {
            super("row()");
            _tableName = tableName;
            _values = values;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
                return true;
            if (!(o instanceof RowEvent))
                return false;
            if (!super.equals(o))
                return false;

            final RowEvent rowItem = (RowEvent) o;

            if (!_tableName.equals(rowItem._tableName))
                return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            if (!Arrays.equals(_values, rowItem._values))
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();
            result = 29 * result + _tableName.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return _name + ": table=" + _tableName + ", values="
                    + Arrays.asList(_values);
        }

    }
}
