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

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.DatabaseSequenceFilter;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.ext.mssql.InsertIdentityOperation;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.TransactionOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The <code>Operation</code> class is the step that defines which
 * operation will be performed in the execution of the <code>DbUnitTask</code>
 * task.
 *
 * @author Timothy Ruppert
 * @author Ben Cox
 * @version $Revision$
 * @since Jun 10, 2002
 */
public class Operation extends AbstractStep
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Operation.class);

    private static final String DEFAULT_FORMAT = FORMAT_FLAT;

    /** The name of the {@link DatabaseOperation} to execute, e.g. <code>"CLEAN_INSERT"</code>. */
    protected String _type = "CLEAN_INSERT";
    private String _format;
    private List<File> _sources = new ArrayList<>();
    private boolean _combine = false;
    private boolean _transaction = false;
    private DatabaseOperation _operation;
    private boolean _forwardOperation = true;
    private String _nullToken;

    /**
     * Returns the source files holding the operation's dataset.
     *
     * @return the source files holding the operation's dataset.
     */
    public File[] getSrc()
    {
        return _sources.toArray(new File[_sources.size()]);
    }

    /**
     * Sets the source files holding the operation's dataset.
     *
     * @param sources the source files holding the operation's dataset.
     */
    public void setSrc(File[] sources)
    {
        _sources.clear();
        _sources.addAll(Arrays.asList(sources));
    }

    /**
     * Sets the single source file holding the operation's dataset.
     *
     * @param src the source file holding the operation's dataset.
     */
    public void setSrc(File src)
    {
        _sources.clear();
        _sources.add(src);
    }

    /**
     * Adds the files matched by the given Ant fileset to the operation's dataset sources.
     *
     * @param fileSet the Ant fileset to add.
     */
    public void addConfiguredFileset(FileSet fileSet)
    {
        DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
        for (String file : scanner.getIncludedFiles()) {
            _sources.add(scanner.getBasedir().toPath().resolve(file).toFile());
        }
    }

    /**
     * Returns the dataset format, defaulting to {@value #DEFAULT_FORMAT} when not set.
     *
     * @return the dataset format.
     */
    public String getFormat()
    {
        return _format != null ? _format : DEFAULT_FORMAT;
    }

    /**
     * Sets the dataset format.
     *
     * @param format the dataset format.
     */
    public void setFormat(String format)
    {
        logger.debug("setFormat(format={}) - start", format);

        // Check if the given format is accepted
        checkDataFormat(format);
        // If we get here the given format is a valid data format
        _format = format;
    }

    /**
     * Returns whether multiple sources are combined into a single dataset.
     *
     * @return {@code true} if multiple sources are combined into a single dataset.
     */
    public boolean isCombine()
    {
        return _combine;
    }

    /**
     * Sets whether multiple sources are combined into a single dataset.
     *
     * @param combine {@code true} to combine multiple sources into a single dataset.
     */
    public void setCombine(boolean combine)
    {
        _combine = combine;
    }

    /**
     * Returns whether the operation runs within a transaction.
     *
     * @return {@code true} if the operation runs within a transaction.
     */
    public boolean isTransaction()
    {
        return _transaction;
    }

    /**
     * Sets whether the operation runs within a transaction.
     *
     * @param transaction {@code true} to run the operation within a transaction.
     */
    public void setTransaction(boolean transaction)
    {
        _transaction = transaction;
    }

    /**
     * Returns the token replaced with a null value in the dataset.
     *
     * @return the token replaced with a null value in the dataset.
     */
    public String getNullToken()
    {
        return _nullToken;
    }

    /**
     * Sets the token to replace with a null value in the dataset.
     *
     * @param nullToken the token to replace with a null value in the dataset.
     */
    public void setNullToken(final String nullToken)
    {
        this._nullToken = nullToken;
    }

    /**
     * Returns the database operation resolved from {@link #setType(String)}.
     *
     * @return the database operation resolved from {@link #setType(String)}.
     */
    public DatabaseOperation getDbOperation()
    {
        return _operation;
    }

    /**
     * Returns the operation type name.
     *
     * @return the operation type name.
     */
    public String getType()
    {
        return _type;
    }

    /**
     * Sets the operation type, resolving it to the corresponding {@link DatabaseOperation}.
     *
     * @param type one of: UPDATE, INSERT, REFRESH, DELETE, DELETE_ALL, CLEAN_INSERT, NONE,
     *            MSSQL_CLEAN_INSERT, MSSQL_INSERT, or MSSQL_REFRESH.
     */
    public void setType(String type)
    {
        logger.debug("setType(type={}) - start", type);

        if ("UPDATE".equals(type)) {
            _operation = DatabaseOperation.UPDATE;
            _forwardOperation = true;
        } else if ("INSERT".equals(type)) {
            _operation = DatabaseOperation.INSERT;
            _forwardOperation = true;
        } else if ("REFRESH".equals(type)) {
            _operation = DatabaseOperation.REFRESH;
            _forwardOperation = true;
        } else if ("DELETE".equals(type)) {
            _operation = DatabaseOperation.DELETE;
            _forwardOperation = false;
        } else if ("DELETE_ALL".equals(type)) {
            _operation = DatabaseOperation.DELETE_ALL;
            _forwardOperation = false;
        } else if ("CLEAN_INSERT".equals(type)) {
            _operation = DatabaseOperation.CLEAN_INSERT;
            _forwardOperation = false;
        } else if ("NONE".equals(type)) {
            _operation = DatabaseOperation.NONE;
            _forwardOperation = true;
        } else if ("MSSQL_CLEAN_INSERT".equals(type)) {
            _operation = InsertIdentityOperation.CLEAN_INSERT;
            _forwardOperation = false;
        } else if ("MSSQL_INSERT".equals(type)) {
            _operation = InsertIdentityOperation.INSERT;
            _forwardOperation = true;
        } else if ("MSSQL_REFRESH".equals(type)) {
            _operation = InsertIdentityOperation.REFRESH;
            _forwardOperation = true;
        } else {
            throw new IllegalArgumentException("Type must be one of: UPDATE, INSERT,"
                    + " REFRESH, DELETE, DELETE_ALL, CLEAN_INSERT, MSSQL_INSERT, "
                    + " or MSSQL_REFRESH but was: " + type);
        }
        _type = type;
    }

    public void execute(IDatabaseConnection connection) throws DatabaseUnitException
    {
        logger.debug("execute(connection={}) - start", connection);
        if (_operation == null)
        {
            throw new DatabaseUnitException("Operation.execute(): setType(String) must be called before execute()!");
        }

        if (_operation == DatabaseOperation.NONE)
        {
            return;
        }

        if (_sources.size() == 0)
        {
            throw new DatabaseUnitException("Operation.execute(): must call setSrc(File), addSrc(File), or setSources(File[]) before execute()!");
        }

        try {
            DatabaseOperation operation = (_transaction ? new TransactionOperation(_operation) : _operation);
            // TODO This is not very nice and the design should be reviewed but it works for now (gommma)
            boolean useForwardOnly = _forwardOperation && ! isOrdered();
            IDataSet dataset;
            if (_sources.size() > 1) {
                IDataSet[] datasets = new IDataSet[_sources.size()];
                for (int i = 0; i < _sources.size(); i++) {
                    datasets[i] = getSrcDataSet(_sources.get(i), getFormat(), useForwardOnly);
                }
                dataset = new CompositeDataSet(datasets, _combine);
            } else {
                dataset = getSrcDataSet(_sources.get(0), getFormat(), useForwardOnly);
            }
            if (_nullToken != null) {
                dataset = new ReplacementDataSet(dataset);
                ((ReplacementDataSet)dataset).addReplacementObject(_nullToken, null);
            }
            if(isOrdered()) 
            {
                DatabaseSequenceFilter databaseSequenceFilter = new DatabaseSequenceFilter(connection);
                dataset = new FilteredDataSet(databaseSequenceFilter, dataset);
            }
            operation.execute(connection, dataset);
        }
        catch (SQLException e)
        {
            throw new DatabaseUnitException(e);
        }
    }

    public String getLogMessage()
    {
        final StringBuilder result = new StringBuilder();
        result.append("Executing operation: " + _type);
        result.append("\n          on   files: [ ");
        for (File f : _sources) {
            result.append(f.getAbsolutePath() + " ");
        }
        result.append("]");
        result.append("\n          with format: " + _format);
        return result.toString();
    }

    public String toString()
    {
        final StringBuilder result = new StringBuilder();
        result.append("Operation: ");
        result.append(" type=").append(_type);
        result.append(", format=").append(_format);
        result.append(", sources=[ ");
        for (File f : _sources) {
            result.append(f.getAbsolutePath() + " ");
        }
        result.append("]");
        result.append(", operation=").append(_operation);
        result.append(", nullToken=").append(_nullToken);
        result.append(", ordered=").append(super.isOrdered());
        return result.toString();
    }
}
