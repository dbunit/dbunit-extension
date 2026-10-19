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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Properties;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.assertion.DefaultFailureHandler;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.dbunit.util.fileloader.FullXmlDataFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
@ClearRowCountCheckSystemProperties
class TestCaseConfigurerTest
{
    @Test
    void testApplyAll_overridingInstance_appliesEveryConfiguredValue()
    {
        final DefaultPrepAndExpectedTestCase testCase =
                mock(DefaultPrepAndExpectedTestCase.class);

        new TestCaseConfigurer(configFrom(AllAttributes.class), testCase).applyAll();

        verify(testCase).setFailureHandler(any(DiffCollectingFailureHandler.class));
        verify(testCase).setDatabaseConfigProperties(any(Properties.class));
        verify(testCase).setRowCountCheckOverride(true, new String[0]);
    }

    @Test
    void testApplyAll_noAttributesConfigured_changesNothingOnTheTestCase()
    {
        final DefaultPrepAndExpectedTestCase testCase = mock(DefaultPrepAndExpectedTestCase.class);

        new TestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        // Nothing declared means nothing to change on a test case that has its own settings,
        // the loader and the close flag included: a built one gets them through its constructor.
        verify(testCase, never()).setDataFileLoader(any());
        verify(testCase, never()).setFailureHandler(any());
        verify(testCase, never()).setDatabaseConfigProperties(any());
        verify(testCase, never()).setCloseConnectionAfterTest(anyBoolean());
        verify(testCase, never()).setRowCountCheckOverride(anyBoolean(), any(String[].class));
        verify(testCase, never()).clearRowCountCheckOverride();
    }

    @Test
    void testUndo_nothingDeclaredOnInstanceWithItsOwnProperties_setsItsPropertiesAgainToPutBackWhatItApplied()
    {
        final DefaultPrepAndExpectedTestCase testCase =
                spy(new DefaultPrepAndExpectedTestCase(new FullXmlDataFileLoader(), null, false));
        final Properties ownProperties = new Properties();
        ownProperties.setProperty("batchSize", "7");
        testCase.setDatabaseConfigProperties(ownProperties);
        final TestCaseRestoration restoration =
                new TestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();
        verify(testCase, times(1)).setDatabaseConfigProperties(any());

        restoration.undo();

        final ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
        verify(testCase, times(2)).setDatabaseConfigProperties(captor.capture());
        assertThat(captor.getValue())
                .as("Setting the instance's own properties again is what makes it put back the"
                        + " DatabaseConfig values they applied to a connection it kept, which a"
                        + " lifecycle abandoned before cleanupData() would otherwise leave.")
                .isEqualTo(ownProperties);
    }

    @Test
    void testApplyAll_nothingConfiguredOnInstanceWithItsOwnSettings_leavesThemUntouched()
    {
        final DataFileLoader ownLoader = new FullXmlDataFileLoader();
        final DefaultPrepAndExpectedTestCase testCase =
                new DefaultPrepAndExpectedTestCase(ownLoader, null, false);
        final FailureHandler ownHandler = new DiffCollectingFailureHandler();
        testCase.setFailureHandler(ownHandler);
        final Properties ownProperties = new Properties();
        ownProperties.setProperty("batchSize", "7");
        testCase.setDatabaseConfigProperties(ownProperties);

        new TestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        assertThat(testCase.getDataFileLoader())
                .as("A loader built into the instance stands when @DbUnitConfig names none.")
                .isSameAs(ownLoader);
        assertThat(testCase.getFailureHandler())
                .as("A failure handler set on the instance stands when none is configured.")
                .isSameAs(ownHandler);
        assertThat(testCase.isCloseConnectionAfterTest())
                .as("closeConnectionAfterTest=false built into the instance stands; no"
                        + " annotation sets it.")
                .isFalse();
        assertThat(testCase.getDatabaseConfigProperties())
                .as("Properties set on the instance stand when none are configured.")
                .isEqualTo(ownProperties);
    }

