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
import org.dbunit.VerifyTableDefinition;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;

/**
 * Class-level wiring for annotation-driven dbUnit test configuration: which
 * {@link DataFileLoader} loads dataset files, how to obtain a tester via
 * {@link DatabaseTesterFactory} when no field supplies one, which
 * {@link DefaultPrepAndExpectedTestCase} subclass drives the prep/expected path,
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
 * @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
 * public @interface AppDatabaseTest {}
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitProperty
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitConfig
{
    /**
     * The {@link DataFileLoader} implementation used to load every {@link DbUnitPrep} and
     * {@link DbUnitExpected} dataset file, reflectively instantiated with its no-arg
     * constructor.
     *
     * <p>On the prep/expected path ({@link DbUnitExpected} declared), a freshly-constructed
     * {@link #prepAndExpectedTestCase()} always receives this value through its constructor.
     * A {@link DbUnitTestCase} field injecting an already-built test case is used as it was
     * built, with its own loader: naming a non-default loader here next to one fails fast with
     * {@link IllegalStateException}.
     *
     * <p>A new loader is constructed for each test, so
     * {@link org.dbunit.util.fileloader.CachingFileExtensionDataFileLoader}, which shares what
     * it parsed among all instances of its class, parses each file once per run instead of once
     * per test.
     *
     * @return The loader class; defaults to {@link FileExtensionDataFileLoader}.
     */
    Class<? extends DataFileLoader> dataFileLoader() default FileExtensionDataFileLoader.class;

    /**
     * A {@link DatabaseTesterFactory} implementation to reflectively instantiate (with its
     * no-arg constructor) and ask to create the {@code IDatabaseTester} to use, when neither a
     * {@link DbUnitTestCase} nor a {@link DbUnitTester} field is declared. Naming one next to a
     * {@link DbUnitTestCase} field fails fast with {@link IllegalStateException}, as the injected
     * instance carries its own tester.
     *
     * @return The factory class; the interface itself (the default) means "not set", falling
     *         through to the plain field auto-scan.
     */
    Class<? extends DatabaseTesterFactory> databaseTesterFactory() default DatabaseTesterFactory.class;

    /**
     * The {@link DefaultPrepAndExpectedTestCase} subclass to reflectively instantiate for the
     * prep/expected path when no {@link DbUnitTestCase} field supplies one already, via a
     * constructor accepting {@code (DataFileLoader, IDatabaseTester)} - the same shape
     * {@link DefaultPrepAndExpectedTestCase} itself has - for example one overriding a
     * hook such as {@code setUpDatabaseConfig()}. Naming one next to a {@link DbUnitTestCase}
     * field, which supplies the test case, fails fast with {@link IllegalStateException}.
     *
     * @return The test case class; defaults to {@link DefaultPrepAndExpectedTestCase}.
     */
    Class<? extends DefaultPrepAndExpectedTestCase> prepAndExpectedTestCase() default DefaultPrepAndExpectedTestCase.class;

    /**
     * Inline {@link DatabaseConfig} property name/value pairs to apply.
     *
     * <p>On the prep/expected path ({@link DbUnitExpected} declared), applied by the
     * {@link DefaultPrepAndExpectedTestCase} to its connection. A {@link DbUnitTestCase} field
     * injecting an already-built test case is used as it was built, so declaring these next to
     * one fails fast with {@link IllegalStateException}: set them where it is built, with
     * {@link DefaultPrepAndExpectedTestCase#setDatabaseConfigProperties(java.util.Properties)}.
     * On the setup/teardown path (no {@link DbUnitExpected}), every value reaches the tester's
     * connection.
     *
     * <p><strong>On the prep/expected path, a {@link DefaultPrepAndExpectedTestCase} subclass
     * that overrides {@code setUpDatabaseConfig(DatabaseConfig)} must call
     * {@code super.setUpDatabaseConfig(config)}</strong> - the pre-3.6.0 way to configure a
     * {@link DatabaseConfig} - or these values are silently dropped: they are applied through
     * that same {@code setUpDatabaseConfig()} hook. The extension logs a warning when it sees
     * such an override together with {@code properties()}, since it cannot tell from
     * reflection alone whether the {@code super} call is present.
     *
     * <p>The values last for the test: when it ends, each is put back to the value it replaced,
     * so a connection that outlives the test - a fixed connection, one cached by a
     * {@code CachingConnectionProvider}, or one the tester's listener leaves open - carries
     * none of them into whatever uses it next.
     *
     * @return The properties; empty (the default) applies none.
     */
    DbUnitProperty[] properties() default {};

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
     * absolute, ahead of the default of the package of the class declaring the annotation.
     *
     * @return The base directory; empty (the default) means "not set".
     */
    String dataSetBaseDir() default "";

    /**
     * The {@link FailureHandler} to hand verification failures to, in place of dbUnit's own
     * default.
     *
     * <p>Applied by the {@link DefaultPrepAndExpectedTestCase} dbUnit constructs. A
     * {@link DbUnitTestCase} field injecting an already-built test case is used as it was
     * built, so declaring this next to one fails fast with {@link IllegalStateException},
     * naming the implementation and this attribute: set the handler where the instance is
     * built.
     *
     * @return The failure handler class, reflectively instantiated with its no-arg
     *         constructor; the interface itself (the default) means "not set".
     */
    Class<? extends FailureHandler> failureHandler() default FailureHandler.class;
}
