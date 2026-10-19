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
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;
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
class InjectedTestCaseConfigurerTest
{
    @Test
    void testApplyAll_overridingInstance_appliesEveryConfiguredValue()
    {
        final DefaultPrepAndExpectedTestCase testCase =
                mock(DefaultPrepAndExpectedTestCase.class);

        new InjectedTestCaseConfigurer(configFrom(AllAttributes.class), testCase).applyAll();

        verify(testCase).setDataFileLoader(any(FlatXmlDataFileLoader.class));
        verify(testCase).setFailureHandler(any(DiffCollectingFailureHandler.class));
        verify(testCase).setDatabaseConfigProperties(any(Properties.class));
        verify(testCase).setRowCountCheckOverride(true, new String[0]);
    }

    @Test
    void testApplyAll_noAttributesConfigured_touchesNoSetterExceptTheMissingLoader()
    {
        // A DefaultPrepAndExpectedTestCase mock, so the getters count as overridden and the
        // missing loader can be seen.
        final DefaultPrepAndExpectedTestCase testCase = mock(DefaultPrepAndExpectedTestCase.class);

        new InjectedTestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        // Nothing declared means nothing to change on an instance that already has its own
        // settings; only a missing loader is filled in, since the instance cannot load without one.
        verify(testCase).setDataFileLoader(any(FileExtensionDataFileLoader.class));
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
        final InjectedTestCaseRestoration restoration =
                new InjectedTestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();
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

        new InjectedTestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        assertThat(testCase.getDataFileLoader())
                .as("A loader built into the instance stands when @DbUnitConfig names none.")
                .isSameAs(ownLoader);
        assertThat(testCase.getFailureHandler())
                .as("A failure handler set on the instance stands when none is configured.")
                .isSameAs(ownHandler);
        assertThat(testCase.isCloseConnectionAfterTest())
                .as("closeConnectionAfterTest=false built into the instance stands, because the"
                        + " default true is not a request to change it.")
                .isFalse();
        assertThat(testCase.getDatabaseConfigProperties())
                .as("Properties set on the instance stand when none are configured.")
                .isEqualTo(ownProperties);
    }