    @Test
    void testApplyAll_valuesDeclared_applyThemOverTheInstancesOwnAndMergeProperties()
    {
        final DefaultPrepAndExpectedTestCase testCase = newInstanceWithOwnSettings();

        new TestCaseConfigurer(configFrom(FailureHandlerAndProperties.class), testCase)
                .applyAll();

        assertThat(testCase.getFailureHandler())
                .as("The declared failure handler replaces the instance's.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
        final Properties expected = new Properties();
        expected.setProperty("fetchSize", "9");
        expected.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, "50");
        assertThat(testCase.getDatabaseConfigProperties())
                .as("Declared properties are added to the instance's own, not substituted for"
                        + " them.")
                .isEqualTo(expected);
    }

    @Test
    void testApplyAll_instanceOwnsTheDeclaredPropertyUnderItsShortName_declaredValueReplacesIt()
    {
        final DefaultPrepAndExpectedTestCase testCase = newInstanceWithOwnSettings();
        final Properties ownProperties = testCase.getDatabaseConfigProperties();
        ownProperties.setProperty("batchSize", "7");
        testCase.setDatabaseConfigProperties(ownProperties);

        new TestCaseConfigurer(configFrom(OnlyProperties.class), testCase).applyAll();

        final Properties expected = new Properties();
        expected.setProperty("fetchSize", "9");
        expected.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, "50");
        assertThat(testCase.getDatabaseConfigProperties())
                .as("The declared batch size replaces the instance's own, whichever form of the"
                        + " name each is spelled in; both left in, which one wins would depend on"
                        + " the order a Properties happens to be iterated in.")
                .isEqualTo(expected);
    }

    @Test
    void testUndo_afterDeclaredValuesApplied_putsTheInstancesOwnBack()
    {
        final DefaultPrepAndExpectedTestCase testCase = newInstanceWithOwnSettings();
        final FailureHandler ownHandler = testCase.getFailureHandler();
        final Properties ownProperties = testCase.getDatabaseConfigProperties();
        final TestCaseRestoration restoration = new TestCaseConfigurer(
                configFrom(FailureHandlerAndProperties.class), testCase).applyAll();

        restoration.undo();

        assertThat(testCase.getFailureHandler()).as("The failure handler is put back.")
                .isSameAs(ownHandler);
        assertThat(testCase.getDatabaseConfigProperties()).as("The properties are put back.")
                .isEqualTo(ownProperties);
    }

    @Test
    void testApplyAll_reusedInstanceWhoseFirstTestDeclaredValues_secondTestSeesItsOwn()
    {
        final DefaultPrepAndExpectedTestCase testCase = newInstanceWithOwnSettings();
        final FailureHandler ownHandler = testCase.getFailureHandler();
        new TestCaseConfigurer(configFrom(FailureHandlerAndProperties.class), testCase)
                .applyAll().undo();

        new TestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        assertThat(testCase.getFailureHandler())
                .as("A second test declaring nothing sees the instance's own failure handler,"
                        + " not the first test's.")
                .isSameAs(ownHandler);
    }

    @Test
    void testApplyAll_laterAttributeFails_undoesTheEarlierOnesBeforeThrowing()
    {
        final FailureHandler ownHandler = new DefaultFailureHandler();
        final DefaultPrepAndExpectedTestCase testCase = spy(new DefaultPrepAndExpectedTestCase(
                new FullXmlDataFileLoader(), null, true));
        testCase.setFailureHandler(ownHandler);
        final IllegalStateException failure = new IllegalStateException("properties rejected");
        doThrow(failure).when(testCase).setDatabaseConfigProperties(
                argThat(properties -> properties != null && !properties.isEmpty()));

        assertThatThrownBy(() -> new TestCaseConfigurer(
                configFrom(FailureHandlerAndProperties.class), testCase).applyAll())
                .as("The failure of a later value is the one reported.")
                .isSameAs(failure);

        assertThat(testCase.getFailureHandler())
                .as("The failure handler applied before the failure must not stay behind on the"
                        + " instance.")
                .isSameAs(ownHandler);
    }

    @Test
    void testApplyAll_rowCountCheckDeclared_setsTheOverrideAndUndoClearsIt()
    {
        final DefaultPrepAndExpectedTestCase testCase =
                spy(new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), null, true));

        final TestCaseRestoration restoration = new TestCaseConfigurer(
                configFrom(OnlyRowCountCheck.class), testCase).applyAll();

        verify(testCase).setRowCountCheckOverride(true, new String[0]);
        verify(testCase, never()).clearRowCountCheckOverride();

        restoration.undo();

        verify(testCase).clearRowCountCheckOverride();
    }

    @Test
    void testApplyAll_propertiesConfiguredAndSubclassOverridesSetUpDatabaseConfig_warns()
    {
        final DefaultPrepAndExpectedTestCase testCase = new SetUpDatabaseConfigOverridingTestCase();
        final ListAppender<ILoggingEvent> appender = attachAppender();
        try
        {
            new TestCaseConfigurer(configFrom(OnlyProperties.class), testCase).applyAll();

            assertThat(appender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .as("An overriding setUpDatabaseConfig() might not call super, which is"
                            + " where @DbUnitProperty values would otherwise land.")
                    .hasSize(1)
                    .allSatisfy(e -> assertThat(e.getFormattedMessage())
                            .contains("setUpDatabaseConfig").contains("super"));
        } finally
        {
            detachAppender(appender);
        }
    }

    @Test
    void testApplyAll_propertiesConfiguredAndSubclassOverridesSetUpDatabaseConfig_warningNamesTheClassTheAttributeNames()
    {
        final DefaultPrepAndExpectedTestCase testCase = new SetUpDatabaseConfigOverridingTestCase();
        final ListAppender<ILoggingEvent> appender = attachAppender();
        try
        {
            new TestCaseConfigurer(configFrom(OnlyProperties.class), testCase).applyAll();

            assertThat(appender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .as("dbUnit builds the test case from @DbUnitConfig(prepAndExpectedTestCase),"
                            + " as properties() next to an injected @DbUnitTestCase is rejected,"
                            + " so the warning names the class that attribute names.")
                    .hasSize(1)
                    .allSatisfy(e -> assertThat(e.getFormattedMessage())
                            .contains("the test case class " + testCase.getClass().getName()
                                    + ", which @DbUnitConfig(prepAndExpectedTestCase) names,")
                            .doesNotContain("DbUnitTestCase"));
        } finally
        {
            detachAppender(appender);
        }
    }

    // ---- helpers ----

    private static DefaultPrepAndExpectedTestCase newInstanceWithOwnSettings()
    {
        final DefaultPrepAndExpectedTestCase testCase = new DefaultPrepAndExpectedTestCase(
                new FullXmlDataFileLoader(), null, true);
        testCase.setFailureHandler(new DiffCollectingFailureHandler());
        final Properties ownProperties = new Properties();
        ownProperties.setProperty("fetchSize", "9");
        testCase.setDatabaseConfigProperties(ownProperties);
        return testCase;
    }

    private static ListAppender<ILoggingEvent> attachAppender()
    {
        final Logger logger =
                (Logger) LoggerFactory.getLogger(TestCaseConfigurer.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(final ListAppender<ILoggingEvent> appender)
    {
        ((Logger) LoggerFactory.getLogger(TestCaseConfigurer.class))
                .detachAppender(appender);
    }

    private static AnnotatedTestConfiguration configFrom(final Class<?> fixture)
    {
        return AnnotatedTestConfiguration.from(fixture,
                fixture.getAnnotation(DbUnitConfig.class), null, null, null, null,
                fixture.getAnnotation(DbUnitRowCountCheck.class));
    }

    // ---- fixtures ----

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            failureHandler = DiffCollectingFailureHandler.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitRowCountCheck
    private static class AllAttributes
    {
    }

    private static class Nothing
    {
    }

    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    private static class FailureHandlerAndProperties
    {
    }

    @DbUnitRowCountCheck
    private static class OnlyRowCountCheck
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    private static class OnlyProperties
    {
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} subclass overriding
     * {@code setUpDatabaseConfig()}.
     */
    public static class SetUpDatabaseConfigOverridingTestCase
            extends DefaultPrepAndExpectedTestCase
    {
        public SetUpDatabaseConfigOverridingTestCase()
        {
            super(new FlatXmlDataFileLoader(), null, true);
        }

        @Override
        protected void setUpDatabaseConfig(final DatabaseConfig config)
        {
            // deliberately does not call super
        }
    }
}
