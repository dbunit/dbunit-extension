package org.dbunit.ext.postgresql;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;

public class GeometryType extends AbstractDataType {
    public GeometryType() {
        super("geometry", Types.OTHER, String.class, false);
    }

    public Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException {
        return resultSet.getString(column);
    }

    public void setSqlValue(Object geom, int column, PreparedStatement statement)
            throws SQLException, TypeCastException {
        statement.setObject(column,
                getGeometry(geom, statement.getConnection()));
    }

    public Object typeCast(Object arg0) throws TypeCastException {
        return arg0.toString();
    }

    private Object getGeometry(Object value, Connection connection)
            throws TypeCastException {
        Object tempgeom = null;

        try {
            Class aPGIntervalClass = super.loadClass("net.postgis.jdbc.PGgeometry",
                    connection);
            Constructor ct = aPGIntervalClass
                    .getConstructor(new Class[] { String.class });

            tempgeom = ct.newInstance(new Object[] { value });
        } catch (ClassNotFoundException e) {
            throw new TypeCastException(value, this, e);
        } catch (InvocationTargetException e) {
            throw new TypeCastException(value, this, e);
        } catch (NoSuchMethodException e) {
            throw new TypeCastException(value, this, e);
        } catch (IllegalAccessException e) {
            throw new TypeCastException(value, this, e);
        } catch (InstantiationException e) {
            throw new TypeCastException(value, this, e);
        }

        return tempgeom;
    }
}
