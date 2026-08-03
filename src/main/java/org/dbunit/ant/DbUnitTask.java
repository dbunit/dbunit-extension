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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>DbUnitTask</code> is the task definition for an Ant
 * interface to <code>DbUnit</code>.   DbUnit is a JUnit extension
 * which sets your database to a known state before executing your
 * tasks.
 *
 * @author Timothy Ruppert
 * @author Ben Cox
 * @version $Revision$
 * @since Jun 10, 2002
 * @see org.apache.tools.ant.Task
 */
public class DbUnitTask extends Task
{
    private static final Logger logger = LoggerFactory.getLogger(DbUnitTask.class);

    /**
     * Database connection
     */
    private Connection conn = null;

    /**
     * DB driver.
     */
    private String driver = null;

    /**
     * DB url.
     */
    private String url = null;

    /**
     * User name.
     */
    private String userId = null;

    /**
     * Password
     */
    private String password = null;

    /**
     * DB schema.
     */
    private String schema = null;

    /**
     * Steps
     */
    private List steps = new ArrayList();

    private Path classpath;

    private AntClassLoader loader;

    /**
     * DB configuration child element to configure {@link DatabaseConfig} properties
     * in a generic way.
     */
    private DbConfig dbConfig;

    /**
     * Flag for using the qualified table names.
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private Boolean useQualifiedTableNames = null;

    /**
     * Flag for using batched statements.
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private Boolean supportBatchStatement = null;

    /**
     * Flag for datatype warning.
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private Boolean datatypeWarning = null;

    /**
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private String escapePattern = null;

    /**
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private String dataTypeFactory = null;

    /**
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private String batchSize = null;

    /**
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private String fetchSize = null;

    /**
     * @deprecated since 2.4. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private Boolean skipOracleRecycleBinTables = null;

    /**
     * @deprecated since 2.5.1. Use {@link #dbConfig} instead. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private Boolean allowEmptyFields = null;

    /**
     * Set the JDBC driver to be used.
     *
     * @param driver the fully qualified class name of the JDBC driver.
     */
    public void setDriver(final String driver)
    {
        logger.trace("setDriver(driver={}) - start", driver);
        this.driver = driver;
    }

    /**
     * Set the DB connection url.
     *
     * @param url the JDBC connection URL.
     */
    public void setUrl(final String url)
    {
        logger.trace("setUrl(url={}) - start", url);
        this.url = url;
    }

    /**
     * Set the user name for the DB connection.
     *
     * @param userId the database user name.
     */
    public void setUserid(final String userId)
    {
        logger.trace("setUserid(userId={}) - start", userId);
        this.userId = userId;
    }

    /**
     * Set the password for the DB connection.
     *
     * @param password the database password.
     */
    public void setPassword(final String password)
    {
        logger.trace("setPassword(password=*****) - start");
        this.password = password;
    }

    /**
     * Set the schema for the DB connection.
     *
     * @param schema the database schema.
     */
    public void setSchema(final String schema)
    {
        logger.trace("setSchema(schema={}) - start", schema);
        this.schema = schema;
    }

    /**
     * Set the flag for using the qualified table names.
     *
     * @param useQualifiedTableNames <code>true</code> to use qualified table names.
     */
    public void setUseQualifiedTableNames(final Boolean useQualifiedTableNames)
    {
        logger.trace("setUseQualifiedTableNames(useQualifiedTableNames={}) - start", String.valueOf(useQualifiedTableNames));
        this.useQualifiedTableNames = useQualifiedTableNames;
    }

    /**
     * Set the flag for supporting batch statements.
     * NOTE: This property cannot be used to force the usage of batch
     *       statement if your database does not support it.
     *
     * @param supportBatchStatement <code>true</code> to use batch statements.
     */
    public void setSupportBatchStatement(final Boolean supportBatchStatement)
    {
        logger.trace("setSupportBatchStatement(supportBatchStatement={}) - start", String.valueOf(supportBatchStatement));
        this.supportBatchStatement = supportBatchStatement;
    }

