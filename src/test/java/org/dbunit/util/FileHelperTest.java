/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHelperTest
{
    @Test
    void testCopyFile_withRegularFiles_copiesContent(
            @TempDir final File tempDir) throws IOException
    {
        final File srcFile = new File(tempDir, "source.txt");
        Files.write(srcFile.toPath(),
                "content".getBytes(StandardCharsets.UTF_8));
        final File destFile = new File(tempDir, "destination.txt");

        FileHelper.copyFile(srcFile, destFile);

        assertThat(destFile)
                .as("The destination file should contain the source file's bytes.")
                .hasBinaryContent("content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testCopyFile_destinationUnwritable_sourceNotLocked(
            @TempDir final File tempDir) throws IOException
    {
        final File srcFile = new File(tempDir, "source.txt");
        Files.write(srcFile.toPath(),
                "content".getBytes(StandardCharsets.UTF_8));
        // A directory is a portable way to make the destination unopenable
        // for writing.
        final File destDir = new File(tempDir, "destination-is-a-directory");
        assertThat(destDir.mkdir())
                .as("Setup: the destination directory must be created.")
                .isTrue();

        assertThatThrownBy(() -> FileHelper.copyFile(srcFile, destDir))
                .as("Opening a directory for writing must fail.")
                .isInstanceOf(IOException.class);

        assertThat(srcFile.delete())
                .as("The source file must not be locked by a leaked channel after the failed copy.")
                .isTrue();
    }
}
