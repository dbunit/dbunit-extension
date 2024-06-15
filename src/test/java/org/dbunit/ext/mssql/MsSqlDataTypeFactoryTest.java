package org.dbunit.ext.mssql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Created By: fede Date: 8-set-2004 Time: 15.08.55
 *
 * Last Checkin: $Author$ Date: $Date$ Revision: $Revision$
 */
public class MsSqlDataTypeFactoryTest
{
    public IDataTypeFactory createFactory() throws Exception
    {
        return new MsSqlDataTypeFactory();
    }

    @Test
    void testCreateCharDataType() throws Exception
    {
        final int sqlType = MsSqlDataTypeFactory.NCHAR;
        final String sqlTypeName = "nchar";

        final DataType expected = DataType.CHAR;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isEqualTo(expected);
    }

    @Test
    void testCreateVarcharDataType() throws Exception
    {
        final int sqlType = MsSqlDataTypeFactory.NVARCHAR;
        final String sqlTypeName = "nvarchar";

        final DataType expected = DataType.VARCHAR;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isEqualTo(expected);
    }

    @Test
    void testCreateLongVarcharDataTypeFromNtext() throws Exception
    {
        final int sqlType = MsSqlDataTypeFactory.NTEXT;
        final String sqlTypeName = "ntext";

        final DataType expected = DataType.LONGVARCHAR;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isEqualTo(expected);
    }

    @Test
    void testCreateLongVarcharDataTypeFromNtextMsSql2005() throws Exception
    {
        final int sqlType = MsSqlDataTypeFactory.NTEXT_MSSQL_2005;
        final String sqlTypeName = "ntext";

        final DataType expected = DataType.LONGVARCHAR;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isEqualTo(expected);
    }

    @Test
    void testCreateUniqueIdentifierType() throws Exception
    {
        final int sqlType = Types.CHAR;
        final String sqlTypeName = UniqueIdentifierType.UNIQUE_IDENTIFIER_TYPE;

        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertTrue(actual instanceof UniqueIdentifierType);
    }

    @Test
    void testCreateDateTimeOffsetType() throws Exception
    {
        final int sqlType = DateTimeOffsetType.TYPE;
        final String sqlTypeName = "datetimeoffset";

        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertTrue(actual instanceof DateTimeOffsetType);
    }
}
