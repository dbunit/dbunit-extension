package org.dbunit;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

/**
 * Strategy pattern for verifying {@link VerifyTableDefinition}s and
 * expectedDataSet configurations agree, e.g. have the same number of tables
 * defined.
 *
 * @author Jeff Jensen
 */
public interface ExpectedDataSetAndVerifyTableDefinitionVerifier
{
    /**
     * Verify {@link VerifyTableDefinition}s and expectedDataSet configurations
     * agree.
     *
     * @param verifyTableDefinitions the table definitions to verify.
     * @param expectedDataSet the expected dataset to verify against.
     * @param config the database configuration in effect.
     * @throws DataSetException if the verify table definitions and expected dataset disagree.
     */
    void verify(VerifyTableDefinition[] verifyTableDefinitions,
            IDataSet expectedDataSet, DatabaseConfig config)
            throws DataSetException;
}