    /**
     * Sets the flag for logging a warning on unsupported data types.
     *
     * @param datatypeWarning <code>true</code> to log a warning on unsupported data types.
     */
    public void setDatatypeWarning(final Boolean datatypeWarning)
    {
        logger.trace("setDatatypeWarning(datatypeWarning={}) - start", String.valueOf(datatypeWarning));
        this.datatypeWarning = datatypeWarning;
    }

    /**
     * Sets the fully qualified class name of the {@link IDataTypeFactory} to use.
     *
     * @param datatypeFactory the fully qualified class name of the {@link IDataTypeFactory} to use.
     */
    public void setDatatypeFactory(final String datatypeFactory)
    {
        logger.trace("setDatatypeFactory(datatypeFactory={}) - start", datatypeFactory);
        this.dataTypeFactory = datatypeFactory;
    }

    /**
     * Sets the pattern used to escape table and column names.
     *
     * @param escapePattern the pattern used to escape table and column names.
     */
    public void setEscapePattern(final String escapePattern)
    {
        logger.trace("setEscapePattern(escapePattern={}) - start", escapePattern);
        this.escapePattern = escapePattern;
    }

    /**
     * Returns the generic {@link DatabaseConfig} configuration child element.
     *
     * @return the generic {@link DatabaseConfig} configuration child element.
     */
    public DbConfig getDbConfig()
    {
        return dbConfig;
    }

    //    public void setDbConfig(DbConfig dbConfig)
    //    {
    //        logger.debug("setDbConfig(dbConfig={}) - start", dbConfig);
    //        this.dbConfig = dbConfig;
    //    }

    /**
     * Sets the generic {@link DatabaseConfig} configuration child element.
     *
     * @param dbConfig the generic {@link DatabaseConfig} configuration child element.
     */
    public void addDbConfig(final DbConfig dbConfig)
    {
        logger.trace("addDbConfig(dbConfig={}) - start", dbConfig);
        this.dbConfig = dbConfig;
    }

    /**
     * Set the classpath for loading the driver.
     *
     * @param classpath the classpath for loading the driver.
     */
    public void setClasspath(final Path classpath)
    {
        logger.trace("setClasspath(classpath={}) - start", classpath);
        if (this.classpath == null)
        {
            this.classpath = classpath;
        }
        else
        {
            this.classpath.append(classpath);
        }
    }

    /**
     * Create the classpath for loading the driver.
     *
     * @return the classpath for loading the driver.
     */
    public Path createClasspath()
    {
        logger.trace("createClasspath() - start");

        if (this.classpath == null)
        {
            this.classpath = new Path(getProject());
        }
        return this.classpath.createPath();
    }

    /**
     * Set the classpath for loading the driver using the classpath reference.
     *
     * @param r the reference to the classpath for loading the driver.
     */
    public void setClasspathRef(final Reference r)
    {
        logger.trace("setClasspathRef(r={}) - start", r);

        createClasspath().setRefid(r);
    }

    /**
     * Gets the Steps.
     *
     * @return the steps to execute.
     */
    public List getSteps()
    {
        return steps;
    }

    /**
     * Adds an Operation.
     *
     * @param operation the operation step to add.
     */
    public void addOperation(final Operation operation)
    {
        logger.trace("addOperation({}) - start", operation);

        steps.add(operation);
    }

    /**
     * Adds a Compare to the steps List.
     *
     * @param compare the compare step to add.
     */
    public void addCompare(final Compare compare)
    {
        logger.trace("addCompare({}) - start", compare);

        steps.add(compare);
    }

    /**
     * Adds an Export to the steps List.
     *
     * @param export the export step to add.
     */
    public void addExport(final Export export)
    {
        logger.trace("addExport(export={}) - start", export);

        steps.add(export);
    }

