/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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

package org.dbunit.database.search;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.IMetadataHandler;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.util.QualifiedTableName;
import org.dbunit.util.SQLHelper;
import org.dbunit.util.search.AbstractNodesFilterSearchCallback;
import org.dbunit.util.search.IEdge;
import org.dbunit.util.search.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Super-class for the ISearchCallback that implements the
 * <code>getEdges()</code> method using the database meta-data.
 * 
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 25, 2005
 */
public abstract class AbstractMetaDataBasedSearchCallback extends AbstractNodesFilterSearchCallback {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractMetaDataBasedSearchCallback.class);

    private final IDatabaseConnection connection;

    /**
     * Default constructor.
     * @param connection connection where the edges will be calculated from
     */
    public AbstractMetaDataBasedSearchCallback(IDatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * Get the connection where the edges will be calculated from.
     * @return the connection where the edges will be calculated from
     */
    public IDatabaseConnection getConnection() {
        return connection;
    }

    protected static final int IMPORT = 0;
    protected static final int EXPORT = 1;

    /** 
     * indexes of the column names on the MetaData result sets.
     */
    protected static final int[] TABLENAME_INDEXES = { 3, 7 };  
    protected static final int[] SCHEMANAME_INDEXES = { 2, 6 };  
    protected static final int[] PK_INDEXES = { 4, 4 };
    protected static final int[] FK_INDEXES = { 8, 8 };


    /**
     * Get the nodes using the direct foreign key dependency, i.e, if table A has
     * a FK for a table B, then getNodesFromImportedKeys(A) will return B.
     * @param node table name 
     * @return tables with direct FK dependency from node
     * @throws SearchException
     */
    protected SortedSet getNodesFromImportedKeys(Object node)
    throws SearchException {
        logger.debug("getNodesFromImportedKeys(node={}) - start", node);

        return getNodes(IMPORT, node);
    }

    /**
     * Get the nodes using the reverse foreign key dependency, i.e, if table C has
     * a FK for a table A, then getNodesFromExportedKeys(A) will return C.<br>
     * 
     * <strong>NOTE:</strong> this method should be used only as an auxiliary
     * method for sub-classes that also use <code>getNodesFromImportedKeys()</code>
     * or something similar, otherwise the generated sequence of tables might not
     * work when inserted in the database (as some tables might be missing).
     * <br>
     * @param node table name 
     * @return tables with reverse FK dependency from node
     * @throws SearchException
     */
    protected SortedSet getNodesFromExportedKeys(Object node)
    throws SearchException {
        logger.debug("getNodesFromExportedKeys(node={}) - start", node);

        return getNodes(EXPORT, node);
    }

    /**
     * Get the nodes using the both direct and reverse foreign key dependency, i.e, 
     * if table C has a FK for a table A and table A has a FK for a table B, then 
     * getNodesFromImportAndExportedKeys(A) will return B and C.
     * @param node table name 
     * @return tables with reverse and direct FK dependency from node
     * @throws SearchException
     */
    protected SortedSet getNodesFromImportAndExportKeys(Object node)
    throws SearchException {
        logger.debug("getNodesFromImportAndExportKeys(node={}) - start", node);

        SortedSet importedNodes = getNodesFromImportedKeys( node );
        SortedSet exportedNodes = getNodesFromExportedKeys( node );
        importedNodes.addAll( exportedNodes );
        return importedNodes;
    }

    private SortedSet getNodes(int type, Object node) throws SearchException {
    	if(logger.isDebugEnabled())
    		logger.debug("getNodes(type={}, node={}) - start", Integer.toString(type), node);

        try {
            Connection conn = this.connection.getConnection();
            String schema = this.connection.getSchema();
            DatabaseMetaData metaData = conn.getMetaData();
            SortedSet edges = new TreeSet();
            getNodes(type, node, conn, schema, metaData, edges);
            return edges;
        } catch (SQLException e) {
            throw new SearchException(e);
        } catch (NoSuchTableException e) {
            throw new SearchException(e);
        }
    }

    private void getNodes(int type, Object node, Connection conn,
            String schema, DatabaseMetaData metaData, SortedSet edges)
    throws SearchException, NoSuchTableException 
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("getNodes(type={}, node={}, conn={}, schema={}, metaData={}, edges={}) - start", 
                    new Object[] {String.valueOf(type), node, conn, schema, metaData, edges});
            logger.debug("Getting edges for node " + node);
        }
        
        if (!(node instanceof String)) {
            throw new IllegalArgumentException("node '" + node + "' should be a String, not a "
                    + node.getClass().getName());
        }
        String tableName = (String) node;

    	QualifiedTableName qualifiedTableName = new QualifiedTableName(tableName, schema);
    	schema = qualifiedTableName.getSchema();
    	tableName = qualifiedTableName.getTable();
        
        ResultSet rs = null;
        try {
            IMetadataHandler metadataHandler = (IMetadataHandler)
                    this.connection.getConfig().getProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER);
            // Validate if the table exists; fall back to DB-corrected case for databases
            // (e.g. PostgreSQL) that store unquoted identifiers in a different case than supplied.
            if (!metadataHandler.tableExists(metaData, schema, tableName))
            {
                String correctedTableName = SQLHelper.correctCase(tableName, conn);
                if (correctedTableName.equals(tableName) || !metadataHandler.tableExists(metaData, schema, correctedTableName))
                {
                    throw new NoSuchTableException("The table '"+tableName+"' does not exist in schema '"+schema+"'");
                }
                tableName = correctedTableName;
            }

            switch (type) {
            case IMPORT:
                rs = metaData.getImportedKeys(null, schema, tableName);
                break;
            case EXPORT:
                rs = metaData.getExportedKeys(null, schema, tableName);
                break;
            }
            
            
            
            DatabaseConfig dbConfig = this.connection.getConfig();
            while (rs.next()) {
                int index = TABLENAME_INDEXES[type];
                int schemaindex = SCHEMANAME_INDEXES[type];
                String dependentTableName = rs.getString(index);
                String dependentSchemaName = rs.getString(schemaindex);
                String pkColumn = rs.getString( PK_INDEXES[type] );
                String fkColumn = rs.getString( FK_INDEXES[type] );

                // set the schema in front if there is none ("SCHEMA.TABLE") - depending on the "qualified table names" feature
            	tableName = new QualifiedTableName(tableName, schema).getQualifiedNameIfEnabled(dbConfig);
            	dependentTableName = new QualifiedTableName(dependentTableName, dependentSchemaName).getQualifiedNameIfEnabled(dbConfig);
                
                IEdge edge = newEdge(rs, type, tableName, dependentTableName, fkColumn, pkColumn );
                if ( logger.isDebugEnabled() ) {
                    logger.debug("Adding edge " + edge);
                }
                edges.add(edge);
            }
        } 
        catch (SQLException e) {
            throw new SearchException(e);
        }
        finally
        {
        	try {
        		SQLHelper.close(rs);
            } catch (SQLException e) {
                throw new SearchException(e);
            }        		
        }
    }


    /**
     * Creates an edge representing a foreign key relationship between 2 tables.<br>
     * @param rs database meta-data result set
     * @param type type of relationship (IMPORT or EXPORT)
     * @param from name of the table representing the 'from' node
     * @param to name of the table representing the 'to' node
     * @param fkColumn name of the foreign key column
     * @param pkColumn name of the primary key column
     * @return edge representing the relationship between the 2 tables, according to 
     * the type
     * @throws SearchException not thrown in this method (but might on sub-classes)
     */
    protected static ForeignKeyRelationshipEdge createFKEdge(ResultSet rs, int type, 
            String from, String to, String fkColumn, String pkColumn)
    throws SearchException {
        if (logger.isDebugEnabled()) {
            logger.debug("createFKEdge(rs={}, type={}, from={}, to={}, fkColumn={}, pkColumn={}) - start",
                    new Object[] {rs, String.valueOf(type), from, to, fkColumn, pkColumn});
        }

        return type == IMPORT ? 
                new ForeignKeyRelationshipEdge( from, to, fkColumn, pkColumn ) :
                    new ForeignKeyRelationshipEdge( to, from, fkColumn, pkColumn );
    }


    /**
     * This method can be overwritten by the sub-classes if they need to decorate
     * the edge (for instance, providing an Edge that contains the primary and 
     * foreign keys used).
     * @param rs database meta-data result set
     * @param type type of relationship (IMPORT or EXPORT)
     * @param from name of the table representing the 'from' node
     * @param to name of the table representing the 'to' node
     * @param fkColumn name of the foreign key column
     * @param pkColumn name of the primary key column
     * @return edge representing the relationship between the 2 tables, according to 
     * the type
     * @throws SearchException not thrown in this method (but might on sub-classes)
     */
    protected IEdge newEdge(ResultSet rs, int type, String from, String to, String fkColumn, String pkColumn)
    throws SearchException {
        if (logger.isDebugEnabled()) {
            logger.debug("newEdge(rs={}, type={}, from={}, to={}, fkColumn={}, pkColumn={}) - start",
                    new Object[] {rs, String.valueOf(type), from, to, fkColumn, pkColumn});
        }

        return createFKEdge( rs, type, from, to, fkColumn, pkColumn );
    }
}
