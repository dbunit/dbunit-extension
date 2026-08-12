/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package org.dbunit.database;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.util.SQLHelper;

/**
 * An in-memory {@link ResultSet}, backed by rows copied out of one or more source result sets
 * ahead of time. It supports only the handful of {@link ResultSet}/{@link ResultSetMetaData}
 * methods dbunit itself calls against an {@link IMetadataHandler} result - {@code next()},
 * {@code getString(int/String)}, {@code getInt(int/String)}, {@code getMetaData()}/
 * {@code getColumnCount()}, {@code close()}, plus {@code equals()}/{@code hashCode()}/
 * {@code toString()} for safe use as a log argument or map key - since implementing the rest of
 * {@link ResultSet}'s ~150 methods would serve no caller. Any other method throws
 * {@link UnsupportedOperationException}.
 * <p>
 * A single instance answers both {@link ResultSet} calls and, since {@code getMetaData()}
 * returns the proxy itself, the {@link ResultSetMetaData} calls made against its result.
 *
 * @since 3.5.0
 */
public final class InMemoryMetadataResultSet implements InvocationHandler
{
    /**
     * Tests whether a source result set's current row, as positioned by a preceding
     * {@link ResultSet#next()}, should be kept.
     *
     * @since 3.5.0
     */
    @FunctionalInterface
    public interface RowFilter
    {
        /**
         * Tests the row the given result set is currently positioned on.
         *
         * @param resultSet The source result set, positioned on the row to test.
         * @return {@code true} if the row should be kept.
         * @throws SQLException if a database access error occurs.
         */
        boolean accept(ResultSet resultSet) throws SQLException;
    }

    private final List<Object[]> rows;
    private final int columnCount;
    private final Map<String, Integer> columnIndexByLabel;
    private int cursor = -1;

    private InMemoryMetadataResultSet(final List<Object[]> rows, final int columnCount,
            final Map<String, Integer> columnIndexByLabel)
    {
        this.rows = rows;
        this.columnCount = columnCount;
        this.columnIndexByLabel = columnIndexByLabel;
    }

    /**
     * Copies every row of each given result set, closing each as it is consumed, and returns a
     * single merged {@link ResultSet} positioned before the first row.
     *
     * @param sources The result sets to merge, in the order their rows should appear.
     * @return The merged result set.
     * @throws SQLException if a source result set cannot be read.
     */
    public static ResultSet merge(final List<ResultSet> sources) throws SQLException
    {
        return copy(sources, null);
    }

    /**
     * Copies the rows of the given result set that match the given filter, closing it as it is
     * consumed, and returns a filtered {@link ResultSet} positioned before the first row.
     *
     * @param source The result set to filter.
     * @param filter The filter a row must match to be kept.
     * @return The filtered result set.
     * @throws SQLException if the source result set cannot be read.
     */
    public static ResultSet filter(final ResultSet source, final RowFilter filter)
            throws SQLException
    {
        return copy(Collections.singletonList(source), filter);
    }

    private static ResultSet copy(final List<ResultSet> sources, final RowFilter filter)
            throws SQLException
    {
        final List<Object[]> rows = new ArrayList<Object[]>();
        final Map<String, Integer> columnIndexByLabel = new HashMap<String, Integer>();
        int columnCount = 0;
        boolean first = true;
        try
        {
            for (final ResultSet source : sources)
            {
                if (first)
                {
                    final ResultSetMetaData metaData = source.getMetaData();
                    columnCount = metaData.getColumnCount();
                    for (int i = 1; i <= columnCount; i++)
                    {
                        columnIndexByLabel.put(
                                metaData.getColumnLabel(i).toUpperCase(Locale.ENGLISH), i);
                    }
                    first = false;
                }
                while (source.next())
                {
                    if (filter != null && !filter.accept(source))
                    {
                        continue;
                    }
                    final Object[] row = new Object[columnCount];
                    for (int i = 1; i <= columnCount; i++)
                    {
                        row[i - 1] = source.getObject(i);
                    }
                    rows.add(row);
                }
            }
        }
        finally
        {
            closeAll(sources);
        }

        final InMemoryMetadataResultSet handler =
                new InMemoryMetadataResultSet(rows, columnCount, columnIndexByLabel);
        return (ResultSet) Proxy.newProxyInstance(InMemoryMetadataResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class, ResultSetMetaData.class}, handler);
    }

    /**
     * Closes every result set in the given list, null- and already-closed-safe. Closing one
     * result set is attempted even if closing an earlier one in the list failed, so a single
     * failure does not leak the rest.
     *
     * @param resultSets The result sets to close.
     * @throws SQLException the first failure encountered while closing, if any.
     */
    private static void closeAll(final List<ResultSet> resultSets) throws SQLException
    {
        SQLException firstFailure = null;
        for (final ResultSet resultSet : resultSets)
        {
            try
            {
                SQLHelper.close(resultSet);
            }
            catch (final SQLException e)
            {
                if (firstFailure == null)
                {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null)
        {
            throw firstFailure;
        }
    }

    /**
     * {@inheritDoc}
     * Answers exactly the methods documented on this class; any other method throws
     * {@link UnsupportedOperationException}.
     */
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
    {
        final String name = method.getName();
        if ("next".equals(name))
        {
            cursor++;
            return cursor < rows.size();
        }
        if ("getString".equals(name))
        {
            final Object value = currentValue(args[0]);
            return value == null ? null : String.valueOf(value);
        }
        if ("getInt".equals(name))
        {
            final Object value = currentValue(args[0]);
            return value == null ? 0 : toInt(value);
        }
        if ("getMetaData".equals(name))
        {
            return proxy;
        }
        if ("getColumnCount".equals(name))
        {
            return columnCount;
        }
        if ("close".equals(name))
        {
            return null;
        }
        if ("toString".equals(name))
        {
            return "InMemoryMetadataResultSet[rows=" + rows.size() + "]";
        }
        if ("hashCode".equals(name))
        {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name))
        {
            return proxy == args[0];
        }
        throw new UnsupportedOperationException(
                "InMemoryMetadataResultSet does not support " + name + "()");
    }

    private Object currentValue(final Object columnArg)
    {
        if (cursor < 0 || cursor >= rows.size())
        {
            throw new IllegalStateException("ResultSet is not positioned on a valid row.");
        }
        return rows.get(cursor)[columnIndex(columnArg) - 1];
    }

    private int columnIndex(final Object columnArg)
    {
        if (columnArg instanceof Integer)
        {
            return (Integer) columnArg;
        }
        final String label = String.valueOf(columnArg).toUpperCase(Locale.ENGLISH);
        final Integer index = columnIndexByLabel.get(label);
        if (index == null)
        {
            throw new IllegalArgumentException("Unknown column '" + columnArg + "'.");
        }
        return index;
    }

    private static int toInt(final Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
