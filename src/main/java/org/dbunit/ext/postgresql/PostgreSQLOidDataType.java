package org.dbunit.ext.postgresql;

import org.dbunit.dataset.datatype.BytesDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.postgresql.util.PSQLState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;

/**
 * {@link BytesDataType} specialization for PostgreSQL's <code>oid</code> large object columns.
 *
 * @since 2.7.0
 */
public class PostgreSQLOidDataType
        extends BytesDataType {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLOidDataType.class);

    /**
     * Default constructor.
     */
    public PostgreSQLOidDataType() {
        super("OID", Types.BIGINT);
    }

    /**
     * Reads the large object referenced by the column's oid.
     *
     * @param column the column index to read, starting at 1.
     * @param resultSet the result set to read the column value from.
     * @return the large object's bytes, or <code>null</code> if the oid is zero (a SQL
     *         <code>NULL</code>) or does not refer to a large object.
     * @throws SQLException if a database access error occurs.
     * @throws TypeCastException never thrown by this implementation.
     */
    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException,
            TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        Statement statement = resultSet.getStatement();
        Connection connection = statement.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        // kinda ugly
        connection.setAutoCommit(false);

        try {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            LargeObjectManager lobj = pgConnection.getLargeObjectAPI();

            long oid = resultSet.getLong(column);
            if (oid == 0) {
                logger.debug("'oid' is zero, the data is NULL.");
                return null;
            }

            return readLargeObject(connection, lobj, oid);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Reads the large object referenced by the given oid, or returns <code>null</code> if the
     * oid does not refer to a large object at all - a PostgreSQL <code>oid</code> column is a
     * generic object identifier, not necessarily a large object reference (e.g.
     * <code>'table'::regclass</code>), so {@link LargeObjectManager#open(long, int)} failing with
     * SQLState <code>42704</code> (undefined_object) is an expected outcome, not a real error.
     * <p>
     * PostgreSQL aborts the entire enclosing transaction on any failed command, so the open runs
     * under a savepoint: on failure, rolling back to it clears the abort without discarding any
     * other work already done in that transaction. Only a failure from the open itself is treated
     * as "not a large object" - a failure reading or closing an object that did open is a real
     * error and is rethrown rather than masked, since by then the oid is proven to be a large
     * object.
     *
     * @param connection the connection to read from, already in a non-autocommit transaction.
     * @param lobj the large object API to read the oid through.
     * @param oid the oid value read from the result set.
     * @return the large object's bytes, or <code>null</code> if <code>oid</code> is not a large
     *         object.
     * @throws SQLException on any failure other than the oid not referring to a large object.
     */
    private byte[] readLargeObject(final Connection connection, final LargeObjectManager lobj, final long oid)
            throws SQLException
    {
        Savepoint savepoint = connection.setSavepoint();
        final LargeObject obj;
        try {
            obj = lobj.open(oid, LargeObjectManager.READ);
        } catch (SQLException ex) {
            connection.rollback(savepoint);
            if (PSQLState.UNDEFINED_OBJECT.getState().equals(ex.getSQLState())) {
                logger.debug("oid {} is not a large object (SQLState={}), returning null.", oid,
                    ex.getSQLState());
                return null;
            }
            throw ex;
        }

        try {
            byte buf[] = new byte[obj.size()];
            obj.read(buf, 0, obj.size());
            obj.close();
            return buf;
        } catch (SQLException ex) {
            // The transaction is aborted again here, same as an open() failure, so recover the
            // same way - but rethrow unconditionally: the oid is proven to be a large object by
            // now, and attempting obj.close() in a finally block would itself throw (the
            // transaction is aborted until the rollback below runs), masking this exception.
            connection.rollback(savepoint);
            throw ex;
        }
    }

    @Override
    public void setSqlValue(final Object value, final int column, final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
            value, column, statement);

        Connection connection = statement.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        // kinda ugly
        connection.setAutoCommit(false);

        try {
            // Get the Large Object Manager to perform operations with
            LargeObjectManager lobj = (connection.unwrap(PGConnection.class)).getLargeObjectAPI();

            // Create a new large object
            long oid = lobj.createLO(LargeObjectManager.READ | LargeObjectManager.WRITE);

            // Open the large object for writing
            LargeObject obj = lobj.open(oid, LargeObjectManager.WRITE);

            // Now open the file
            ByteArrayInputStream bis = new ByteArrayInputStream((byte[]) super.typeCast(value));

            // Copy the data from the file to the large object
            byte buf[] = new byte[2048];
            int s = 0;
            while ((s = bis.read(buf, 0, 2048)) > 0) {
                obj.write(buf, 0, s);
            }

            // Close the large object
            obj.close();

            statement.setLong(column, oid);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
