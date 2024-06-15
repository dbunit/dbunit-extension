package org.dbunit.database.search;

import java.sql.SQLException;

import org.dbunit.database.AbstractImportedAndExportedKeysFilteredByPKsTestCase;
import org.dbunit.dataset.DataSetException;
import org.dbunit.util.search.SearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportedAndExportedKeysFilteredByPKsSingleTest
        extends AbstractImportedAndExportedKeysFilteredByPKsTestCase
{

    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUpConnectionWithFile("hypersonic_simple_dataset.sql");
    }

    @Override
    protected int[] setupTablesSizeFixture()
    {
        final int[] sizes = new int[] {1, 1, 2};
        return sizes;
    }

    @Test
    void testCWithOne() throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithTwo() throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1, C2});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithTwoInvertedInput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C2, C1});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithTwoInvertedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1, C2});
        addOutput(C, new String[] {C2, C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithRepeated()
            throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1, C2, C2, C1, C1, C1, C2, C2});
        addOutput(C, new String[] {C2, C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testB() throws DataSetException, SQLException, SearchException
    {
        addInput(B, new String[] {B1});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testA() throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }
}
