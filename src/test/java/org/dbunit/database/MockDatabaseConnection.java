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

package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import org.dbunit.database.statement.IStatementFactory;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
public class MockDatabaseConnection implements IDatabaseConnection
{
    private Integer _closeCalls = 0;
    private Integer _expectedCloseCalls = 0;

    private Connection _connection;
    private String _schema;
    private IDataSet _dataSet;
    // private IStatementFactory _statementFactory;
    private DatabaseConfig _databaseConfig = new DatabaseConfig();

    public void setupSchema(final String schema)
    {
        _schema = schema;
    }

    public void setupConnection(final Connection connection)
    {
        _connection = connection;
    }

    public void setupDataSet(final IDataSet dataSet)
    {
        _dataSet = dataSet;
    }

    public void setupDataSet(final ITable table)
            throws AmbiguousTableNameException
    {
        _dataSet = new DefaultDataSet(table);
    }

    public void setupDataSet(final ITable[] tables)
            throws AmbiguousTableNameException
    {
        _dataSet = new DefaultDataSet(tables);
    }

    public void setupStatementFactory(final IStatementFactory statementFactory)
    {
        _databaseConfig.setProperty(DatabaseConfig.PROPERTY_STATEMENT_FACTORY,
                statementFactory);
    }

    // public void setupEscapePattern(String escapePattern)
    // {
    // _databaseConfig.setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN,
    // escapePattern);
    // }
    //
    public void setExpectedCloseCalls(final int callsCount)
    {
        _expectedCloseCalls = callsCount;
    }

    public void verify()
    {
        if (!Objects.isNull(_expectedCloseCalls))
        {
            assertThat(_closeCalls).isEqualTo(_expectedCloseCalls);
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // IDatabaseConnection interface

    @Override
    public Connection getConnection() throws SQLException
    {
        return _connection;
    }

    @Override
    public String getSchema()
    {
        return _schema;
    }

    @Override
    public void close() throws SQLException
    {
        _closeCalls++;
    }

    @Override
    public IDataSet createDataSet() throws SQLException
    {
        return _dataSet;
    }

    @Override
    public IDataSet createDataSet(final String[] tableNames)
            throws SQLException, AmbiguousTableNameException
    {
        return new FilteredDataSet(tableNames, createDataSet());
    }

    @Override
    public ITable createQueryTable(final String resultName, final String sql)
            throws DataSetException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ITable createTable(final String tableName,
            final PreparedStatement preparedStatement)
            throws DataSetException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ITable createTable(final String tableName)
            throws DataSetException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getRowCount(final String tableName) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getRowCount(final String tableName, final String whereClause)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public IStatementFactory getStatementFactory()
    {
        return (IStatementFactory) _databaseConfig
                .getProperty(DatabaseConfig.PROPERTY_STATEMENT_FACTORY);
    }

    @Override
    public DatabaseConfig getConfig()
    {
        return _databaseConfig;
    }
}
