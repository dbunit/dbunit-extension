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
import org.dbunit.database.rowcount.RowCountCheckOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the values a test declares that a {@link DefaultPrepAndExpectedTestCase} can only
 * receive through its own API - the failure handler, the {@code @DbUnitProperty} values and the
 * {@code @DbUnitRowCountCheck} override - in a fixed order.
 *
 * <p>The loader and the close-connection flag reach a test case dbUnit builds through its
 * constructor, so this class does not touch them. An injected {@code @DbUnitTestCase} is used as
 * its owner built it, and {@link InjectedTestCaseDeclarationCheck} rejects what would configure
 * it other than the row count override, so for it this applies that alone. Declared properties
 * are added to the test case's own rather than replacing them. Each value applied is recorded as
 * an undo step, returned as a {@link TestCaseRestoration}, that puts back what the test case had
 * before, so a shared instance carries nothing from one method to the next.
 *
 * <p>Not intended for direct use by test code; machinery consumed by {@link TestCaseLifecycle}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class TestCaseConfigurer
{
    private static final Logger log = LoggerFactory.getLogger(TestCaseConfigurer.class);

    private final AnnotatedTestConfiguration configuration;
    private final DefaultPrepAndExpectedTestCase testCase;

    /**
     * Creates a configurer for one test case.
     *
     * @param configuration The resolved configuration whose declared values to apply.
     * @param testCase The test case to apply them to.
     */
    TestCaseConfigurer(final AnnotatedTestConfiguration configuration,
            final DefaultPrepAndExpectedTestCase testCase)
    {
        this.configuration = configuration;
        this.testCase = testCase;
    }

    /**
     * Applies every declared value to {@link #testCase}, in a fixed order: failure handler,
     * {@code DatabaseConfig} properties, then row count check override.
     *
     * <p>If applying one value fails, the ones already applied are undone before the failure is
     * thrown, so a failed attempt leaves the test case as it was found.
     *
     * @return The restoration that puts back what this applied; undoing it is a no-op when
     *         nothing was applied.
     */
    TestCaseRestoration applyAll()
    {
        final List<Runnable> undoSteps = new ArrayList<>();
        try
        {
            applyFailureHandler(undoSteps);
            applyDatabaseConfigProperties(undoSteps);
            applyRowCountCheckOverride(undoSteps);
        } catch (final RuntimeException failure)
        {
            TestCaseRestoration.undoSuppressing(new TestCaseRestoration(undoSteps), failure);
            throw failure;
        }
        return new TestCaseRestoration(undoSteps);
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
            // Setting the test case's own properties again is what puts back, on a connection it
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
     * Returns whether {@code ownName}, a property name of the test case's own, names a property
     * {@code declared} also holds. The declared names are the long ones, and the test case's own
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

    private void applyRowCountCheckOverride(final List<Runnable> undoSteps)
    {
        if (!configuration.isRowCountCheckDeclared())
        {
            return;
        }

        final RowCountCheckOverride incoming = testCase.getRowCountCheckOverride();
        final RowCountCheckOverride declared = new RowCountCheckOverride(
                configuration.isRowCountCheckEnabled(), configuration.getRowCountCheckExclude());
        testCase.setRowCountCheckOverride(declared);
        undoSteps.add(() -> testCase.setRowCountCheckOverride(incoming));
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
        log.warn("DbUnitConfig declares {} @DbUnitProperty value(s) and the test case class {},"
                + " which @DbUnitConfig(prepAndExpectedTestCase) names,"
                + " overrides setUpDatabaseConfig(DatabaseConfig); those values reach the"
                + " connection only if that override calls super.setUpDatabaseConfig(config)."
                + " Add the super call, or drop properties() and configure"
                + " the DatabaseConfig entirely in the override.", properties.size(),
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
