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
package org.dbunit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConfigPropertiesProvider;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;

/**
 * Class-level wiring for annotation-driven dbUnit test configuration: which
 * {@link DataFileLoader} loads dataset files, how to obtain a tester via
 * {@link DatabaseTesterFactory} when no field supplies one, which
 * {@link PrepAndExpectedTestCase} implementation drives the prep/expected path,
 * {@link DatabaseConfig} properties, and the shared table-definition catalog.
 *
 * <p>For configuration shared across many test classes, compose one custom annotation
 * carrying {@code @DbUnitTest}, this annotation, and the lifecycle annotations, rather than
 * repeating them on every class:
 * <pre>{@code
 * @Retention(RetentionPolicy.RUNTIME)
 * @Target(ElementType.TYPE)
 * @Inherited
 * @DbUnitTest
 * @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
 * @DbUnitSetup(operation = DbUnitOperation.REFRESH)
 * public @interface AppDatabaseTest {}
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitProperty
 * @see DatabaseConfigPropertiesProvider
 * @see org.dbunit.VerifyTableDefinitionsProvider
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitConfig {
    /**
     * The {@link DataFileLoader} implementation used to load every {@link DbUnitPrep} and
     * {@link DbUnitExpected} dataset file, reflectively instantiated with its no-arg
     * constructor.
     *
     * <p>On the prep/expected path ({@link DbUnitExpected} declared), a {@link DbUnitTestCase}
     * field injecting an already-built instance also receives this value, when it resolves to
     * a {@link DefaultPrepAndExpectedTestCase} - the only {@link PrepAndExpectedTestCase}
     * implementation exposing a setter for it - the same as a freshly-constructed instance,
     * which always receives it through its constructor regardless of implementation.
     *
     * @return The loader class; defaults to {@link FileExtensionDataFileLoader}.
     */
    Class<? extends DataFileLoader> dataFileLoader() default FileExtensionDataFileLoader.class;

    /**
     * A {@link DatabaseTesterFactory} implementation to reflectively instantiate (with its
     * no-arg constructor) and ask to create the {@code IDatabaseTester} to use, when neither a
     * {@link DbUnitTestCase} nor a {@link DbUnitTester} field is declared.
     *
     * @return The factory class; the interface itself (the default) means "not set", falling
     *         through to the plain field auto-scan.
     */
    Class<? extends DatabaseTesterFactory> databaseTesterFactory() default DatabaseTesterFactory.class;

    /**
     * The {@link PrepAndExpectedTestCase} implementation to reflectively instantiate for the
     * prep/expected path when no {@link DbUnitTestCase} field supplies one already, via a
     * constructor accepting {@code (DataFileLoader, IDatabaseTester, boolean)} - the same
     * shape {@link DefaultPrepAndExpectedTestCase} itself has.
     *
     * @return The test case class; defaults to {@link DefaultPrepAndExpectedTestCase}.
     */
    Class<? extends PrepAndExpectedTestCase> prepAndExpectedTestCase() default DefaultPrepAndExpectedTestCase.class;

    /**
     * Inline {@link DatabaseConfig} property name/value pairs to apply.
     *
     * <p>Mutually exclusive with {@link #propertiesProvider()}; setting both is rejected.
     *
     * <p>On the prep/expected path ({@link DbUnitExpected} declared), only takes effect when
     * {@link #prepAndExpectedTestCase()} resolves to a {@link DefaultPrepAndExpectedTestCase} -
     * the only {@link PrepAndExpectedTestCase} implementation exposing a setter for it. A
     * {@link DbUnitTestCase} field injecting a different implementation leaves this attribute
     * silently unapplied there. On the simple path (no {@link DbUnitExpected}), this
     * restriction does not apply - every value always reaches the tester's connection.
     *
     * @return The properties; empty (the default) applies none.
     */
    DbUnitProperty[] properties() default {};

    /**
     * A {@link DatabaseConfigPropertiesProvider} implementation to reflectively instantiate
     * (with its no-arg constructor) and ask for the {@link DatabaseConfig} properties to
     * apply, for properties shared across several test classes.
     *
     * <p>Mutually exclusive with {@link #properties()}; setting both is rejected.
     *
     * <p>Subject to the same {@link DefaultPrepAndExpectedTestCase}-only restriction on the
     * prep/expected path that {@link #properties()} documents.
     *
     * @return The provider class; the interface itself (the default) means "not set".
     */
    Class<? extends DatabaseConfigPropertiesProvider> propertiesProvider() default DatabaseConfigPropertiesProvider.class;

    /**
     * One or more catalog classes to read shared {@link VerifyTableDefinition} constants from,
     * as the class-level default for every method's {@link DbUnitExpected#verifyDefinitions()}.
     * See {@link DbUnitExpected} for how the two resolve independently and what "catalog"
     * means.
     *
     * @return The catalog classes; empty (the default) means "not set".
     */
    Class<?>[] verifyDefinitions() default {};

    /**
     * A classpath directory prefix applied to every {@link DbUnitPrep} and
     * {@link DbUnitExpected} path that neither starts with {@code /} nor is otherwise
     * absolute, ahead of the test-class-package default.
     *
     * @return The base directory; empty (the default) means "not set".
     */
    String dataSetBaseDir() default "";

    /**
     * The {@link FailureHandler} to hand verification failures to, in place of dbUnit's own
     * default.
     *
     * <p>Only takes effect when {@link #prepAndExpectedTestCase()} resolves to a
     * {@link DefaultPrepAndExpectedTestCase} - the only {@link PrepAndExpectedTestCase}
     * implementation exposing a setter for it. A {@link DbUnitTestCase} field injecting a
     * different implementation leaves this attribute silently unapplied.
     *
     * @return The failure handler class, reflectively instantiated with its no-arg
     *         constructor; the interface itself (the default) means "not set".
     */
    Class<? extends FailureHandler> failureHandler() default FailureHandler.class;

    /**
     * Whether the connection this executor resolves - for the prep/expected path, the row count
     * check, or parameter injection - is closed after each test.
     *
     * <p>Set to {@code false} when the {@code IDatabaseTester} shares a
     * {@code CachingConnectionProvider} across test methods, so this test does not close a
     * connection other tests still expect to reuse. The connection is also left open,
     * regardless of this attribute, when the tester's {@link IOperationListener} is
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER} - the established, pre-existing
     * signal that a tester's connection is managed elsewhere - so a connection already
     * protected that way needs no explicit {@code false} here.
     *
     * @return True to close the connection after each test; defaults to true.
     */
    boolean closeConnectionAfterTest() default true;
}
