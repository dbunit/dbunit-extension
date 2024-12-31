package org.dbunit.dataset.datatype;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ClobDataTypeTest
{
    @Mock
    private ResultSet mockedResultSet;

    /**
     * Assert calls ResultSet.getClob(columnIndex) before ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.CLOB.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getClob(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
