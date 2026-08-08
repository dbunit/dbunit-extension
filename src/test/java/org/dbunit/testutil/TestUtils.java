/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2010, DbUnit.org
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

package org.dbunit.testutil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.dbunit.DatabaseEnvironment;

/**
 * @author John Hurst (john.b.hurst@gmail.com)
 * @since 2.4.8
 */
public class TestUtils
{
    private static String getProfileName() throws Exception
    {
        return DatabaseEnvironment.getInstance().getProfile()
                .getActiveProfile();
    }

    public static String getFileName(String fileName)
    {
        return "src/test/resources/" + fileName;
    }

    public static File getFile(String fileName)
    {
        return Paths.get(getFileName(fileName)).toFile();
    }

    public static File getFileForDatabaseEnvironment(String originalFileName)
            throws Exception
    {
        String profilePath =
                originalFileName.replace(".", "-" + getProfileName() + ".");
        File profileFile = Paths.get(profilePath).toFile();
        if (profileFile.exists())
        {
            return profileFile;
        } else
        {
            return Paths.get(originalFileName).toFile();
        }
    }

    public static Reader getFileReader(String fileName) throws IOException
    {
        return Files.newBufferedReader(Paths.get(getFileName(fileName)),
                StandardCharsets.UTF_8);
    }

    public static InputStream getFileInputStream(String fileName)
            throws IOException
    {
        return Files.newInputStream(Paths.get(getFileName(fileName)));
    }

}
