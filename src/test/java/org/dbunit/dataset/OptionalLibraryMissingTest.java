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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.dbunit.DatabaseUnitRuntimeException;
import org.junit.jupiter.api.Test;

/**
 * Proves reading or writing a JSON or YAML dataset on a classpath without Jackson or SnakeYAML
 * fails with an error that says what to add, rather than a bare {@link NoClassDefFoundError}.
 * Flat XML needs neither library and is unaffected.
 */
class OptionalLibraryMissingTest
{
    private static final String JACKSON_PREFIX = "com.fasterxml.";
    private static final String SNAKEYAML_PREFIX = "org.yaml.";
    private static final String JSON_PACKAGE = "org.dbunit.dataset.json.";
    private static final String YAML_PACKAGE = "org.dbunit.dataset.yaml.";
    private static final String LOADER_PACKAGE = "org.dbunit.util.fileloader.";
    private static final String COMPARER_PACKAGE = "org.dbunit.assertion.comparer.value.";
    private static final String JACKSON_COORDINATES = "com.fasterxml.jackson.core:jackson-databind";
    private static final String SNAKEYAML_COORDINATES = "org.yaml:snakeyaml";

    @Test
    void testJsonDataSetFromStream_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(JSON_PACKAGE + "JsonDataSet");

        final Throwable thrown = failureOf(() -> dataSetClass.getConstructor(InputStream.class)
                .newInstance(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining("com.fasterxml.jackson.core:jackson-databind");
    }

    @Test
    void testJsonDataSetWrite_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(JSON_PACKAGE + "JsonDataSet");
        final Method write = dataSetClass.getMethod("write", IDataSet.class,
                java.io.OutputStream.class);

        final Throwable thrown = failureOf(() -> write.invoke(null, new DefaultDataSet(),
                new ByteArrayOutputStream()));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining("com.fasterxml.jackson.core:jackson-databind");
    }

