/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * @author John Hurst
 * @version $Revision$
 * @since 2.4.8
 */
public class TestBlob implements Blob
{
    private byte[] data = new byte[0];

    public TestBlob(byte[] data)
    {
        this.data = data;
    }

    public long length() throws SQLException
    { // used in DbUnit
        return data.length;
    }

    public byte[] getBytes(long pos, int length) throws SQLException
    { // used in DbUnit
        byte[] result = new byte[length];
        System.arraycopy(data, (int) pos - 1, result, 0, length);
        return result;
    }

    public InputStream getBinaryStream() throws SQLException
    {
        throw new SQLException("TestBlob does not support getBinaryStream()");
    }

    public long position(byte[] pattern, long start) throws SQLException
    {
        throw new SQLException(
                "TestBlob does not support position(byte[], long)");
    }

    public long position(Blob pattern, long start) throws SQLException
    {
        throw new SQLException(
                "TestBlob does not support position(Blob, long)");
    }

    public int setBytes(long pos, byte[] bytes) throws SQLException
    {
        throw new SQLException(
                "TestBlob does not support setBytes(long, byte[])");
    }

    public int setBytes(long pos, byte[] bytes, int offset, int len)
            throws SQLException
    {
        throw new SQLException(
                "TestBlob does not support setBytes(long, byte[], int, int)");
    }

    public OutputStream setBinaryStream(long pos) throws SQLException
    {
        throw new SQLException(
                "TestBlob does not support setBinaryStream(long)");
    }

    public void truncate(long len) throws SQLException
    {
        throw new SQLException("TestBlob does not support truncate(long)");
    }

    public void free() throws SQLException
    {
        throw new SQLException("TestBlob does not support free()");
    }

    public InputStream getBinaryStream(long pos, long length)
            throws SQLException
    {
        throw new SQLException(
                "TestBlob does not support getBinaryStream(long, long)");
    }
}
