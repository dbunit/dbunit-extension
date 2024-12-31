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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dbunit.dataset.ITable;
import org.dbunit.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Mar 20, 2002)
 */
public class BytesDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(BytesDataType.class);

    private static final int MAX_URI_LENGTH = 256;
    private static final Pattern inputPattern =
            Pattern.compile("^\\[(.*?)](.*)");

    public BytesDataType(final String name, final int sqlType)
    {
        super(name, sqlType, byte[].class, false);
    }

    private byte[] toByteArray(InputStream in, final int length)
            throws IOException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("toByteArray(in={}, length={}) - start", in, length);
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        in = new BufferedInputStream(in);
        int i = in.read();
        while (i != -1)
        {
            out.write(i);
            i = in.read();
        }
        return out.toByteArray();
    }

    public byte[] loadFile(final String filename) throws IOException
    {
        // Not an URL, try as file name
        final File file = new File(filename);
        return toByteArray(new FileInputStream(file), (int) file.length());
    }

    public byte[] loadURL(final String urlAsString) throws IOException
    {
        // Not an URL, try as file name
        final URL url = new URL(urlAsString);
        return toByteArray(url.openStream(), 0);
    }

    ////////////////////////////////////////////////////////////////////////////
    // DataType class

    /**
     * Casts the given value into a byte[] using different strategies. Note that
     * this might sometimes result in undesired behavior when character data
     * (Strings) are used.
     *
     * @see org.dbunit.dataset.datatype.DataType#typeCast(java.lang.Object)
     */
    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        logger.debug("typeCast(value={}) - start", value);

        if (value == null || value == ITable.NO_VALUE)
        {
            return null;
        }

        if (value instanceof byte[])
        {
            return value;
        }

        if (value instanceof String)
        {
            String stringValue = (String) value;

            // If the string starts with <text [encoding id]>, it means that the
            // user
            // intentionally wants to transform the text into a blob.
            //
            // Example of a valid string: "<text UTF-8>This is a valid string
            // with the accent 'é'"
            if (isExtendedSyntax(stringValue))
            {
                final Matcher matcher = inputPattern.matcher(stringValue);
                if (matcher.matches())
                {
                    final String commandLine = matcher.group(1).toUpperCase();
                    stringValue = matcher.group(2);

                    final String[] split = commandLine.split(" ");
                    final String command = split[0];

                    if ("TEXT".equals(command))
                    {
                        String encoding = "UTF-8"; // Default

                        if (split.length > 1)
                        {
                            encoding = split[1];
                        }
                        logger.debug(
                                "Data explicitly states that given string is text encoded {}",
                                encoding);
                        try
                        {
                            final Charset charset = Charset.forName(encoding);
                            return stringValue.getBytes(charset);
                        } catch (final IllegalArgumentException e)
                        {
                            return "Error:  [text " + encoding
                                    + "] has an invalid encoding id.";
                        }
                    } else if ("BASE64".equals(command))
                    {
                        logger.debug(
                                "Data explicitly states that given string is base46");
                        return Base64.decode(stringValue);
                    } else if ("FILE".equals(command))
                    {
                        try
                        {
                            logger.debug(
                                    "Data explicitly states that given string is a file name");
                            return loadFile(stringValue);
                        } catch (final IOException e)
                        {
                            final String errMsg =
                                    "Could not load file following instruction >>"
                                            + value + "<<";
                            logger.error(errMsg);
                            return ("Error:  " + errMsg).getBytes();
                        }
                    } else if ("URL".equals(command))
                    {
                        try
                        {
                            logger.debug(
                                    "Data explicitly states that given string is a URL");
                            return loadURL(stringValue);
                        } catch (final IOException e)
                        {
                            final String errMsg =
                                    "Could not load URL following instruction >>"
                                            + value + "<<";
                            logger.error(errMsg);
                            return ("Error:  " + errMsg).getBytes();
                        }
                    }
                }
            }

            // Assume not an uri if length greater than max uri length
            if (stringValue.length() == 0
                    || stringValue.length() > MAX_URI_LENGTH)
            {
                logger.debug(
                        "Assuming given string to be Base64 and not a URI");
                return Base64.decode((String) value);
            }

            try
            {
                logger.debug("Assuming given string to be a URI");
                try
                {
                    // Try value as URL
                    return loadURL(stringValue);
                } catch (final MalformedURLException e1)
                {
                    logger.debug(
                            "Given string is not a valid URI - trying to resolve it as file...");
                    try
                    {
                        // Not an URL, try as file name
                        return loadFile(stringValue);
                    } catch (final FileNotFoundException e2)
                    {
                        logger.debug(
                                "Assuming given string to be Base64 and not a URI or File");
                        // Not a file name either
                        final byte[] decodedBytes = Base64.decode(stringValue);
                        if (decodedBytes == null && stringValue.length() > 0)
                        {
                            // Ok, here the user has not specified the "[text
                            // ...]" tag, but
                            // it looks that its text that should be stored in
                            // the blob. So
                            // we make a last attempt at doing so.
                            logger.debug(
                                    "Assuming given string to be content of the blob, encoded with UTF-8.");
                            return stringValue.getBytes();
                        } else
                        {
                            return decodedBytes;
                        }
                    }
                }
            } catch (final IOException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        if (value instanceof Blob)
        {
            try
            {
                final Blob blobValue = (Blob) value;
                if (blobValue.length() == 0)
                {
                    return null;
                }
                return blobValue.getBytes(1, (int) blobValue.length());
            } catch (final SQLException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        if (value instanceof URL)
        {
            try
            {
                return toByteArray(((URL) value).openStream(), 0);
            } catch (final IOException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        if (value instanceof File)
        {
            try
            {
                final File file = (File) value;
                return toByteArray(new FileInputStream(file),
                        (int) file.length());
            } catch (final IOException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        throw new TypeCastException(value, this);
    }

    @Override
    protected int compareNonNulls(final Object value1, final Object value2)
            throws TypeCastException
    {
        logger.debug("compareNonNulls(value1={}, value2={}) - start", value1,
                value2);

        try
        {
            final byte[] value1cast = (byte[]) typeCast(value1);
            final byte[] value2cast = (byte[]) typeCast(value2);

            return compare(value1cast, value2cast);
        } catch (final ClassCastException e)
        {
            throw new TypeCastException(e);
        }
    }

    public int compare(final byte[] v1, final byte[] v2)
            throws TypeCastException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("compare(v1={}, v2={}) - start", v1, v2);
        }

        final int len1 = v1.length;
        final int len2 = v2.length;
        int n = Math.min(len1, len2);
        int i = 0;
        int j = 0;

        if (i == j)
        {
            int k = i;
            final int lim = n + i;
            while (k < lim)
            {
                final byte c1 = v1[k];
                final byte c2 = v2[k];
                if (c1 != c2)
                {
                    return c1 - c2;
                }
                k++;
            }
        } else
        {
            while (n-- != 0)
            {
                final byte c1 = v1[i++];
                final byte c2 = v2[j++];
                if (c1 != c2)
                {
                    return c1 - c2;
                }
            }
        }
        return len1 - len2;
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
                resultSet);

        final byte[] value = resultSet.getBytes(column);
        if (value == null || resultSet.wasNull())
        {
            return null;
        }
        return value;
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        super.setSqlValue(value, column, statement);
    }
}
