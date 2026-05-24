package org.dbunit.database.search;

import java.sql.SQLException;

import org.dbunit.database.AbstractImportedKeysFilteredByPKsTestCase;
import org.dbunit.dataset.DataSetException;
import org.dbunit.util.search.SearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportedKeysFilteredByPKsCyclicTest
        extends AbstractImportedKeysFilteredByPKsTestCase
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
    void testAWithOne_withTableAAndOnePk_returnsCyclicDependencies() throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testAWithTwo_withTableAAndTwoPks_returnsCyclicDependencies() throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1, A2});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1, A2});
        doIt();
    }

    @Test
    void testAWithTwoInvertedInput_withInvertedInputOrder_returnsSortedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A2, A1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1, A2});
        doIt();
    }

    @Test
    void testAWithTwoInvertedOutput_withRegularInputOrder_returnsInvertedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1, A2});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2, A1});
        doIt();
    }

    @Test
    void testAWithRepatead_withDuplicatePkInputs_returnsDeduplicatedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1, A2, A1, A2, A1, A1, A2, A2, A2, A1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2, A1});
        doIt();
    }

    @Test
    void testBWithOne_withTableBAndOnePk_returnsCyclicDependencies() throws DataSetException, SQLException, SearchException
    {
        addInput(B, new String[] {B1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2});
        doIt();
    }

    @Test
    void testCWithOne_withTableCAndOnePk_returnsCyclicDependencies() throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A2});
        doIt();
    }

}
