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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConfig.ConfigProperty;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pushes the {@code @DbUnitConfig}-driven values that reach an injected {@code @DbUnitTestCase}
 * instance only through its own public API - the data file loader, the failure handler, the
 * {@code @DbUnitProperty} values, {@code closeConnectionAfterTest}, and the
 * {@code @DbUnitRowCountCheck} override - onto that {@link DefaultPrepAndExpectedTestCase}, in a
 * fixed order.
 *
 * <p>Only a value the test actually declares is pushed: an instance built with its own loader,
 * failure handler, properties or {@code closeConnectionAfterTest=false} keeps them when
 * {@code @DbUnitConfig} says nothing about them. The one exception is a missing data file
 * loader, which an instance cannot load datasets without, so it is given the configured one.
 * Declared properties are added to the instance's own rather than replacing them.
 * {@code closeConnectionAfterTest} is declared only by setting it to {@code false}, since the
 * default {@code true} is indistinguishable from not writing it. Each value pushed is recorded
 * as an undo step, returned as an {@link InjectedTestCaseRestoration}, so a shared instance
 * carries nothing from one method to the next.
 *
 * <p>Not intended for direct use by test code; machinery consumed by {@link ExpectedLifecycle}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class InjectedTestCaseConfigurer
{
    private static final Logger log = LoggerFactory.getLogger(InjectedTestCaseConfigurer.class);

    private final AnnotatedTestConfiguration configuration;
    private final DefaultPrepAndExpectedTestCase testCase;

    /**
     * Creates a configurer for one injected test case.
     *
     * @param configuration The resolved configuration whose {@code @DbUnitConfig} values to
     *            apply.
     * @param testCase The already-constructed {@code @DbUnitTestCase} instance to apply them to.
     */
    InjectedTestCaseConfigurer(final AnnotatedTestConfiguration configuration,
            final DefaultPrepAndExpectedTestCase testCase)
    {
        this.configuration = configuration;
        this.testCase = testCase;
    }

    /**
     * Applies every declared {@code @DbUnitConfig}-driven value to {@link #testCase}, in a fixed
     * order: data file loader, failure handler, {@code DatabaseConfig} properties,
     * close-connection flag, then row count check override.
     *
     * <p>If applying one value fails, the ones already applied are undone before the failure is
     * thrown, so a failed attempt leaves the instance as it was found.
     *
     * @return The restoration that puts back what this applied; undoing it is a no-op when
     *         nothing was applied.
     */
    InjectedTestCaseRestoration applyAll()
    {
        final List<Runnable> undoSteps = new ArrayList<>();
        try
        {
            applyDataFileLoader(undoSteps);
            applyFailureHandler(undoSteps);
            applyDatabaseConfigProperties(undoSteps);
            applyCloseConnectionAfterTest(undoSteps);
            applyRowCountCheckOverride(undoSteps);
        } catch (final RuntimeException failure)
        {
            InjectedTestCaseRestoration.undoSuppressing(
                    new InjectedTestCaseRestoration(undoSteps), failure);
            throw failure;
        }
        return new InjectedTestCaseRestoration(undoSteps);
    }

    private void applyDataFileLoader(final List<Runnable> undoSteps)
    {
        final DataFileLoader dataFileLoader = configuration.getDataFileLoader();
        final boolean declared = dataFileLoader.getClass() != FileExtensionDataFileLoader.class;
        final DataFileLoader incoming = testCase.getDataFileLoader();
        if (!declared && incoming != null)
        {
            return;
        }

        testCase.setDataFileLoader(dataFileLoader);
        undoSteps.add(() -> testCase.setDataFileLoader(incoming));
    }

    private void applyFailureHandler(final List<Runnable> undoSteps)
    {
        final FailureHandler failureHandler = configuration.getFailureHandler();
        if (failureHandler == null)
        {
            return;
        }

        final FailureHandler incoming = testCase.getFailureHandler();
        testCase.setFailureHandler(failureHandler);
        undoSteps.add(() -> testCase.setFailureHandler(incoming));
    }

    private void applyDatabaseConfigProperties(final List<Runnable> undoSteps)
    {
        final Properties properties = configuration.getDatabaseConfigProperties();
        final Properties incoming = testCase.getDatabaseConfigProperties();
        if (properties.isEmpty())
        {
            // Setting the instance's own properties again is what puts back, on a connection it
            // kept, the DatabaseConfig values it applied from them, when its lifecycle is
            // abandoned before cleanupData() runs.
            undoSteps.add(() -> testCase.setDatabaseConfigProperties(incoming));
            return;
        }

        warnIfOverridesSetUpDatabaseConfig(properties);
        final Properties merged = new Properties();
        if (incoming != null)
        {
            for (final Map.Entry<Object, Object> own : incoming.entrySet())
            {
                if (!isReplacedByDeclared(own.getKey(), properties))
                {
                    merged.put(own.getKey(), own.getValue());
                }
            }
        }
        merged.putAll(properties);
        testCase.setDatabaseConfigProperties(merged);
        undoSteps.add(() -> testCase.setDatabaseConfigProperties(incoming));
    }

    /**
     * Returns whether {@code ownName}, a property name of the instance's own, names a property
     * {@code declared} also holds. The declared names are the long ones, and the instance's own
     * may be spelled in the short form, so leaving both in would have which value wins depend
     * on the order a {@link Properties} happens to be iterated in.
     */
    private static boolean isReplacedByDeclared(final Object ownName, final Properties declared)
    {
        if (!(ownName instanceof String))
        {
            return false;
        }
        final ConfigProperty known = DatabaseConfig.findByNameOrShortName((String) ownName);
        return known != null && declared.containsKey(known.getProperty());
    }

    private void applyCloseConnectionAfterTest(final List<Runnable> undoSteps)
    {
        if (configuration.isCloseConnectionAfterTest())
        {
            return;
        }

        final boolean incoming = testCase.isCloseConnectionAfterTest();
        testCase.setCloseConnectionAfterTest(false);
        undoSteps.add(() -> testCase.setCloseConnectionAfterTest(incoming));
    }

    private void applyRowCountCheckOverride(final List<Runnable> undoSteps)
    {
        if (!configuration.isRowCountCheckDeclared())
        {
            return;
        }

        testCase.setRowCountCheckOverride(configuration.isRowCountCheckEnabled(),
                configuration.getRowCountCheckExclude());
        undoSteps.add(testCase::clearRowCountCheckOverride);
    }

    /**
     * Warns when {@link #testCase} is a {@link DefaultPrepAndExpectedTestCase} subclass
     * overriding {@code setUpDatabaseConfig(DatabaseConfig)}, the pre-3.6.0 way to configure a
     * {@link DatabaseConfig}: that override drops the values just applied unless it calls
     * {@code super.setUpDatabaseConfig(config)}, since that same hook is where they would
     * otherwise be applied. Reflection cannot tell whether the override calls {@code super},
     * only that one exists, so this warns rather than fails.
     *
     * @param properties The resolved {@code @DbUnitProperty} values; a no-op when empty.
     */
    private void warnIfOverridesSetUpDatabaseConfig(final Properties properties)
    {
        if (properties.isEmpty() || !overridesSetUpDatabaseConfig())
        {
            return;
        }
        log.warn("DbUnitConfig declares {} @DbUnitProperty value(s) and the injected"
                + " DbUnitTestCase {} overrides setUpDatabaseConfig(DatabaseConfig); those"
                + " values reach the connection only if that override calls"
                + " super.setUpDatabaseConfig(config). Add the super call, or drop"
                + " properties()/propertiesProvider() and configure the DatabaseConfig"
                + " entirely in the override.", properties.size(),
                testCase.getClass().getName());
    }

    /**
     * Returns whether {@link #testCase} redeclares {@code setUpDatabaseConfig(DatabaseConfig)}
     * somewhere below {@link DefaultPrepAndExpectedTestCase} itself.
     *
     * @return True when a {@link DefaultPrepAndExpectedTestCase} subclass overrides
     *         {@code setUpDatabaseConfig(DatabaseConfig)}.
     */
    private boolean overridesSetUpDatabaseConfig()
    {
        return DefaultMethodOverrideCheck.declaresBelow(testCase.getClass(),
                DefaultPrepAndExpectedTestCase.class, "setUpDatabaseConfig",
                DatabaseConfig.class);
    }
}
