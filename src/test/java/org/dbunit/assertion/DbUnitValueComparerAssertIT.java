package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.FileNotFoundException;
import java.util.Map;

import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.dbunit.assertion.comparer.value.builder.ColumnValueComparerMapBuilder;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

public class DbUnitValueComparerAssertIT
{
    public static final String FILE_PATH = "xml/assertionTest.xml";

    private final DbUnitValueComparerAssert sut =
            new DbUnitValueComparerAssert();

    private IDataSet getDataSet() throws DataSetException, FileNotFoundException
    {
        return new FlatXmlDataSetBuilder()
                .build(TestUtils.getFileReader(FILE_PATH));
    }

    @Test
    void testAssertWithValueComparerITableITableValueComparer_AllRowsEqual_NoFail()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();

        final ITable expectedTable = dataSet.getTable("TEST_TABLE");
        final ITable actualTable =
                dataSet.getTable("TEST_TABLE_WITH_SAME_VALUE");
        final ValueComparer defaultValueComparer =
                ValueComparers.isActualEqualToExpected;
        assertDoesNotThrow(() -> sut.assertWithValueComparer(expectedTable,
                actualTable, defaultValueComparer));
    }

    @Test
    void testAssertWithValueComparerITableITableValueComparerMap_OneColumnNotEqual_NoFail()
            throws Exception
    {
        final IDataSet dataSet = getDataSet();

        final ITable expectedTable = dataSet.getTable("TEST_TABLE");
        final ITable actualTable =
                dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE_ALL_ROWS");
        final ValueComparer defaultValueComparer =
                ValueComparers.isActualEqualToExpected;
        final ValueComparer valueComparer =
                ValueComparers.isActualNotEqualToExpected;
        final Map<String, ValueComparer> columnValueComparers =
                new ColumnValueComparerMapBuilder()
                        .add("COLUMN2", valueComparer).build();
        assertDoesNotThrow(() -> sut.assertWithValueComparer(expectedTable,
                actualTable, defaultValueComparer, columnValueComparers));
    }

    @Test
    void testAssertWithValueComparer_isActualGreaterThan_passesWhenActualIsLarger()
            throws Exception
    {
        final Column[] columns = new Column[]{new Column("SCORE", DataType.INTEGER)};

        final DefaultTable expected = new DefaultTable("T", columns);
        expected.addRow(new Object[]{10});

        final DefaultTable actual = new DefaultTable("T", columns);
        actual.addRow(new Object[]{20});

        assertDoesNotThrow(() -> sut.assertWithValueComparer(expected, actual,
                ValueComparers.isActualGreaterThanExpected));
    }

    @Test
    void testAssertWithValueComparer_isActualGreaterThan_failsWhenActualIsSmaller()
            throws Exception
    {
        final Column[] columns = new Column[]{new Column("SCORE", DataType.INTEGER)};

        final DefaultTable expected = new DefaultTable("T", columns);
        expected.addRow(new Object[]{100});

        final DefaultTable actual = new DefaultTable("T", columns);
        actual.addRow(new Object[]{5});

        assertThatThrownBy(() -> sut.assertWithValueComparer(expected, actual,
                ValueComparers.isActualGreaterThanExpected))
                        .as("Smaller actual should fail isActualGreaterThan.")
                        .isInstanceOf(DbComparisonFailure.class);
    }

    @Test
    void testAssertWithValueComparer_isActualNull_passesWhenActualIsNull()
            throws Exception
    {
        final Column[] columns = new Column[]{new Column("VAL", DataType.VARCHAR)};

        final DefaultTable expected = new DefaultTable("T", columns);
        expected.addRow(new Object[]{"anything"});

        final DefaultTable actual = new DefaultTable("T", columns);
        actual.addRow(new Object[]{null});

        assertDoesNotThrow(() -> sut.assertWithValueComparer(expected, actual,
                ValueComparers.isActualNullValueComparer));
    }

    @Test
    void testAssertWithValueComparer_isActualNotNull_passesWhenActualIsNotNull()
            throws Exception
    {
        final Column[] columns = new Column[]{new Column("VAL", DataType.VARCHAR)};

        final DefaultTable expected = new DefaultTable("T", columns);
        expected.addRow(new Object[]{"anything"});

        final DefaultTable actual = new DefaultTable("T", columns);
        actual.addRow(new Object[]{"someValue"});

        assertDoesNotThrow(() -> sut.assertWithValueComparer(expected, actual,
                ValueComparers.isActualNotNullValueComparer));
    }
}