    @Test
    void testJsonDataFileLoader_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader =
                new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE, LOADER_PACKAGE);
        final Class<?> loaderClass = loader.loadClass(LOADER_PACKAGE + "JsonDataFileLoader");
        final Object dataFileLoader = loaderClass.getConstructor().newInstance();

        final Throwable thrown = failureOf(() -> loaderClass.getMethod("load", String.class)
                .invoke(dataFileLoader, "/org/dbunit/junit/jupiter/annotation-it-prep.json"));

        assertThat(thrown).hasMessageContaining("com.fasterxml.jackson.core:jackson-databind");
    }

    @Test
    void testYamlDataSetFromStream_snakeYamlMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(YAML_PACKAGE + "YamlDataSet");

        final Throwable thrown = failureOf(() -> dataSetClass.getConstructor(InputStream.class)
                .newInstance(new ByteArrayInputStream("T: []".getBytes(StandardCharsets.UTF_8))));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining("org.yaml:snakeyaml");
    }

    @Test
    void testYamlDataSetWrite_snakeYamlMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(YAML_PACKAGE + "YamlDataSet");
        final Method write = dataSetClass.getMethod("write", IDataSet.class,
                java.io.OutputStream.class);

        final Throwable thrown = failureOf(() -> write.invoke(null, new DefaultDataSet(),
                new ByteArrayOutputStream()));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining("org.yaml:snakeyaml");
    }

    @Test
    void testYamlDataFileLoader_snakeYamlMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader =
                new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE, LOADER_PACKAGE);
        final Class<?> loaderClass = loader.loadClass(LOADER_PACKAGE + "YamlDataFileLoader");
        final Object dataFileLoader = loaderClass.getConstructor().newInstance();

        final Throwable thrown = failureOf(() -> loaderClass.getMethod("load", String.class)
                .invoke(dataFileLoader, "/yaml/dataSetTest.yml"));

        assertThat(thrown).hasMessageContaining("org.yaml:snakeyaml");
    }

    @Test
    void testJsonDataSetFromFile_jacksonMissing_failsNamingTheDependencyBeforeOpeningTheFile()
            throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(JSON_PACKAGE + "JsonDataSet");

        final Throwable thrown = failureOf(() -> dataSetClass.getConstructor(File.class)
                .newInstance(Paths.get("does-not-exist.json").toFile()));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining(JACKSON_COORDINATES);
    }

    @Test
    void testJsonDataSetWriteToWriter_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(JSON_PACKAGE + "JsonDataSet");
        final Method write = dataSetClass.getMethod("write", IDataSet.class, Writer.class);

        final Throwable thrown = failureOf(
                () -> write.invoke(null, new DefaultDataSet(), new StringWriter()));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining(JACKSON_COORDINATES);
    }

    @Test
    void testJsonProducerFromStream_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> producerClass = loader.loadClass(JSON_PACKAGE + "JsonProducer");

        final Throwable thrown = failureOf(() -> producerClass.getConstructor(InputStream.class)
                .newInstance(new ByteArrayInputStream(new byte[0])));

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(JACKSON_COORDINATES);
    }

    @Test
    void testJsonProducerFromFile_jacksonMissing_failsNamingTheDependencyBeforeOpeningTheFile()
            throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> producerClass = loader.loadClass(JSON_PACKAGE + "JsonProducer");

        final Throwable thrown = failureOf(() -> producerClass.getConstructor(File.class)
                .newInstance(Paths.get("does-not-exist.json").toFile()));

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(JACKSON_COORDINATES);
    }

    @Test
    void testJsonWriter_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(JACKSON_PREFIX, JSON_PACKAGE);
        final Class<?> writerClass = loader.loadClass(JSON_PACKAGE + "JsonWriter");
        final java.lang.reflect.Constructor<?> constructor =
                writerClass.getDeclaredConstructor(Writer.class);
        constructor.setAccessible(true);

        final Throwable thrown = failureOf(() -> constructor.newInstance(new StringWriter()));

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(JACKSON_COORDINATES);
    }

    @Test
    void testJacksonJsonValueComparer_jacksonMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader =
                new LibraryHidingClassLoader(JACKSON_PREFIX, COMPARER_PACKAGE);
        final Class<?> comparerClass = loader.loadClass(
                COMPARER_PACKAGE + "IsActualEqualToExpectedJsonValueComparer");

        final Throwable thrown = failureOf(() -> comparerClass.getConstructor().newInstance());

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(JACKSON_COORDINATES);
    }

    @Test
    void testYamlDataSetFromFile_snakeYamlMissing_failsNamingTheDependencyBeforeOpeningTheFile()
            throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(YAML_PACKAGE + "YamlDataSet");

        final Throwable thrown = failureOf(() -> dataSetClass.getConstructor(File.class)
                .newInstance(Paths.get("does-not-exist.yml").toFile()));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining(SNAKEYAML_COORDINATES);
    }

    @Test
    void testYamlDataSetWriteToWriter_snakeYamlMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(YAML_PACKAGE + "YamlDataSet");
        final Method write = dataSetClass.getMethod("write", IDataSet.class, Writer.class);

        final Throwable thrown = failureOf(
                () -> write.invoke(null, new DefaultDataSet(), new StringWriter()));

        assertThat(thrown).isInstanceOf(DataSetException.class)
                .hasMessageContaining(SNAKEYAML_COORDINATES);
    }

    @Test
    void testYamlProducerFromStream_snakeYamlMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> producerClass = loader.loadClass(YAML_PACKAGE + "YamlProducer");

        final Throwable thrown = failureOf(() -> producerClass.getConstructor(InputStream.class)
                .newInstance(new ByteArrayInputStream(new byte[0])));

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(SNAKEYAML_COORDINATES);
    }

    @Test
    void testYamlProducerFromFile_snakeYamlMissing_failsNamingTheDependencyBeforeOpeningTheFile()
            throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> producerClass = loader.loadClass(YAML_PACKAGE + "YamlProducer");

        final Throwable thrown = failureOf(() -> producerClass.getConstructor(File.class)
                .newInstance(Paths.get("does-not-exist.yml").toFile()));

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(SNAKEYAML_COORDINATES);
    }

    @Test
    void testYamlWriter_snakeYamlMissing_failsNamingTheDependency() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader(SNAKEYAML_PREFIX, YAML_PACKAGE);
        final Class<?> writerClass = loader.loadClass(YAML_PACKAGE + "YamlWriter");
        final java.lang.reflect.Constructor<?> constructor =
                writerClass.getDeclaredConstructor(Writer.class);
        constructor.setAccessible(true);

        final Throwable thrown = failureOf(() -> constructor.newInstance(new StringWriter()));

        assertThat(thrown).isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasMessageContaining(SNAKEYAML_COORDINATES);
    }

    @Test
    void testJsonDataSetFromStream_jacksonPresent_buildsTheDataSetAsUsual() throws Exception
    {
        final ClassLoader loader = new LibraryHidingClassLoader("no.such.prefix.",
                JSON_PACKAGE);
        final Class<?> dataSetClass = loader.loadClass(JSON_PACKAGE + "JsonDataSet");

        final Object dataSet = dataSetClass.getConstructor(InputStream.class).newInstance(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

        assertThat(dataSet)
                .as("With the library present, nothing is reported missing and the dataset is"
                        + " built as usual.")
                .isNotNull();
    }

    private interface ReflectiveCall
    {
        void run() throws Exception;
    }

    /** Runs the call and returns what it threw, unwrapping the reflective wrapper. */
    private static Throwable failureOf(final ReflectiveCall call)
    {
        try
        {
            call.run();
        } catch (final InvocationTargetException e)
        {
            return e.getCause();
        } catch (final Exception e)
        {
            return e;
        }
        throw new AssertionError("Expected the call to fail.");
    }
}
