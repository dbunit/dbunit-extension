package org.dbunit;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;

// TODO always passes on same counts, fix prod to always verify table names

class DefaultExpectedDataSetAndVerifyTableDefinitionVerifierTest
{
    private static final ITable TABLE_1 = new DefaultTable("TABLE_1");
    private static final ITable TABLE_2 = new DefaultTable("TABLE_2");

    private static final DatabaseConfig DATABASE_CONFIG = new DatabaseConfig();

    private ExpectedDataSetAndVerifyTableDefinitionVerifier sut =
            new DefaultExpectedDataSetAndVerifyTableDefinitionVerifier();

    @Test
    void testVerify_VtdMatchesExpected_Success() throws DataSetException
    {
        final VerifyTableDefinition[] verifyTableDefinitions =
                makeVerifyTableDefinitions_MatchingExpected();
        final IDataSet expectedDataSet = new DefaultDataSet(TABLE_1, TABLE_2);

        sut.verify(verifyTableDefinitions, expectedDataSet, DATABASE_CONFIG);
    }

    @Test
    void testVerify_VtdLessThanExpected_Exception() throws DataSetException
    {
        assertThrows(DataSetException.class, () -> {
            final VerifyTableDefinition[] verifyTableDefinitions =
                    makeVerifyTableDefinitions_LessThanExpected();
            final IDataSet expectedDataSet =
                    new DefaultDataSet(TABLE_1, TABLE_2);
            sut.verify(verifyTableDefinitions, expectedDataSet,
                    DATABASE_CONFIG);
        });
    }

    @Test
    void testVerify_VtdMoreThanExpected_Success() throws DataSetException
    {
        final VerifyTableDefinition[] verifyTableDefinitions =
                makeVerifyTableDefinitions_MoreThanExpected();
        final IDataSet expectedDataSet = new DefaultDataSet(TABLE_1, TABLE_2);

        sut.verify(verifyTableDefinitions, expectedDataSet, DATABASE_CONFIG);
    }

    private VerifyTableDefinition[] makeVerifyTableDefinitions_MatchingExpected()
    {
        final String[] excludeColumns = new String[0];

        final VerifyTableDefinition testTable =
                new VerifyTableDefinition("test_table", excludeColumns);
        final VerifyTableDefinition secondTable =
                new VerifyTableDefinition("second_table", excludeColumns);

        final VerifyTableDefinition[] tables = {testTable, secondTable};
        return tables;
    }

    private VerifyTableDefinition[] makeVerifyTableDefinitions_LessThanExpected()
    {
        final String[] excludeColumns = new String[0];

        final VerifyTableDefinition testTable =
                new VerifyTableDefinition("test_table", excludeColumns);

        final VerifyTableDefinition[] tables = {testTable};
        return tables;
    }

    private VerifyTableDefinition[] makeVerifyTableDefinitions_MoreThanExpected()
    {
        final String[] excludeColumns = new String[0];

        final VerifyTableDefinition testTable =
                new VerifyTableDefinition("test_table", excludeColumns);
        final VerifyTableDefinition secondTable =
                new VerifyTableDefinition("second_table", excludeColumns);
        final VerifyTableDefinition emptyTable =
                new VerifyTableDefinition("empty_table", excludeColumns);

        final VerifyTableDefinition[] tables =
                {testTable, secondTable, emptyTable};
        return tables;
    }
}
