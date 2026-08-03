package org.dbunit.assertion.comparer.value.verifier;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.assertion.comparer.value.ValueComparer;

/**
 * Strategy pattern for verifying a correctly configured
 * {@link VerifyTableDefinition}, e.g. a {@link ValueComparer} does not exist
 * for a column with an excluded column definition.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public interface VerifyTableDefinitionVerifier
{
    /**
     * Verify the {@link VerifyTableDefinition} is valid.
     *
     * @param verifyTableDefinition the table definition to verify.
     */
    void verify(final VerifyTableDefinition verifyTableDefinition);
}
