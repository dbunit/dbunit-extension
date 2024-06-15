package org.dbunit.dataset;

import org.dbunit.database.CachedResultSetTable;
import org.dbunit.database.ForwardOnlyResultSetTable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class only logs out the toString() results for review, does not test
 * anything. Currently only ITables that subclass AbstractTable.
 *
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
class ToStringViewTest
{
    private final Logger LOG = LoggerFactory.getLogger(ToStringViewTest.class);

    @Test
    void testForwardOnlyResultSetTable()
    {
        // TODO existing test is an IT
        final ForwardOnlyResultSetTable table = null;
        LOG.info("ForwardOnlyResultSetTable.toString()={}", table);
    }

    @Test
    void testScrollableResultSetTable() throws Exception
    {
        // TODO existing test is an IT
        // ScrollableResultSetTableTest test =
        // new ScrollableResultSetTableTest("the string");
        final ITable table = null; // test.createTable();
        LOG.info("ScrollableResultSetTable.toString()={}", table);
    }

    @Test
    void testCompositeTable() throws Exception
    {
        final CompositeTableTest test = new CompositeTableTest();
        final ITable table = test.createTable();
        LOG.info("CompositeTable.toString()={}", table);
    }

    @Test
    void testDefaultTable() throws Exception
    {
        final DefaultTableTest test = new DefaultTableTest();
        final ITable table = test.createTable();
        LOG.info("DefaultTable.toString()={}", table);
    }

    @Test
    void testCachedTable()
    {
        // TODO no existing test to use
        final CachedTable table = null;
        LOG.info("CachedTable.toString()={}", table);
    }

    @Test
    void testCachedResultSetTable()
    {
        // TODO existing test is an IT
        final CachedResultSetTable table = null;
        LOG.info("CachedResultSetTable.toString()={}", table);
    }

    @Test
    void testSortedTable() throws Exception
    {
        final SortedTableTest test = new SortedTableTest();
        final ITable table = test.createTable();
        LOG.info("SortedTable.toString()={}", table);
    }

    public void testStreamingTable()
    {
        // StreamingTable is not a public class
        // StreamingTable table = null;
        // LOG.info("StreamingTable.toString()={}", table);
    }

    public void testXlsTable()
    {
        // XlsTable is not a public class
        // XlsTable table = null;
        // LOG.info("XlsTable.toString()={}", table);
    }
}
