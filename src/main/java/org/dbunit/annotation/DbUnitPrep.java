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

import org.dbunit.util.fileloader.DataSetPathsProvider;

/**
 * Specifies the dataset file(s) to load and insert into the database before each test method,
 * seeding the state the test needs to run.
 *
 * <p>A method-level annotation overrides a class-level annotation on a per-test basis; the
 * class annotation is inherited by subclasses. Pair with {@link DbUnitSetup} only when a
 * setup operation other than the default
 * {@link org.dbunit.operation.DbUnitOperation#CLEAN_INSERT} is needed - see
 * {@link DbUnitSetup} for why the two are separate annotations.
 *
 * <p>Each path is resolved to an absolute classpath resource: a path already starting with
 * {@code /} is used as-is; otherwise it is resolved relative to the test class's package, or
 * to {@code DbUnitConfig#dataSetBaseDir()} when that is set. The file format is inferred from
 * the extension by the configured {@code DataFileLoader} - see {@link DbUnitConfig#dataFileLoader()}.
 *
 * <p>Example:
 * <pre>{@code
 * @DbUnitTest
 * class UserRepositoryTest {
 *     IDatabaseTester databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *
 *     @Test
 *     @DbUnitPrep("/datasets/users.xml")
 *     void testFindAll() { ... }
 * }
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitSetup
 * @see DbUnitExpected
 * @see DataSetPathsProvider
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitPrep {
    /**
     * Classpath resource paths for the prep dataset files to load.
     *
     * <p>Mutually exclusive with {@link #provider()}; setting both is rejected.
     *
     * @return Zero or more dataset resource paths.
     */
    String[] value() default {};

    /**
     * A {@link DataSetPathsProvider} implementation to reflectively instantiate and ask for
     * the prep dataset paths, for a path list shared across several test classes.
     *
     * <p>Mutually exclusive with {@link #value()}; setting both is rejected.
     *
     * @return The provider class; the interface itself (the default) means "not set".
     */
    Class<? extends DataSetPathsProvider> provider() default DataSetPathsProvider.class;
}
