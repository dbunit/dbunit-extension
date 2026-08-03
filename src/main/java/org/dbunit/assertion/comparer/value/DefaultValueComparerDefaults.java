package org.dbunit.assertion.comparer.value;

import java.util.Collections;
import java.util.Map;

/**
 * Default implementation for the {@link ValueComparerDefaults}.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class DefaultValueComparerDefaults implements ValueComparerDefaults
{

    @Override
    public ValueComparer getDefaultValueComparer()
    {
        return ValueComparers.isActualEqualToExpected;
    }

    @Override
    public Map<String, Map<String, ValueComparer>> getDefaultTableColumnValueComparerMap()
    {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, ValueComparer> getDefaultColumnValueComparerMapForTable(
            final String tableName)
    {
        return Collections.emptyMap();
    }
}
