/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.junit.jupiter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringTestTransactionCheckTest
{
    @Test
    void testGetAsBoolean_springNotVisibleToTheClassLoader_isFalse()
    {
        final ClassLoader withoutSpring = new ClassLoader(null)
        {
        };
        final SpringTestTransactionCheck check = new SpringTestTransactionCheck(withoutSpring);

        final boolean inATransaction = check.getAsBoolean();

        assertThat(inATransaction)
                .as("Without Spring there is no test-managed transaction, and no class of Spring"
                        + " is needed to say so.")
                .isFalse();
    }

    @Test
    void testGetAsBoolean_springPresentAndNoSpringTestRunning_isFalse()
    {
        final SpringTestTransactionCheck check =
                new SpringTestTransactionCheck(getClass().getClassLoader());

        final boolean inATransaction = check.getAsBoolean();

        assertThat(inATransaction)
                .as("No test-managed transaction is active outside a Spring test.")
                .isFalse();
    }
}
