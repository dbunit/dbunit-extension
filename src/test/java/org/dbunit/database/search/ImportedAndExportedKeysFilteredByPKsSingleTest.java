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
    void testCWithOne_withTableCAndOnePk_returnsCAndAllRelated() throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1});
        addOutput(C, new String[] {C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithTwo_withTableCAndTwoPks_returnsAllRows() throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1, C2});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithTwoInvertedInput_withInvertedInputOrder_returnsSortedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C2, C1});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithTwoInvertedOutput_withRegularInputOrder_returnsInvertedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1, C2});
        addOutput(C, new String[] {C2, C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testCWithRepeated_withDuplicatePkInputs_returnsDeduplicatedOutput()
            throws DataSetException, SQLException, SearchException
    {
        addInput(C, new String[] {C1, C2, C2, C1, C1, C1, C2, C2});
        addOutput(C, new String[] {C2, C1});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testB_withTableBAndOnePk_returnsCAndBAndA() throws DataSetException, SQLException, SearchException
    {
        addInput(B, new String[] {B1});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }

    @Test
    void testA_withTableAAndOnePk_returnsCAndBAndA() throws DataSetException, SQLException, SearchException
    {
        addInput(A, new String[] {A1});
        addOutput(C, new String[] {C1, C2});
        addOutput(B, new String[] {B1});
        addOutput(A, new String[] {A1});
        doIt();
    }
}
