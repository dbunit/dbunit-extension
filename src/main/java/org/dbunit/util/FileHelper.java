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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * Utility that provides some general methods for working with {@link File} objects.
 * 
 * @author gommma
 * @version $Revision$
 * @since 2.3.0
 */
public class FileHelper
{
	private static Logger logger = LoggerFactory.getLogger(FileHelper.class);

	private FileHelper(){
	}
	
	/**
     * Recursively deletes the given directory
     * @param directory The directory to delete
	 * @param failOnError If an exception should be thrown in case the deletion did not work.
	 */
	public static void deleteDirectory(File directory, boolean failOnError) {
	    boolean success = deleteDirectory(directory);
	    if(!success){
	        throw new RuntimeException("Failed to delete directory " + directory);
	    }
	}

	/**
	 * Recursively deletes the given directory
	 * @param directory The directory to delete
	 * @return <code>true</code> if the deletion was successfully.
	 */
	public static boolean deleteDirectory(File directory)
	{
		if(!directory.isDirectory()) {
			logger.warn("The directory '" + directory + "' does not exist. Will return without delete.");
			return false;
		}
		
		// First we must delete all files in the directory
		File[] containedFiles = directory.listFiles();
		for (int i = 0; i < containedFiles.length; i++) {
			File currentFile = containedFiles[i];
			if(currentFile.isDirectory()) {
				// First delete children recursively
				deleteDirectory(currentFile);
			}
			else {
				// Delete the file itself
				boolean success = currentFile.delete();
				if(!success){
					logger.warn("Failed to delete file '" + currentFile + "'");
				}
			}		
		}
		// Finally delete the directory itself
		boolean success = directory.delete();
		if(!success){
			logger.warn("Failed to delete file '" + directory + "'");
		}
		return success;
	}

	public static InputSource createInputSource(File file) throws MalformedURLException
	{
        String uri = file/*.getAbsoluteFile()*/.toURI().toURL().toString();
        InputSource source = new InputSource(uri);
        return source;
	}
	
	
    /**
     * Copy file.
     * 
     * @param srcFile the src file
     * @param destFile the dest file
     * @throws IOException 
     */
    public static void copyFile(File srcFile, File destFile) throws IOException 
    {
        logger.debug("copyFile(srcFile={}, destFile={}) - start", srcFile, destFile);

        // Create channel on the source
        FileChannel srcChannel = new FileInputStream(srcFile).getChannel();

        // Create channel on the destination
        FileChannel dstChannel = new FileOutputStream(destFile).getChannel();

        try {
            // Copy file contents from source to destination
            dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
        }
        finally {
            // Close the channels
            srcChannel.close();
            dstChannel.close();
        }
    }

    /**
     * Get a list of Strings from a given file.
     * Uses the default encoding of the current platform.
     * 
     * @param theFile the file to be read
     * @return a list of Strings, each one representing one line from the given file
     * @throws IOException
     */
    public static List readLines(File theFile) throws IOException 
    {
        logger.debug("readLines(theFile={}) - start", theFile);

        InputStream tableListStream = new FileInputStream(theFile);
        try {
            List orderedNames = new ArrayList();
            BufferedReader reader = new BufferedReader(new InputStreamReader(tableListStream));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String table = line.trim();
                if (table.length() > 0) {
                    orderedNames.add(table);
                }
            }
            return orderedNames;
        }
        finally {
            tableListStream.close();
        }
    }

}
