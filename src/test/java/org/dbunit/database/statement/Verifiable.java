package org.dbunit.database.statement;

/**
 * @author Edward Mann <e.dev@edmann.com>
 *         <p>
 *         Created: Jan 20, 2024
 */
public interface Verifiable
{

    /**
     * Throw an AssertionFailedException if any expectations have not been met.
     */
    public abstract void verify();
}
