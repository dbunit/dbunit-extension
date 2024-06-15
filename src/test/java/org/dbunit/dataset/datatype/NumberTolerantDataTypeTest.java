package org.dbunit.dataset.datatype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.Types;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the number tolerant data type which is quite similar to the
 * NumberDataTypeTest.
 *
 * @author gommma
 */
@ExtendWith(MockitoExtension.class)
public class NumberTolerantDataTypeTest extends AbstractDataTypeTest
{

    private NumberTolerantDataType THIS_TYPE =
            new NumberTolerantDataType("NUMERIC", Types.NUMERIC,
                    new ToleratedDeltaMap.Precision(new BigDecimal("1E-5")));
    private NumberTolerantDataType THIS_TYPE_PERCENTAGE =
            new NumberTolerantDataType("NUMERIC", Types.NUMERIC,
                    new ToleratedDeltaMap.Precision(new BigDecimal("1.0"),
                            true));

    @Mock
    private ResultSet mockedResultSet;

    @Test
    void testCreateWithNegativeDelta() throws Exception
    {
        final BigDecimal neg = new BigDecimal("-0.1");
        final IllegalArgumentException expected = assertThrows(
                IllegalArgumentException.class,
                () -> new ToleratedDeltaMap.Precision(neg),
                "Should not be able to created datatype with negative delta");
        final String expectedMsg = "The given delta '-0.1' must be >= 0";
        assertThat(expected).hasMessage(expectedMsg);
    }

    @Test
    void testCompareToWithDelta_DiffWithinToleratedDelta() throws Exception
    {
        final int result = THIS_TYPE.compare(new BigDecimal(0.12345678D),
                new BigDecimal(0.123456789D));
        assertThat(result).isZero();
    }

    @Test
    void testCompareToWithDelta_DiffOutsideOfToleratedDelta() throws Exception
    {
        final int result = THIS_TYPE.compare(new BigDecimal(0.1234),
                new BigDecimal(0.1235D));
        assertThat(result).isNegative().isEqualTo(-1);
    }

    @Test
    void testCompareToWithDeltaPercentage_DiffWithinToleratedDelta()
            throws Exception
    {
        final int result = THIS_TYPE_PERCENTAGE
                .compare(new BigDecimal("1000.0"), new BigDecimal("1010.0"));
        assertThat(result).isZero();
    }

    @Test
    void testCompareToWithDeltaPercentage_DiffOutsideOfToleratedDelta()
            throws Exception
    {
        final int result = THIS_TYPE_PERCENTAGE
                .compare(new BigDecimal("1000.0"), new BigDecimal("1010.1"));
        assertThat(result).isNegative().isEqualTo(-1);
    }

    /**
     *
     */
    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("NUMERIC");
    }

    /**
     *
     */
    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class")
                .isEqualTo(java.math.BigDecimal.class);
    }

    /**
     *
     */
    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        assertThat(THIS_TYPE.isNumber()).as("is number").isTrue();
    }

    @Override
    @Test
    public void testIsDateTime() throws Exception
    {
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isFalse();
    }

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, new BigDecimal((double) 1234), "1234",
                "12.34", Boolean.TRUE, Boolean.FALSE,};
        final BigDecimal[] expected = {null, new BigDecimal((double) 1234),
                new BigDecimal((double) 1234), new BigDecimal("12.34"),
                new BigDecimal("1"), new BigDecimal("0"),};

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (int j = 0; j < values.length; j++)
        {
            assertThat(THIS_TYPE.typeCast(values[j])).as("typecast " + j)
                    .isEqualTo(expected[j]);
        }
    }

    @Override
    @Test
    public void testTypeCastNone() throws Exception
    {
        assertThat(THIS_TYPE.typeCast(ITable.NO_VALUE)).as("typecast").isNull();
    }

    @Override
    @Test
    public void testTypeCastInvalid() throws Exception
    {
        final Object[] values = {new Object(), "bla",};

        for (int i = 0; i < values.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.typeCast(values[id]),
                    "Should throw TypeCastException - " + id);
        }
    }

    @Override
    @Test
    public void testCompareEquals() throws Exception
    {
        final Object[] values1 = {null, new BigDecimal((double) 1234), "1234",
                "12.34", Boolean.TRUE, Boolean.FALSE, new BigDecimal(123.4),
                "123",};
        final Object[] values2 = {null, new BigDecimal((double) 1234),
                new BigDecimal(1234), new BigDecimal("12.34"),
                new BigDecimal("1"), new BigDecimal("0"),
                new BigDecimal(123.4000), new BigDecimal("123.0"),};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            assertThat(THIS_TYPE.compare(values1[i], values2[i]))
                    .as("compare1 " + i).isZero();
            assertThat(THIS_TYPE.compare(values2[i], values1[i]))
                    .as("compare2 " + i).isZero();
        }
    }

    @Override
    @Test
    public void testCompareInvalid() throws Exception
    {
        final Object[] values1 = {new Object(), "bla",};
        final Object[] values2 = {null, null,};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[id], values2[id]),
                    "Should throw TypeCastException - " + id);

            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[id], values2[id]),
                    "Should throw TypeCastException - " + i);
        }
    }

    @Override
    @Test
    public void testCompareDifferent() throws Exception
    {
        final Object[] less = {null, "-7500", new BigDecimal("-0.01"),
                new BigInteger("1234"),};

        final Object[] greater = {"0", "5.555", new BigDecimal("0.01"),
                new BigDecimal("1234.5"),};

        assertThat(greater).as("values count").hasSameSizeAs(less);

        for (int j = 0; j < less.length; j++)
        {
            assertThat(THIS_TYPE.compare(less[j], greater[j])).as("less " + j)
                    .isNegative();
            assertThat(THIS_TYPE.compare(greater[j], less[j]))
                    .as("greater " + j).isPositive();
        }
    }

    @Override
    public void testSqlType() throws Exception
    {
    }

    @Override
    public void testForObject() throws Exception
    {
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final BigDecimal[] values = {new BigDecimal("1234"),};

        final String[] expected = {"1234",};

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(DataType.asString(values[i])).as("asString " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetSqlValue() throws Exception
    {
        final BigDecimal[] expected = {null, new BigDecimal("12.34"),};

        lenient().when(mockedResultSet.getBigDecimal(2))
                .thenReturn(expected[1]);

        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];

            final DataType dataType = THIS_TYPE;
            final Object actualValue =
                    dataType.getSqlValue(i + 1, mockedResultSet);
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

}
