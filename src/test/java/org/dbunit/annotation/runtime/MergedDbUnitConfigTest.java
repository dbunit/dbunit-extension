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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
                .as("The properties merge by name: those of the nearest declaration first, then"
                        + " the others of the farther one.")
                .containsExactly("batchSize", "fetchSize");
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
    void testGetProperties_nearerDeclarationNamesOtherProperty_keepsInheritedOnes()
    {
        final MergedDbUnitConfig merged =
                MergedDbUnitConfig.of(Arrays.asList(declarationOn(NamesBatchSize.class), FAR));

        assertThat(merged.getProperties())
                .extracting(DbUnitProperty::name, DbUnitProperty::value)
                .as("A nearer declaration that names one property keeps the inherited ones.")
                .containsExactly(tuple("batchSize", "500"), tuple("fetchSize", "10"));
    }

    @Test
    void testGetProperties_nearerDeclarationNamesSameProperty_nearerValueWins()
    {
        final MergedDbUnitConfig merged = MergedDbUnitConfig.of(Arrays.asList(
                declarationOn(NamesBatchSize.class),
                declarationOn(NamesBatchSizeAndFetchSize.class)));

        assertThat(merged.getProperties())
                .extracting(DbUnitProperty::name, DbUnitProperty::value)
                .as("The nearer value of a property both name wins, and the farther one's other"
                        + " property stays.")
                .containsExactly(tuple("batchSize", "500"), tuple("fetchSize", "10"));
    }

    @Test
    void testGetProperties_shortAndLongNameInDifferentDeclarations_nearerWins()
    {
        final DbUnitConfig shortName = declarationOn(NamesBatchSize.class);
        final DbUnitConfig longName = declarationOn(NamesBatchSizeByItsLongName.class);

        final MergedDbUnitConfig shortNameNearer =
                MergedDbUnitConfig.of(Arrays.asList(shortName, longName));
        final MergedDbUnitConfig longNameNearer =
                MergedDbUnitConfig.of(Arrays.asList(longName, shortName));

        assertThat(shortNameNearer.getProperties())
                .extracting(DbUnitProperty::name, DbUnitProperty::value)
                .as("The long and the short name of a property name the same one, so the nearer"
                        + " declaration's wins whichever form it uses.")
                .containsExactly(tuple("batchSize", "500"));
        assertThat(longNameNearer.getProperties())
                .extracting(DbUnitProperty::name, DbUnitProperty::value)
                .containsExactly(tuple("http://www.dbunit.org/properties/batchSize", "50"));
    }

    @Test
    void testGetProperties_propertyNamedTwiceInOneDeclaration_staysForTheResolverToReject()
    {
        final MergedDbUnitConfig asNearest = MergedDbUnitConfig
                .of(Arrays.asList(declarationOn(NamesBatchSizeInBothForms.class), FAR));
        final MergedDbUnitConfig asFarthest = MergedDbUnitConfig
                .of(Arrays.asList(FAR, declarationOn(NamesBatchSizeInBothForms.class)));

        assertThatThrownBy(() -> new DatabaseConfigPropertiesResolver()
                .resolve(asNearest.getProperties()))
                        .as("A property named twice in one declaration still fails.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("more than one");
        assertThatThrownBy(() -> new DatabaseConfigPropertiesResolver()
                .resolve(asFarthest.getProperties()))
                        .as("It fails as a farther declaration's too, as no nearer one names it.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("more than one");
    }

    @Test
    void testGetProperties_unknownPropertyName_staysForTheResolverToReject()
    {
        final MergedDbUnitConfig merged = MergedDbUnitConfig.of(Arrays.asList(
                declarationOn(NamesBatchSize.class),
                declarationOn(NamesAnUnknownProperty.class)));

        assertThatThrownBy(() -> new DatabaseConfigPropertiesResolver()
                .resolve(merged.getProperties()))
                        .as("A name no DatabaseConfig property has is no property to merge with,"
                                + " so it reaches the resolver, which rejects it.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("notAProperty");
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

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "500"))
    private static class NamesBatchSize
    {
    }

    @DbUnitConfig(properties = {@DbUnitProperty(name = "batchSize", value = "50"),
            @DbUnitProperty(name = "fetchSize", value = "10")})
    private static class NamesBatchSizeAndFetchSize
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "http://www.dbunit.org/properties/batchSize",
            value = "50"))
    private static class NamesBatchSizeByItsLongName
    {
    }

    @DbUnitConfig(properties = {@DbUnitProperty(name = "batchSize", value = "1"),
            @DbUnitProperty(name = "http://www.dbunit.org/properties/batchSize", value = "2")})
    private static class NamesBatchSizeInBothForms
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "notAProperty", value = "x"))
    private static class NamesAnUnknownProperty
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
