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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Properties;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCaseSteps;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
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
    void testApplyAll_noAttributesConfigured_stillResetsEverySetterOnAReusedInstance()
    {
        final PrepAndExpectedTestCase testCase = mock(PrepAndExpectedTestCase.class);

        new InjectedTestCaseConfigurer(configFrom(Nothing.class), testCase).applyAll();

        // Every setter runs even when nothing is configured, so a value an earlier test left
        // on a reused instance (e.g. a @DbUnitTestCase static field) is reset.
        verify(testCase).setFailureHandler(isNull());
        verify(testCase).clearRowCountCheckOverride();
        verify(testCase, never()).setRowCountCheckOverride(anyBoolean(), any(String[].class));
        final ArgumentCaptor<Properties> properties = ArgumentCaptor.forClass(Properties.class);
        verify(testCase).setDatabaseConfigProperties(properties.capture());
        assertThat(properties.getValue()).as("No @DbUnitProperty resets to empty.").isEmpty();
    }

    @Test
    void testApplyAll_configuredDataFileLoaderNotOverridden_throwsNamingTheClassAndAttribute()
    {
        final PrepAndExpectedTestCase testCase = new NonOverridingTestCase();

        assertThatThrownBy(() -> new InjectedTestCaseConfigurer(
                configFrom(AllAttributes.class), testCase).applyAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dataFileLoader")
                .hasMessageContaining(NonOverridingTestCase.class.getName());
    }

    @Test
    void testApplyAll_rowCountCheckDeclaredNotOverridden_throws()
    {
        final PrepAndExpectedTestCase testCase = new NonOverridingTestCase();

        assertThatThrownBy(() -> new InjectedTestCaseConfigurer(
                configFrom(OnlyRowCountCheck.class), testCase).applyAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbUnitRowCountCheck");
    }

    @Test
    void testApplyAll_closeConnectionAfterTestFalseNotOverridden_warnsRatherThanThrows()
    {
        final PrepAndExpectedTestCase testCase = new NonOverridingTestCase();
        final ListAppender<ILoggingEvent> appender = attachAppender();
        try
        {
            assertThatCode(() -> new InjectedTestCaseConfigurer(
                    configFrom(OnlyCloseConnectionFalse.class), testCase).applyAll())
                    .as("closeConnectionAfterTest only warns - the executor honors the flag for"
                            + " its own connection regardless.")
                    .doesNotThrowAnyException();
            assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(1);
        } finally
        {
            detachAppender(appender);
        }
    }

    @Test
    void testApplyAll_nothingConfiguredAndNotOverridden_doesNotThrow()
    {
        final PrepAndExpectedTestCase testCase = new NonOverridingTestCase();

        assertThatCode(() -> new InjectedTestCaseConfigurer(
                configFrom(Nothing.class), testCase).applyAll())
                .as("Nothing configured means nothing for a non-overriding instance to fail to"
                        + " apply.")
                .doesNotThrowAnyException();
    }

    @Test
    void testApplyAll_propertiesConfiguredAndSubclassOverridesSetUpDatabaseConfig_warns()
    {
        final PrepAndExpectedTestCase testCase = new SetUpDatabaseConfigOverridingTestCase();
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

    /** Implements {@link PrepAndExpectedTestCase} without overriding any configurer setter. */
    public static class NonOverridingTestCase implements PrepAndExpectedTestCase
    {
        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public void preTest()
        {
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final PrepAndExpectedTestCaseSteps testSteps)
        {
            return null;
        }

        @Override
        public void postTest()
        {
        }

        @Override
        public void postTest(final boolean verifyData)
        {
        }

        @Override
        public void verifyData()
        {
        }

        @Override
        public void cleanupData()
        {
        }

        @Override
        public IDataSet getPrepDataset()
        {
            return null;
        }

        @Override
        public IDataSet getExpectedDataset()
        {
            return null;
        }
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} subclass overriding
     * {@code setUpDatabaseConfig()} - and, so the properties setter itself resolves as
     * overridden rather than tripping its own fail-fast check first,
     * {@code setDatabaseConfigProperties()} too.
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

        @Override
        public void setDatabaseConfigProperties(final Properties databaseConfigProperties)
        {
        }
    }
}
