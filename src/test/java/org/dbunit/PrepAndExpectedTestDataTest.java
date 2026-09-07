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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link PrepAndExpectedTestData}.
 *
 * @since 3.6.0
 */
class PrepAndExpectedTestDataTest
{
    private static final VerifyTableDefinition ACCOUNT_TABLE =
            new VerifyTableDefinition("ACCOUNT", new String[] {});

    private static final VerifyTableDefinition TRANSACTION_TABLE =
            new VerifyTableDefinition("TRANSACTION", new String[] {});

    @Test
    void testConstructor_allThreeArraysGiven_gettersReturnEquivalentValues()
    {
        final VerifyTableDefinition[] verifyTables =
                {ACCOUNT_TABLE, TRANSACTION_TABLE};
        final String[] prepFiles = {"/prep/accounts.xml"};
        final String[] expectedFiles = {"/expected/accounts-after.xml"};

        final PrepAndExpectedTestData testData = new PrepAndExpectedTestData(
                verifyTables, prepFiles, expectedFiles);

        assertThat(testData.getVerifyTableDefinitions())
                .as("getVerifyTableDefinitions() must return the definitions"
                        + " given to the constructor.")
                .containsExactly(ACCOUNT_TABLE, TRANSACTION_TABLE);
        assertThat(testData.getPrepDataFiles())
                .as("getPrepDataFiles() must return the prep files given to the"
                        + " constructor.")
                .containsExactly("/prep/accounts.xml");
        assertThat(testData.getExpectedDataFiles())
                .as("getExpectedDataFiles() must return the expected files given"
                        + " to the constructor.")
                .containsExactly("/expected/accounts-after.xml");
    }

    @Test
    void testConstructor_nullArrays_gettersReturnEmptyArrays()
    {
        final PrepAndExpectedTestData testData =
                new PrepAndExpectedTestData(null, null, null);

        assertThat(testData.getVerifyTableDefinitions())
                .as("A null verifyTableDefinitions must normalize to an empty"
                        + " array.")
                .isEmpty();
        assertThat(testData.getPrepDataFiles())
                .as("A null prepDataFiles must normalize to an empty array.")
                .isEmpty();
        assertThat(testData.getExpectedDataFiles())
                .as("A null expectedDataFiles must normalize to an empty array.")
                .isEmpty();
    }

    @Test
    void testConstructor_callerMutatesSourceArrayAfterward_storedValueUnchanged()
    {
        final String[] prepFiles = {"/prep/accounts.xml"};
        final PrepAndExpectedTestData testData =
                new PrepAndExpectedTestData(null, prepFiles, null);

        prepFiles[0] = "/prep/MUTATED.xml";

        assertThat(testData.getPrepDataFiles())
                .as("The constructor must copy the prep files array so a later"
                        + " caller mutation cannot change the stored value.")
                .containsExactly("/prep/accounts.xml");
    }

    @Test
    void testGetPrepDataFiles_callerMutatesReturnedArray_storedValueUnchanged()
    {
        final PrepAndExpectedTestData testData =
                PrepAndExpectedTestData.prepOnly("/prep/accounts.xml");

        testData.getPrepDataFiles()[0] = "/prep/MUTATED.xml";

        assertThat(testData.getPrepDataFiles())
                .as("Each getPrepDataFiles() call must return a fresh copy so"
                        + " mutating one cannot change the stored value.")
                .containsExactly("/prep/accounts.xml");
    }

    @Test
    void testPrepOnly_prepFilesGiven_loadsThemAndVerifiesAndExpectsNothing()
    {
        final PrepAndExpectedTestData testData = PrepAndExpectedTestData
                .prepOnly("/prep/accounts.xml", "/prep/transactions.xml");

        final PrepAndExpectedTestData expected = new PrepAndExpectedTestData(null,
                new String[] {"/prep/accounts.xml", "/prep/transactions.xml"},
                null);
        assertThat(testData)
                .as("prepOnly() must carry the prep files and leave the verify"
                        + " definitions and expected files empty.")
                .isEqualTo(expected);
    }

    @Test
    void testNone_sharedConstant_hasEmptyArraysForAllThree()
    {
        assertThat(PrepAndExpectedTestData.NONE.getVerifyTableDefinitions())
                .as("NONE verifies no tables.").isEmpty();
        assertThat(PrepAndExpectedTestData.NONE.getPrepDataFiles())
                .as("NONE loads no prep data.").isEmpty();
        assertThat(PrepAndExpectedTestData.NONE.getExpectedDataFiles())
                .as("NONE expects no data.").isEmpty();
    }

    @Test
    void testEquals_equivalentContentInSeparateArrayInstances_areEqual()
    {
        final PrepAndExpectedTestData one = new PrepAndExpectedTestData(
                new VerifyTableDefinition[] {ACCOUNT_TABLE},
                new String[] {"/prep/accounts.xml"},
                new String[] {"/expected/accounts-after.xml"});
        final PrepAndExpectedTestData two = new PrepAndExpectedTestData(
                new VerifyTableDefinition[] {ACCOUNT_TABLE},
                new String[] {"/prep/accounts.xml"},
                new String[] {"/expected/accounts-after.xml"});

        assertThat(one)
                .as("Two instances with equal array contents must be equal even"
                        + " though the arrays are separate instances.")
                .isEqualTo(two)
                .hasSameHashCodeAs(two);
    }

    @Test
    void testEquals_differentPrepFiles_areNotEqual()
    {
        final PrepAndExpectedTestData one = new PrepAndExpectedTestData(null,
                new String[] {"/prep/accounts.xml"}, null);
        final PrepAndExpectedTestData two = new PrepAndExpectedTestData(null,
                new String[] {"/prep/transactions.xml"}, null);

        assertThat(one).as("Instances differing in a prep file must not be equal.")
                .isNotEqualTo(two);
    }

    @Test
    void testToString_populated_namesEachOfTheThreeParts()
    {
        final PrepAndExpectedTestData testData = new PrepAndExpectedTestData(
                new VerifyTableDefinition[] {ACCOUNT_TABLE},
                new String[] {"/prep/accounts.xml"},
                new String[] {"/expected/accounts-after.xml"});

        assertThat(testData.toString())
                .as("toString() must name all three parts for a readable test"
                        + " failure message.")
                .contains("ACCOUNT", "/prep/accounts.xml",
                        "/expected/accounts-after.xml");
    }
}