    @Test
    void testApplyAll_valuesDeclared_applyThemOverTheInstancesOwnAndMergeProperties()
    {
        final DefaultPrepAndExpectedTestCase testCase = newInstanceWithOwnSettings();

        new InjectedTestCaseConfigurer(configFrom(EveryDeclaredAttribute.class), testCase)
                .applyAll();

        assertThat(testCase.getDataFileLoader()).as("The declared loader replaces the instance's.")
                .isInstanceOf(FlatXmlDataFileLoader.class);
        assertThat(testCase.getFailureHandler())
                .as("The declared failure handler replaces the instance's.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
        assertThat(testCase.isCloseConnectionAfterTest())
                .as("A declared closeConnectionAfterTest=false replaces the instance's true.")
                .isFalse();
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

        new InjectedTestCaseConfigurer(configFrom(OnlyProperties.class), testCase).applyAll();

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
        final DataFileLoader ownLoader = testCase.getDataFileLoader();
        final FailureHandler ownHandler = testCase.getFailureHandler();
        final Properties ownProperties = testCase.getDatabaseConfigProperties();
        final InjectedTestCaseRestoration restoration = new InjectedTestCaseConfigurer(
                configFrom(EveryDeclaredAttribute.class), testCase).applyAll();

        restoration.undo();

        assertThat(testCase.getDataFileLoader()).as("The loader is put back.")
                .isSameAs(ownLoader);
        assertThat(testCase.getFailureHandler()).as("The failure handler is put back.")
                .isSameAs(ownHandler);
        assertThat(testCase.isCloseConnectionAfterTest())
                .as("closeConnectionAfterTest is put back.").isTrue();
        assertThat(testCase.getDatabaseConfigProperties()).as("The properties are put back.")
                .isEqualTo(ownProperties);
    }

    @Test
    void testApplyAll_reusedInstanceWhoseFirstTestDeclaredValues_secondTestSeesItsOwn()
    {
        final DefaultPrepAndExpectedTestCase testCase = newInstanceWithOwnSettings();
        final DataFileLoader ownLoader = testCase.getDataFileLoader();
        final FailureHandler ownHandler = testCase.getFailureHandler();
        new InjectedTestCaseConfigurer(configFrom(EveryDeclaredAttribute.class), testCase)
                .applyAll().undo();

        new InjectedTestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        assertThat(testCase.getDataFileLoader())
                .as("A second test declaring nothing sees the instance's own loader, not the"
                        + " first test's.")
                .isSameAs(ownLoader);
        assertThat(testCase.getFailureHandler())
                .as("A second test declaring nothing sees the instance's own failure handler.")
                .isSameAs(ownHandler);
        assertThat(testCase.isCloseConnectionAfterTest())
                .as("A second test declaring nothing sees the instance's own"
                        + " closeConnectionAfterTest.")
                .isTrue();
    }

    @Test
    void testApplyAll_instanceHasNoDataFileLoader_givesItTheConfiguredOneAndUndoRemovesIt()
    {
        final DefaultPrepAndExpectedTestCase testCase = new DefaultPrepAndExpectedTestCase();

        final InjectedTestCaseRestoration restoration =
                new InjectedTestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        assertThat(testCase.getDataFileLoader())
                .as("An instance with no loader cannot load datasets, so it gets the configured"
                        + " one.")
                .isInstanceOf(FileExtensionDataFileLoader.class);

        restoration.undo();

        assertThat(testCase.getDataFileLoader()).as("The instance is left as it was found.")
                .isNull();
    }

    @Test
    void testApplyAll_laterAttributeFails_undoesTheEarlierOnesBeforeThrowing()
    {
        final DataFileLoader ownLoader = new FullXmlDataFileLoader();
        final DefaultPrepAndExpectedTestCase testCase =
                spy(new DefaultPrepAndExpectedTestCase(ownLoader, null, true));
        final IllegalStateException failure = new IllegalStateException("handler rejected");
        doThrow(failure).when(testCase).setFailureHandler(any());

        assertThatThrownBy(() -> new InjectedTestCaseConfigurer(
                configFrom(LoaderAndFailureHandler.class), testCase).applyAll())
                .as("The failure of a later value is the one reported.")
                .isSameAs(failure);

        assertThat(testCase.getDataFileLoader())
                .as("The loader applied before the failure must not stay behind on the instance.")
                .isSameAs(ownLoader);
    }

    @Test
    void testApplyAll_rowCountCheckDeclared_setsTheOverrideAndUndoClearsIt()
    {
        final DefaultPrepAndExpectedTestCase testCase =
                spy(new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), null, true));

        final InjectedTestCaseRestoration restoration = new InjectedTestCaseConfigurer(
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
            new InjectedTestCaseConfigurer(configFrom(OnlyProperties.class), testCase).applyAll();

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
                (Logger) LoggerFactory.getLogger(InjectedTestCaseConfigurer.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(final ListAppender<ILoggingEvent> appender)
    {
        ((Logger) LoggerFactory.getLogger(InjectedTestCaseConfigurer.class))
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

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            failureHandler = DiffCollectingFailureHandler.class,
            closeConnectionAfterTest = false,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    private static class EveryDeclaredAttribute
    {
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            failureHandler = DiffCollectingFailureHandler.class)
    private static class LoaderAndFailureHandler
    {
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            failureHandler = DiffCollectingFailureHandler.class,
            closeConnectionAfterTest = false)
    private static class LoaderFailureHandlerAndCloseFalse
    {
    }

    @DbUnitRowCountCheck
    private static class OnlyRowCountCheck
    {
    }

    @DbUnitConfig(closeConnectionAfterTest = false)
    private static class OnlyCloseConnectionFalse
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
