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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.assertion.DefaultFailureHandler;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.dbunit.util.fileloader.FullXmlDataFileLoader;
import org.junit.jupiter.api.Test;

class MergedDbUnitConfigTest
{
    private static final DbUnitConfig NEAR = declarationOn(Near.class);
    private static final DbUnitConfig FAR = declarationOn(Far.class);
    private static final DbUnitConfig SETTING_NOTHING = declarationOn(SettingNothing.class);

    @Test
    void testGetters_noDeclaration_reportNothingSet()
    {
        final MergedDbUnitConfig merged = MergedDbUnitConfig.of(Collections.emptyList());

        assertThat(merged.getDataFileLoader()).as("No loader is set.").isNull();
        assertThat(merged.getDatabaseTesterFactory()).as("No factory is set.").isNull();
        assertThat(merged.getFailureHandler()).as("No failure handler is set.").isNull();
        assertThat(merged.getPrepAndExpectedTestCase())
                .as("The test case class defaults to DefaultPrepAndExpectedTestCase.")
                .isEqualTo(DefaultPrepAndExpectedTestCase.class);
        assertThat(merged.getDataSetBaseDir()).as("No base directory is set.").isEmpty();
        assertThat(merged.getProperties()).as("No property is set.").isEmpty();
        assertThat(merged.getVerifyDefinitions()).as("No catalog is set.").isEmpty();
    }

    @Test
    void testOfOne_null_isTheSameAsNoDeclaration()
    {
        final MergedDbUnitConfig merged = MergedDbUnitConfig.ofOne(null);

        assertThat(merged.getDataFileLoader()).as("A test with no declaration sets nothing.")
                .isNull();
    }

    @Test
    void testGetters_nearestDeclarationSetsEverything_itsValuesWin()
    {
        final MergedDbUnitConfig merged = MergedDbUnitConfig.of(Arrays.asList(NEAR, FAR));

        assertThat(merged.getDataFileLoader()).isEqualTo(FlatXmlDataFileLoader.class);
        assertThat(merged.getDatabaseTesterFactory()).isEqualTo(NearFactory.class);
        assertThat(merged.getFailureHandler()).isEqualTo(DiffCollectingFailureHandler.class);
        assertThat(merged.getPrepAndExpectedTestCase()).isEqualTo(NearTestCase.class);
        assertThat(merged.getDataSetBaseDir()).isEqualTo("/near");
        assertThat(merged.getVerifyDefinitions()).containsExactly(String.class);
        assertThat(merged.getProperties()).extracting(DbUnitProperty::name)
                .as("An array attribute is taken whole from the nearest declaration that sets it.")
                .containsExactly("batchSize");
    }

    @Test
    void testGetters_nearestDeclarationSetsNothing_theFartherOnesValuesApply()
    {
        final MergedDbUnitConfig merged =
                MergedDbUnitConfig.of(Arrays.asList(SETTING_NOTHING, FAR));

        assertThat(merged.getDataFileLoader()).isEqualTo(FullXmlDataFileLoader.class);
        assertThat(merged.getDatabaseTesterFactory()).isEqualTo(FarFactory.class);
        assertThat(merged.getFailureHandler()).isEqualTo(DefaultFailureHandler.class);
        assertThat(merged.getPrepAndExpectedTestCase()).isEqualTo(FarTestCase.class);
        assertThat(merged.getDataSetBaseDir()).isEqualTo("/far");
        assertThat(merged.getVerifyDefinitions()).containsExactly(Integer.class);
        assertThat(merged.getProperties()).extracting(DbUnitProperty::name)
                .containsExactly("fetchSize");
    }

    @Test
    void testGetters_nearDeclarationSetsOneAttribute_keepsTheOthersFromTheFarOne()
    {
        final MergedDbUnitConfig merged =
                MergedDbUnitConfig.of(Arrays.asList(declarationOn(SetsOnlyTheHandler.class), FAR));

        assertThat(merged.getFailureHandler()).as("The nearer declaration's own value wins.")
                .isEqualTo(DiffCollectingFailureHandler.class);
        assertThat(merged.getDataFileLoader()).as("The loader stays the farther one's.")
                .isEqualTo(FullXmlDataFileLoader.class);
        assertThat(merged.getProperties()).as("So do the properties.")
                .extracting(DbUnitProperty::name).containsExactly("fetchSize");
    }

    @Test
    void testGetProperties_returnsACopy()
    {
        final MergedDbUnitConfig merged = MergedDbUnitConfig.of(Collections.singletonList(NEAR));

        merged.getProperties()[0] = null;

        assertThat(merged.getProperties()[0]).as("Changing the returned array changes nothing.")
                .isNotNull();
    }

    private static DbUnitConfig declarationOn(final Class<?> annotated)
    {
        return annotated.getAnnotation(DbUnitConfig.class);
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            databaseTesterFactory = NearFactory.class,
            failureHandler = DiffCollectingFailureHandler.class,
            prepAndExpectedTestCase = NearTestCase.class, dataSetBaseDir = "/near",
            verifyDefinitions = String.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    private static class Near
    {
    }

    @DbUnitConfig(dataFileLoader = FullXmlDataFileLoader.class,
            databaseTesterFactory = FarFactory.class, failureHandler = DefaultFailureHandler.class,
            prepAndExpectedTestCase = FarTestCase.class, dataSetBaseDir = "/far",
            verifyDefinitions = Integer.class,
            properties = @DbUnitProperty(name = "fetchSize", value = "10"))
    private static class Far
    {
    }

    @DbUnitConfig
    private static class SettingNothing
    {
    }

    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    private static class SetsOnlyTheHandler
    {
    }

    public static class NearFactory implements DatabaseTesterFactory
    {
        @Override
        public IDatabaseTester getDatabaseTester()
        {
            return null;
        }
    }

    public static class FarFactory implements DatabaseTesterFactory
    {
        @Override
        public IDatabaseTester getDatabaseTester()
        {
            return null;
        }
    }

    public static class NearTestCase extends DefaultPrepAndExpectedTestCase
    {
    }

    public static class FarTestCase extends DefaultPrepAndExpectedTestCase
    {
    }
}
