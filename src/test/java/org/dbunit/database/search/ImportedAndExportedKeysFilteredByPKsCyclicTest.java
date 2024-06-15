package org.dbunit.database.search;

import java.sql.SQLException;

import org.dbunit.database.AbstractImportedAndExportedKeysFilteredByPKsTestCase;
import org.dbunit.dataset.DataSetException;
import org.dbunit.util.search.SearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportedAndExportedKeysFilteredByPKsCyclicTest
        extends AbstractImportedAndExportedKeysFilteredByPKsTestCase
{

    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUpConnectionWithFile("hypersonic_cyclic_dataset.sql");
    }

    @Override
    protected int[] setupTablesSizeFixture()
    {
        final int[] sizes = new int[] {2, 1, 1};
        return sizes;
    }

    @Test
    void testAWithOne() throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testAWithTwo() throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1, A2});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1, A2});
        doIt();
    }

    @Test
    void testAWithTwoInvertedInput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A2, A1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1, A2});
        doIt();
    }

    @Test
    void testAWithTwoInvertedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1, A2});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2, A1});
        doIt();
    }

    @Test
    void testAWithRepeated()
            throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1, A2, A2, A1, A1, A1, A2, A2});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2, A1});
        doIt();
    }

    @Test
    void testBWithOne() throws DataSetException, SQLException, SearchException
    {
        addInput(B, new String[] {B1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2, A1});
        doIt();
    }

    @Test
    void testCWithOne() throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2, A1});
        doIt();
    }

}
