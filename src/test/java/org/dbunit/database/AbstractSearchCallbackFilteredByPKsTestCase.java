package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dbunit.AbstractHSQLTestCase;
import org.dbunit.database.PrimaryKeyFilter.PkTableMap;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.util.CollectionsHelper;
import org.dbunit.util.search.SearchException;

public abstract class AbstractSearchCallbackFilteredByPKsTestCase
        extends AbstractHSQLTestCase
{

    private static final char FIRST_TABLE = 'A';

    private PkTableMap fInput = new PkTableMap();
    private PkTableMap fOutput = new PkTableMap();

    protected abstract int[] setupTablesSizeFixture();

    protected IDataSet setupTablesDataSetFixture() throws SQLException
    {
        final IDatabaseConnection connection = getConnection();
        final IDataSet allDataSet = connection.createDataSet();
        return allDataSet;
    }

    protected void addInput(final String tableName, final String[] ids)
    {
        // Set idsSet = CollectionsHelper.objectsToSet( ids );
        final SortedSet<String> idsSet = new TreeSet<>(Arrays.asList(ids));
        this.fInput.put(tableName, idsSet);
    }

    protected void addOutput(final String tableName, final String[] ids)
    {
        // List idsList = Arrays.asList( ids );
        // Set idsSet = CollectionsHelper.objectsToSet( ids );
        final SortedSet<String> idsSet = new TreeSet<>(Arrays.asList(ids));
        this.fOutput.put(tableName, idsSet);
    }

    protected abstract IDataSet getDataset()
            throws SQLException, SearchException, DataSetException;

    protected void doIt() throws SQLException, DataSetException, SearchException
    {
        final IDataSet dataset = getDataset();
        assertThat(dataset).isNotNull();

        // first, check if only the correct tables had been generated
        final String[] outputTables = dataset.getTableNames();
        assertTablesSize(outputTables);
        assertTablesName(outputTables);
        assertRows(dataset);
    }

    protected void assertTablesSize(final String[] actualTables)
    {
        final int expectedSize = this.fOutput.size();
        final int actualSize = actualTables.length;
        if (expectedSize != actualSize)
        {
            super.logger.error(
                    "Expected tables: " + dump(this.fOutput.getTableNames()));
            super.logger.error("Actual tables: " + dump(actualTables));
            fail("I number of returned tables did not match: " + actualSize
                    + " instead of " + expectedSize);
        }
    }

    protected void assertTablesName(final String[] outputTables)
    {
        final Set<Object> expectedTables =
                CollectionsHelper.objectsToSet(this.fOutput.getTableNames());
        final Set<String> notExpectedTables = new HashSet<>();
        boolean ok = true;
        // first check if expected tables are lacking or nonExpected tables were
        // found
        for (int i = 0; i < outputTables.length; i++)
        {
            final String table = outputTables[i];
            if (expectedTables.contains(table))
            {
                expectedTables.remove(table);
            } else
            {
                notExpectedTables.add(table);
            }
        }
        if (!notExpectedTables.isEmpty())
        {
            ok = false;
            super.logger.error(
                    "Returned tables not waited: " + dump(notExpectedTables));
        }
        if (!expectedTables.isEmpty())
        {
            ok = false;
            super.logger.error(
                    "Waited tables not returned: " + dump(expectedTables));
        }
        if (!ok)
        {
            fail("Returned tables do not match the expectation; check error output");
        }
    }

    protected void assertRows(final IDataSet dataset) throws DataSetException
    {
        final ITableIterator iterator = dataset.iterator();
        while (iterator.next())
        {
            final ITable table = iterator.getTable();
            final String tableName = table.getTableMetaData().getTableName();
            final String idField = "PK" + tableName;
            final Set<?> expectedIds = this.fOutput.get(tableName);
            final Set<String> actualIds = new HashSet<>();
            final int rowCount = table.getRowCount();
            for (int row = 0; row < rowCount; row++)
            {
                final String id = (String) table.getValue(row, idField);
                actualIds.add(id);
                if (super.logger.isDebugEnabled())
                {
                    super.logger.debug(
                            "T:" + tableName + " row: " + row + " id: " + id);
                }
            }
            // Collections.sort( expectedIds );
            // Collections.sort( actualIds );
            assertThat(actualIds)
                    .as("ids of table " + tableName + " do not match")
                    .isEqualTo(expectedIds);
        }
    }

    public void testSetupTables() throws SQLException, DataSetException
    {
        final int[] sizes = setupTablesSizeFixture();
        final IDataSet allDataSet = setupTablesDataSetFixture();
        assertNotNull(allDataSet);
        for (short i = 0; i < sizes.length; i++)
        {
            final char table = (char) (FIRST_TABLE + i);
            if (super.logger.isDebugEnabled())
            {
                super.logger.debug("Getting table " + table);
            }
            final ITable itable = allDataSet.getTable("" + table);
            assertThat(itable).as("did not find table " + table).isNotNull();
            assertThat(itable.getRowCount())
                    .as("size did not match for table " + table)
                    .isEqualTo(sizes[i]);
        }
    }

    protected PkTableMap getInput()
    {
        return this.fInput;
    }

    protected PkTableMap getOutput()
    {
        return this.fOutput;
    }

}
