/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

package org.dbunit.dataset.datatype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

import org.dbunit.TurkishDefaultLocale;
import org.dbunit.dataset.ITable;
import org.dbunit.testutil.FileAsserts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
class BytesDataTypeTest extends AbstractDataTypeTest
{
    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet mockedResultSet;

    private final static DataType[] TYPES =
            {DataType.BINARY, DataType.VARBINARY, DataType.LONGVARBINARY,
            // DataType.BLOB,
            };

    @Override
    @Test
    public void testToString_withDataType_returnsExpectedString() throws Exception
    {
        final String[] expected = {"BINARY", "VARBINARY", "LONGVARBINARY",
                // "BLOB",
        };

        assertThat(TYPES).as("type count").hasSize(expected.length);
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i]).as("name").hasToString(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetTypeClass_returnsExpectedClass() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.getTypeClass()).as("class")
                    .isEqualTo(byte[].class);
        }
    }

    @Override
    @Test
    public void testIsNumber_returnsExpectedBoolean() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.isNumber()).as("is number").isFalse();
        }
    }

    @Override
    @Test
    public void testIsDateTime_returnsExpectedBoolean() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.isDateTime()).as("is date/time").isFalse();
        }
    }

    @Override
    @Test
    public void testTypeCast_withCompatibleInput_returnsExpectedValue() throws Exception
    {
        final Object[] values = {null, "", "YWJjZA==",
                new byte[] {0, 1, 2, 3, 4, 5},
                "[text]This is text with UTF-8 (the default) characters >>àéç<<",
                "[text UTF-8]This is text with UTF-8 (the default) characters >>àéç<<",
                "[text]c27ccbf5-6ca1-4bdd-8cb0-bacfea6a5a8b",
                "[base64]VGhpcyBpcyBhIHRlc3QgZm9yIGJhc2U2NC4K=="};

        final byte[][] expected = {null, new byte[0],
                new byte[] {'a', 'b', 'c', 'd'}, new byte[] {0, 1, 2, 3, 4, 5},
                values[4].toString().replaceAll("\\[.*?\\]", "")
                        .getBytes(StandardCharsets.UTF_8),
                values[5].toString().replaceAll("\\[.*?\\]", "")
                        .getBytes(StandardCharsets.UTF_8),
                values[6].toString().replaceAll("\\[.*?\\]", "")
                        .getBytes(StandardCharsets.UTF_8),
                "This is a test for base64.\n".getBytes(),};

        assertThat(expected).as("actual vs expected count")
                .hasNumberOfRows(values.length);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < values.length; j++)
            {
                final byte[] actual = (byte[]) element.typeCast(values[j]);
                assertThat(actual).as("typecast " + j).isEqualTo(expected[j]);
            }
        }
    }

    @Test
    void testTypeCastFileName_withFilePathValue_returnsFileContentsAsBytes() throws Exception
    {
        final File file = Paths.get("LICENSE.txt").toFile();

        final Object[] values = {"[file]" + file.toString(), file.toString(),
                file.getAbsolutePath(), file.toURI().toURL().toString(), file,
                file.toURI().toURL(), "[url]" + file.toURI().toURL(),};

        assertThat(file).as("exists").exists();

        for (final DataType element : TYPES)
        {
            for (final Object value : values)
            {
                final byte[] actual = (byte[]) element.typeCast(value);
                FileAsserts.assertEquals(new ByteArrayInputStream(actual),
                        file);
            }
        }
    }

    @Test
    void testLoadFile_afterLoad_fileIsDeletable() throws Exception
    {
        final File file = Files.createTempFile("BytesDataTypeTest", ".bin").toFile();
        file.deleteOnExit();
        Files.write(file.toPath(), new byte[] {1, 2, 3});

        new BytesDataType("BINARY", Types.BINARY).loadFile(file.getPath());

        assertThat(file.delete())
                .as("loadFile() must close its file handle so the loaded"
                        + " file can be deleted immediately afterward.")
                .isTrue();
    }

    @Test
    void testTypeCast_urlValue_closesStream() throws Exception
    {
        final byte[] expected = {1, 2, 3};
        final InputStream spyStream =
                Mockito.spy(new ByteArrayInputStream(expected));
        final URLStreamHandler handler = new URLStreamHandler()
        {
            @Override
            protected URLConnection openConnection(final URL u)
            {
                return new URLConnection(u)
                {
                    @Override
                    public void connect()
                    {
                        // No connection setup needed for this test double.
                    }

                    @Override
                    public InputStream getInputStream()
                    {
                        return spyStream;
                    }
                };
            }
        };
        final URL url = new URL("spy", "test", -1, "/data", handler);

        final Object actual =
                new BytesDataType("BINARY", Types.BINARY).typeCast(url);

        assertThat(actual).as("typeCast(URL) result.").isEqualTo(expected);
        verify(spyStream, times(1)).close();
    }

    @Test
    void testTypeCast_textCommandInvalidEncoding_throwsTypeCastException()
    {
        final String value = "[text bogus-encoding]hello";
        final BytesDataType dataType = new BytesDataType("BINARY", Types.BINARY);

        assertThatExceptionOfType(TypeCastException.class)
                .as("An unrecognized [text <encoding>] id must fail fast with"
                        + " a typed exception instead of returning error text"
                        + " as if it were the byte[] result.")
                .isThrownBy(() -> dataType.typeCast(value));
    }

    @Test
    void testTypeCast_base64CommandCorruptInput_throwsTypeCastException()
    {
        final String value = "[base64]!!!not-valid-base64!!!";
        final BytesDataType dataType = new BytesDataType("BINARY", Types.BINARY);

        assertThatExceptionOfType(TypeCastException.class)
                .as("A value explicitly tagged [base64] that is not"
                        + " decodable must fail fast with a typed exception"
                        + " instead of silently becoming NULL.")
                .isThrownBy(() -> dataType.typeCast(value));
    }

    @Test
    void testTypeCast_fileCommandMissingFile_throwsTypeCastException()
    {
        final String value = "[file]/does/not/exist/dbunit-missing-file.bin";
        final BytesDataType dataType = new BytesDataType("BINARY", Types.BINARY);

        assertThatExceptionOfType(TypeCastException.class)
                .as("A value explicitly tagged [file] that cannot be loaded"
                        + " must fail fast with a typed exception instead of"
                        + " storing an \"Error: ...\" message as the blob's"
                        + " bytes.")
                .isThrownBy(() -> dataType.typeCast(value));
    }

    @Test
    void testTypeCast_urlCommandUnreachable_throwsTypeCastException()
            throws Exception
    {
        final File missingFile =
                Paths.get("does-not-exist", "dbunit-missing-file.bin").toFile();
        final String value = "[url]" + missingFile.toURI().toURL();
        final BytesDataType dataType = new BytesDataType("BINARY", Types.BINARY);

        assertThatExceptionOfType(TypeCastException.class)
                .as("A value explicitly tagged [url] that cannot be loaded"
                        + " must fail fast with a typed exception instead of"
                        + " storing an \"Error: ...\" message as the blob's"
                        + " bytes.")
                .isThrownBy(() -> dataType.typeCast(value));
    }

    @Test
    void testTypeCast_untaggedTextFallback_usesUtf8Bytes() throws Exception
    {
        final String nonAsciiValue = "café!";
        final byte[] expected = nonAsciiValue.getBytes(StandardCharsets.UTF_8);
        final BytesDataType dataType = new BytesDataType("BINARY", Types.BINARY);

        final Object actual = dataType.typeCast(nonAsciiValue);

        assertThat(actual)
                .as("The untagged text fallback must encode with UTF-8"
                        + " regardless of the platform default charset.")
                .isEqualTo(expected);
    }

    @Test
    void testTypeCast_untaggedLongInvalidBase64_fallsBackToUtf8Bytes()
            throws Exception
    {
        // Longer than BytesDataType's private MAX_URI_LENGTH (256), so this
        // skips the "assume URI" guess entirely and goes straight to the
        // Base64 attempt; '!' is not a valid Base64 character
        final char[] invalidBase64Chars = new char[300];
        Arrays.fill(invalidBase64Chars, '!');
        final String longInvalidBase64Value = new String(invalidBase64Chars);
        final byte[] expected =
                longInvalidBase64Value.getBytes(StandardCharsets.UTF_8);
        final BytesDataType dataType = new BytesDataType("BINARY", Types.BINARY);

        final Object actual = dataType.typeCast(longInvalidBase64Value);

        assertThat(actual)
                .as("An untagged value too long to plausibly be a URI, and"
                        + " not valid Base64 either, must fall back to its"
                        + " literal UTF-8 bytes like the shorter 'assume URI'"
                        + " fallback does, instead of silently becoming"
                        + " NULL.")
                .isEqualTo(expected);
    }

    @Test
    @TurkishDefaultLocale
    void testTypeCast_turkishLocaleFileCommand_recognizedAsFileCommand() throws Exception
    {
        final File file = Files.createTempFile("BytesDataTypeTest", ".bin").toFile();
        file.deleteOnExit();
        final byte[] expected =
                "dbunit turkish locale file command test"
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), expected);

        final BytesDataType spyType =
                Mockito.spy(new BytesDataType("BINARY", Types.BINARY));

        final Object actual =
                spyType.typeCast("[file]" + file.getPath());

        assertThat(actual)
                .as("typeCast() must recognize the lower-case '[file]'"
                        + " command and load the file's bytes.")
                .isEqualTo(expected);
        // Only the fallback URI/file/Base64 guesser (taken when the
        // "FILE" command is not recognized) ever calls loadURL() first;
        // a buggy default-locale fold of "file" to "FİLE" under tr-TR
        // would miss the command match and fall through to it.
        verify(spyType, never()).loadURL(anyString());
    }

    @Override
    @Test
    public void testTypeCastNone_withNullInput_returnsNull() throws Exception
    {
        for (final DataType type : TYPES)
        {
            assertThat(type.typeCast(ITable.NO_VALUE)).as("typecast " + type)
                    .isNull();
        }
    }

    @Override
    @Test
    public void testTypeCastInvalid_withIncompatibleInput_throwsTypeCastException() throws Exception
    {
        final Object[] values = {new Object(), Integer.valueOf(1234),};

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values.length; j++)
            {
                final int id = i;
                final int jd = j;
                assertThrows(TypeCastException.class,
                        () -> TYPES[id].typeCast(values[jd]),
                        "Should throw TypeCastException: " + values[jd]);
            }
        }
    }

    @Override
    @Test
    public void testCompareEquals_withEqualValues_returnsZero() throws Exception
    {
        final Object[] values1 =
                {null, "", "YWJjZA==", new byte[] {0, 1, 2, 3, 4, 5},};

        final byte[][] values2 =
                {null, new byte[0], new byte[] {'a', 'b', 'c', 'd'},
                        new byte[] {0, 1, 2, 3, 4, 5},};

        assertThat(values2).as("values count").hasNumberOfRows(values1.length);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < values1.length; j++)
            {
                assertThat(element.compare(values1[j], values2[j]))
                        .as("compare1 " + j).isZero();
                assertThat(element.compare(values2[j], values1[j]))
                        .as("compare2 " + j).isZero();
            }
        }
    }

    @Override
    @Test
    public void testCompareInvalid_withInvalidInput_throwsTypeCastException() throws Exception
    {
        final Object[] values1 = {new Object(), new java.util.Date()};
        final Object[] values2 = {null, null};

        assertThat(values2).as("values count").hasSize(values1.length);

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values1.length; j++)
            {
                final int id = i;
                final int jd = j;
                assertThrows(TypeCastException.class,
                        () -> TYPES[id].compare(values1[jd], values2[jd]),
                        "Should throw TypeCastException");

                assertThrows(TypeCastException.class,
                        () -> TYPES[id].compare(values2[jd], values1[jd]),
                        "Should throw TypeCastException");
            }
        }
    }

    @Override
    @Test
    public void testCompareDifferent_withDifferentValues_returnsNonZero() throws Exception
    {
        final Object[] less = {null, new byte[] {'a', 'a', 'c', 'd'},
                new byte[] {0, 1, 2, 3, 4, 5},};
        final Object[] greater = {new byte[0], new byte[] {'a', 'b', 'c', 'd'},
                new byte[] {0, 1, 2, 3, 4, 5, 6},};

        assertThat(greater).as("values count").hasSize(less.length);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < less.length; j++)
            {
                assertThat(element.compare(less[j], greater[j])).as("less " + j)
                        .isNegative();
                assertThat(element.compare(greater[j], less[j]))
                        .as("greater " + j).isPositive();
            }
        }
    }

    @Override
    @Test
    public void testSqlType_returnsExpectedSqlType() throws Exception
    {
        final int[] sqlTypes =
                {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY,
                // Types.BLOB,
                };

        assertThat(TYPES).as("count").hasSize(sqlTypes.length);
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(DataType.forSqlType(sqlTypes[i])).as("forSqlType")
                    .isEqualTo(TYPES[i]);
            assertThat(DataType.forSqlTypeName(TYPES[i].toString()))
                    .as("forSqlTypeName").isEqualTo(TYPES[i]);
            assertThat(TYPES[i].getSqlType()).as("getSqlType")
                    .isEqualTo(sqlTypes[i]);
        }
    }

    @Override
    @Test
    public void testForObject_withValidInput_returnsDataType() throws Exception
    {
        assertThat(DataType.forObject(new byte[0]))
                .isEqualTo(DataType.VARBINARY);
    }

    @Override
    @Test
    public void testAsString_withValidInput_returnsStringRepresentation() throws Exception
    {
        final byte[][] values = {new byte[0], new byte[] {'a', 'b', 'c', 'd'},};

        final String[] expected = {"", "YWJjZA==",};

        assertThat(expected).as("actual vs expected count")
                .hasSize(values.length);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(DataType.asString(values[i])).as("asString " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetSqlValue_withValidStatement_returnsExpectedValue() throws Exception
    {
        final byte[][] expected =
                {null, new byte[0], new byte[] {'a', 'b', 'c', 'd'},
                        new byte[] {0, 1, 2, 3, 4, 5},};

        when(mockedResultSet.getBytes(1)).thenReturn(expected[0]);
        when(mockedResultSet.getBytes(2)).thenReturn(expected[1]);
        when(mockedResultSet.getBytes(3)).thenReturn(expected[2]);
        when(mockedResultSet.getBytes(4)).thenReturn(expected[3]);
        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];

            for (int j = 0; j < TYPES.length; j++)
            {
                final DataType dataType = TYPES[j];
                final Object actualValue =
                        dataType.getSqlValue(i + 1, mockedResultSet);
                assertThat(actualValue).as("value " + j)
                        .isEqualTo(expectedValue);
            }
        }
    }

    @Test
    void testSetSqlValue_withBytesValue_callsSetObjectOnStatement() throws Exception
    {

        final Object[] expected =
                {null, new byte[0], new byte[] {'a', 'b', 'c', 'd'},};

        final int[] expectedSqlTypesForDataType =
                {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY};

        for (final Object expectedValue : expected)
        {
            for (int j = 0; j < TYPES.length; j++)
            {
                final DataType dataType = TYPES[j];
                final int expectedSqlType = expectedSqlTypesForDataType[j];

                dataType.setSqlValue(expectedValue, 1, preparedStatement);
                // Check the results immediately
                verify(preparedStatement, times(1)).setObject(1, expectedValue,
                        expectedSqlType);
            }
        }
    }

    /**
     * Assert calls ResultSet.getBytes(columnIndex) before ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder_afterGetSqlValue_callsGetBytesBeforeWasNull()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.BINARY.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getBytes(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