    /**
     * Returns the size of batch inserts.
     *
     * @return the size of batch inserts.
     */
    public String getBatchSize()
    {
        return batchSize;
    }

    /**
     * sets the size of batch inserts.
     * @param batchSize the size of batch inserts.
     */
    public void setBatchSize(final String batchSize)
    {
        this.batchSize = batchSize;
    }

    /**
     * Returns the JDBC fetch size used for result sets.
     *
     * @return the JDBC fetch size used for result sets.
     */
    public String getFetchSize()
    {
        return fetchSize;
    }

    /**
     * Sets the JDBC fetch size used for result sets.
     *
     * @param fetchSize the JDBC fetch size used for result sets.
     */
    public void setFetchSize(final String fetchSize)
    {
        this.fetchSize = fetchSize;
    }

    /**
     * Sets the flag for skipping Oracle recycle bin tables.
     *
     * @param skipOracleRecycleBinTables <code>true</code> to skip Oracle recycle bin tables.
     */
    public void setSkipOracleRecycleBinTables(final Boolean skipOracleRecycleBinTables)
    {
        this.skipOracleRecycleBinTables = skipOracleRecycleBinTables;
    }

    /**
     * Load the step and then execute it.
     */
    @Override
    public void execute() throws BuildException
    {
        logger.trace("execute() - start");

        try
        {
            final IDatabaseConnection connection = createConnection();

            final Iterator stepIter = steps.listIterator();
            while (stepIter.hasNext())
            {
                final DbUnitTaskStep step = (DbUnitTaskStep)stepIter.next();
                log(step.getLogMessage(), Project.MSG_INFO);
                step.execute(connection);
            }
        }
        catch (DatabaseUnitException | SQLException e)
        {
            throw new BuildException(e, getLocation());
        }
        finally
        {
            try
            {
                if (conn != null)
                {
                    conn.close();
                }
            }
            catch (final SQLException e)
            {
                logger.error("execute()", e);
            }
        }
    }

    /**
     * Loads the JDBC driver and opens the configured database connection.
     *
     * @return the opened database connection.
     * @throws SQLException if the connection cannot be opened.
     */
    protected IDatabaseConnection createConnection() throws SQLException
    {
        logger.trace("createConnection() - start");

        if (driver == null)
        {
            throw new BuildException("Driver attribute must be set!", getLocation());
        }
        if (userId == null)
        {
            throw new BuildException("User Id attribute must be set!", getLocation());
        }
        if (password == null)
        {
            throw new BuildException("Password attribute must be set!", getLocation());
        }
        if (url == null)
        {
            throw new BuildException("Url attribute must be set!", getLocation());
        }
        if (steps.size() == 0)
        {
            throw new BuildException("Must declare at least one step in a <dbunit> task!", getLocation());
        }

        // Instantiate JDBC driver
        Driver driverInstance = null;
        try
        {
            Class dc;
            if (classpath != null)
            {
                log("Loading " + driver + " using AntClassLoader with classpath " + classpath,
                        Project.MSG_VERBOSE);

                loader = new AntClassLoader(getProject(), classpath);
                dc = loader.loadClass(driver);
            }
            else
            {
                log("Loading " + driver + " using system loader.", Project.MSG_VERBOSE);
                dc = Class.forName(driver);
            }
            driverInstance = (Driver)dc.newInstance();
        }
        catch (final ClassNotFoundException e)
        {
            throw new BuildException("Class Not Found: JDBC driver "
                    + driver + " could not be loaded", e, getLocation());
        }
        catch (final IllegalAccessException e)
        {
            throw new BuildException("Illegal Access: JDBC driver "
                    + driver + " could not be loaded", e, getLocation());
        }
        catch (final InstantiationException e)
        {
            throw new BuildException("Instantiation Exception: JDBC driver "
                    + driver + " could not be loaded", e, getLocation());
        }

        log("connecting to " + url, Project.MSG_VERBOSE);
        final Properties info = new Properties();
        info.put("user", userId);
        info.put("password", password);
        conn = driverInstance.connect(url, info);

        if (conn == null)
        {
            // Driver doesn't understand the URL
            throw new SQLException("No suitable Driver for " + url);
        }
        conn.setAutoCommit(true);

        final IDatabaseConnection connection = createDatabaseConnection(conn, schema);
        return connection;
    }

