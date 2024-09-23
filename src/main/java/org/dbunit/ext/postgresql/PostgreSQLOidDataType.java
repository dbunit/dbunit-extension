package org.dbunit.ext.postgresql;

import org.dbunit.dataset.datatype.BytesDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.sql.*;

public class PostgreSQLOidDataType
        extends BytesDataType {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLOidDataType.class);

    public PostgreSQLOidDataType() {
        super("OID", Types.BIGINT);
    }

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
            LargeObject obj = lobj.open(oid, LargeObjectManager.READ);
            // If lobj.open() throws an exception, it means something wrong with the OID / table.
            // So to be accurate, we don't catch this exception but let it propagate.
            // Swallowing the exception silently indeed hides the problem, which is the wrong behavior
//            try {
//                obj = lobj.open(oid, LargeObjectManager.READ);
//            } catch (SQLException ex) {
//                logger.error("Failed to open oid {} for Large Object reading.", oid);
//                logger.error("Exception: {}", ex.getMessage());
//                logger.error("Returning null instead of bailing out");
//                return null;
//            }

            // Read the data
            byte buf[] = new byte[obj.size()];
            obj.read(buf, 0, obj.size());
            // Close the object
            obj.close();

            return buf;
        } finally {
            connection.setAutoCommit(autoCommit);
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
            LargeObjectManager lobj = ((org.postgresql.PGConnection) statement.getConnection()).getLargeObjectAPI();

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
