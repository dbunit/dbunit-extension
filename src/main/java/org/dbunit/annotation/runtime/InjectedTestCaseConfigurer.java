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
import java.util.Properties;
import java.util.function.Supplier;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pushes the {@code @DbUnitConfig}-driven values that reach an injected {@code @DbUnitTestCase}
 * instance only through its own public API - the data file loader, the failure handler, the
 * {@code @DbUnitProperty} values, {@code closeConnectionAfterTest}, and the
 * {@code @DbUnitRowCountCheck} override - onto that instance, in a fixed order.
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
 * <p>Each setter first checks - via
 * {@link DefaultMethodOverrideCheck#overridesDefaultMethod} - that the instance actually
 * overrides it, since a non-overriding instance inherits {@link PrepAndExpectedTestCase}'s
 * no-op default body and the configured value would otherwise silently never take effect. When
 * a declared value cannot land that way, most setters fail fast;
 * {@code setCloseConnectionAfterTest} only warns, because {@link AnnotatedTestExecutor} still
 * honors that flag for its own connection regardless.
 *
 * <p>Not intended for direct use by test code; machinery consumed by {@link ExpectedLifecycle}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class InjectedTestCaseConfigurer
{
    private static final Logger log = LoggerFactory.getLogger(InjectedTestCaseConfigurer.class);

    private final AnnotatedTestConfiguration configuration;
    private final PrepAndExpectedTestCase testCase;

    /**
     * Creates a configurer for one injected test case.
     *
     * @param configuration The resolved configuration whose {@code @DbUnitConfig} values to
     *            apply.
     * @param testCase The already-constructed {@code @DbUnitTestCase} instance to apply them to.
     */
    InjectedTestCaseConfigurer(final AnnotatedTestConfiguration configuration,
            final PrepAndExpectedTestCase testCase)
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
        requireApplicable(declared, false, "setDataFileLoader",
                new Class<?>[] {DataFileLoader.class},
                () -> "DbUnitConfig.dataFileLoader() names "
                        + dataFileLoader.getClass().getName(),
                "it would silently keep loading with whatever loader it was already constructed"
                        + " with. Override setDataFileLoader(), or drop dataFileLoader() from"
                        + " @DbUnitConfig for this test.");
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
        final boolean declared = failureHandler != null;
        requireApplicable(declared, false, "setFailureHandler",
                new Class<?>[] {FailureHandler.class},
                () -> "DbUnitConfig.failureHandler() names "
                        + failureHandler.getClass().getName(),
                "it would silently keep dbUnit's own default handler instead. Override"
                        + " setFailureHandler(), or drop failureHandler() from @DbUnitConfig for"
                        + " this test.");
        if (!declared)
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
        final boolean declared = !properties.isEmpty();
        requireApplicable(declared, false, "setDatabaseConfigProperties",
                new Class<?>[] {Properties.class},
                () -> "DbUnitConfig declares " + properties.size()
                        + " @DbUnitProperty value(s)",
                "this path never reaches the connection any other way - the setup/teardown"
                        + " path's IOperationListener-based application never triggers here."
                        + " Override setDatabaseConfigProperties(), or drop"
                        + " properties()/propertiesProvider() from @DbUnitConfig for this"
                        + " test.");
        if (!declared)
        {
            return;
        }

        warnIfOverridesSetUpDatabaseConfig(properties);
        final Properties incoming = testCase.getDatabaseConfigProperties();
        final Properties merged = new Properties();
        if (incoming != null)
        {
            merged.putAll(incoming);
        }
        merged.putAll(properties);
        testCase.setDatabaseConfigProperties(merged);
        undoSteps.add(() -> testCase.setDatabaseConfigProperties(incoming));
    }

    private void applyCloseConnectionAfterTest(final List<Runnable> undoSteps)
    {
        final boolean declared = !configuration.isCloseConnectionAfterTest();
        requireApplicable(declared, true,
                "setCloseConnectionAfterTest", new Class<?>[] {boolean.class},
                () -> "DbUnitConfig.closeConnectionAfterTest() is false",
                "it may still close a connection this test's tester shares with other tests."
                        + " This executor's own connection - for the row count check or"
                        + " parameter injection - still honors the false value regardless.");
        if (!declared)
        {
            return;
        }

        final boolean incoming = testCase.isCloseConnectionAfterTest();
        testCase.setCloseConnectionAfterTest(false);
        undoSteps.add(() -> testCase.setCloseConnectionAfterTest(incoming));
    }

    private void applyRowCountCheckOverride(final List<Runnable> undoSteps)
    {
        final boolean declared = configuration.isRowCountCheckDeclared();
        requireApplicable(declared, false,
                "setRowCountCheckOverride", new Class<?>[] {boolean.class, String[].class},
                () -> "@DbUnitRowCountCheck is declared",
                "the check would silently never run for this test. Override"
                        + " setRowCountCheckOverride()/clearRowCountCheckOverride(), or drop"
                        + " @DbUnitRowCountCheck for this test.");
        if (!declared)
        {
            return;
        }

        testCase.setRowCountCheckOverride(configuration.isRowCountCheckEnabled(),
                configuration.getRowCountCheckExclude());
        undoSteps.add(testCase::clearRowCountCheckOverride);
    }

    /**
     * Fails fast - or, when {@code warnOnly}, logs a warning - when {@code configured} is true
     * but {@link #testCase}'s runtime type does not override {@code setterName}, inheriting
     * {@link PrepAndExpectedTestCase}'s no-op default body so the configured value would
     * silently never take effect. A no-op when nothing was configured for this setter, or it is
     * genuinely overridden. See {@link DefaultMethodOverrideCheck#overridesDefaultMethod} for
     * this check's own known limitation.
     *
     * @param configured Whether {@code @DbUnitConfig} configured a non-default value for this
     *            setter.
     * @param warnOnly True to log a warning rather than throw - only
     *            {@code setCloseConnectionAfterTest}, which {@link AnnotatedTestExecutor} still
     *            honors for its own connection regardless.
     * @param setterName The setter's name.
     * @param setterParameterTypes The setter's parameter types.
     * @param configuredClause What was configured, e.g.
     *            {@code "DbUnitConfig.failureHandler() names X"}, evaluated only when
     *            {@code configured} - it may read a value that is null otherwise.
     * @param consequence What silently goes wrong when the value cannot land, and how to fix it.
     */
    private void requireApplicable(final boolean configured, final boolean warnOnly,
            final String setterName, final Class<?>[] setterParameterTypes,
            final Supplier<String> configuredClause, final String consequence)
    {
        if (!configured
                || DefaultMethodOverrideCheck.overridesDefaultMethod(testCase.getClass(),
                        PrepAndExpectedTestCase.class, setterName, setterParameterTypes))
        {
            return;
        }
        final String message = configuredClause.get() + ", but the injected DbUnitTestCase "
                + testCase.getClass().getName() + " does not override " + setterName + "(); "
                + consequence;
        if (warnOnly)
        {
            log.warn(message);
        } else
        {
            throw new IllegalStateException(message);
        }
    }

    /**
     * The extra {@code @DbUnitProperty} check {@link #applyDatabaseConfigProperties()} needs on
     * top of the shared not-overridden check: a {@link DefaultPrepAndExpectedTestCase} subclass
     * overriding {@code setUpDatabaseConfig(DatabaseConfig)} - the pre-3.6.0 way to configure a
     * {@link DatabaseConfig} - drops the values just applied unless that override calls
     * {@code super.setUpDatabaseConfig(config)}, since that same hook is where they would
     * otherwise be applied. Reflection cannot tell whether the override calls {@code super},
     * only that one exists, so this warns rather than fails. A no-op for a
     * non-{@link DefaultPrepAndExpectedTestCase} implementation, whose own
     * {@code setDatabaseConfigProperties()} override (already required by
     * {@link #applyDatabaseConfigProperties()}) is its own business.
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
     * Returns whether {@link #testCase} is a {@link DefaultPrepAndExpectedTestCase} subclass
     * that redeclares {@code setUpDatabaseConfig(DatabaseConfig)} somewhere below
     * {@link DefaultPrepAndExpectedTestCase} itself.
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