    /**
     * Creates the dbunit connection using the two given arguments. The configuration
     * properties of the dbunit connection are initialized using the fields of this class.
     *
     * @param jdbcConnection the JDBC connection to adapt.
     * @param dbSchema the database schema.
     * @return The dbunit connection
     */
    protected IDatabaseConnection createDatabaseConnection(final Connection jdbcConnection,
            final String dbSchema)
    {
        logger.trace("createDatabaseConnection(jdbcConnection={}, dbSchema={}) - start", jdbcConnection, dbSchema);

        IDatabaseConnection connection = null;
        try
        {
            connection = new DatabaseConnection(jdbcConnection, dbSchema);
        }
        catch(final DatabaseUnitException e)
        {
            throw new BuildException("Could not create dbunit connection object", e);
        }
        final DatabaseConfig config = connection.getConfig();

        if(this.dbConfig != null){
            try {
                this.dbConfig.copyTo(config);
            }
            catch(final DatabaseUnitException e)
            {
                throw new BuildException("Could not populate dbunit config object", e, getLocation());
            }
        }

        // For backwards compatibility (old mode overrides the new one) copy the other attributes to the config
        copyAttributes(config);

        log("Created connection for schema '" + schema + "' with config: " + config, Project.MSG_VERBOSE);

        return connection;
    }

    /**
     * @param config
     * @deprecated since 2.4. Only here because of backwards compatibility should be removed in the next major release.
     */
    @Deprecated
    private void copyAttributes(final DatabaseConfig config)
    {
        if(supportBatchStatement!=null)
            config.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, supportBatchStatement.booleanValue());
        if(useQualifiedTableNames!=null)
            config.setFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, useQualifiedTableNames.booleanValue());
        if(datatypeWarning!=null)
            config.setFeature(DatabaseConfig.FEATURE_DATATYPE_WARNING, datatypeWarning.booleanValue());
        if(skipOracleRecycleBinTables!=null)
            config.setFeature(DatabaseConfig.FEATURE_SKIP_ORACLE_RECYCLEBIN_TABLES, skipOracleRecycleBinTables.booleanValue());
        if(allowEmptyFields!=null)
            config.setFeature(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, allowEmptyFields.booleanValue());

        if(escapePattern!=null)
        {
            config.setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, escapePattern);
        }
        if (batchSize != null)
        {
            final Integer batchSizeInteger = new Integer(batchSize);
            config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, batchSizeInteger);
        }
        if (fetchSize != null)
        {
            config.setProperty(DatabaseConfig.PROPERTY_FETCH_SIZE, new Integer(fetchSize));
        }

        // Setup data type factory
        if(this.dataTypeFactory!=null) {
            try
            {
                final IDataTypeFactory dataTypeFactory = (IDataTypeFactory)Class.forName(
                        this.dataTypeFactory).newInstance();
                config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, dataTypeFactory);
            }
            catch (final ClassNotFoundException e)
            {
                throw new BuildException("Class Not Found: DataType factory "
                        + driver + " could not be loaded", e, getLocation());
            }
            catch (final IllegalAccessException e)
            {
                throw new BuildException("Illegal Access: DataType factory "
                        + driver + " could not be loaded", e, getLocation());
            }
            catch (final InstantiationException e)
            {
                throw new BuildException("Instantiation Exception: DataType factory "
                        + driver + " could not be loaded", e, getLocation());
            }
        }

    }
}
