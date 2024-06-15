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
package org.dbunit.testutil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.dbunit.assertion.DefaultFailureHandler;
import org.dbunit.assertion.JUnitFailureFactory;
import org.dbunit.assertion.SimpleAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple utility to compare file or stream data with each other.
 * 
 * <p>
 * From "Dale E Martin" dmartin@c.. Date Thursday, March 14, 2002 2:42 pm To
 * junit@yahoogroups.com Subject [junit] file assert, a starting point
 *
 * OK, this isn't rocket science or anything but it's working for me. What it is
 * is a couple of assert methods that compare files, so your expected results
 * can be located in a file.
 *
 * I did not use the diff library as the docs are in French, which I have not
 * studied for about 15 years and am not ready to pick back up ;-)
 *
 * I declare this code to be open to the public domain and anyone may do
 * anything they like with it. Before inclusion in JUnit I'm sure you'll need to
 * tweak it some, but it's working for my needs and maybe it will help someone
 * else.
 *
 * Later, Dale
 * </p>
 * 
 * 
 * @author Dale E Martin
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since ? (pre 2.2)
 */
public class FileAsserts
{
    private static final DefaultFailureHandler FAILURE_HANDLER =
            new DefaultFailureHandler();
    static
    {
        FAILURE_HANDLER.setFailureFactory(new JUnitFailureFactory());
    }
    private static final SimpleAssert ASSERT =
            new SimpleAssert(FAILURE_HANDLER);

    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(FileAsserts.class);

    private static String processOneLine(int lineNumber,
            BufferedReader expectedData, BufferedReader actualData)
            throws IOException
    {
        if (logger.isDebugEnabled())
            logger.debug(
                    "processOneLine(lineNumber={}, expectedData={}, actualData={}) - start",
                    new Object[] {Integer.valueOf(lineNumber), expectedData,
                            actualData});

        String problem = null;
        String expectedLine = expectedData.readLine();
        if (!actualData.ready())
        {
            problem = "at line " + lineNumber + ", expected:\n" + expectedLine
                    + "\n"
                    + "but actual file was not ready for reading at this line.";
        } else
        {
            String actualLine = actualData.readLine();
            if (!expectedLine.equals(actualLine))
            {
                // Uh oh, they did not match.
                problem = "at line " + lineNumber
                        + " there was a mismatch.  Expected:\n";
                int maxLen = expectedLine.length();
                if (expectedLine.length() > actualLine.length())
                {
                    maxLen = actualLine.length();
                }
                int startOffset = 0;
                for (int i = 0; i < maxLen; i++)
                {
                    if (expectedLine.charAt(i) != actualLine.charAt(i))
                    {
                        startOffset = i;
                        break;
                    }
                }
                problem += expectedLine.substring(startOffset) + "\n"
                        + "actual was:\n" + actualLine.substring(startOffset)
                        + "\n";
            }
        }
        return problem;
    }

    public static void assertEquals(BufferedReader expected,
            BufferedReader actual) throws Exception
    {
        logger.debug("assertEquals(expected={}, actual={}) - start", expected,
                actual);

        ASSERT.assertNotNull(expected);
        ASSERT.assertNotNull(actual);

        String problem = null;
        try
        {
            int lineCounter = 0;
            while (expected.ready() && problem == null)
            {
                problem = processOneLine(lineCounter, expected, actual);
                lineCounter++;
            }
        } finally
        {
            expected.close();
            actual.close();
        }

        if (problem != null)
        {
            ASSERT.fail(problem);
        }
    }

    public static void assertEquals(InputStream expected, File actual)
            throws Exception
    {
        logger.debug("assertEquals(expected={}, actual={}) - start", expected,
                actual);

        ASSERT.assertNotNull(expected);
        ASSERT.assertNotNull(actual);

        ASSERT.assertTrue(actual.canRead());

        BufferedReader expectedData =
                new BufferedReader(new InputStreamReader(expected));

        BufferedReader actualData = new BufferedReader(
                new InputStreamReader(new FileInputStream(actual)));
        assertEquals(expectedData, actualData);
    }

    public static void assertEquals(File expected, File actual) throws Exception
    {
        logger.debug("assertEquals(expected={}, actual={}) - start", expected,
                actual);

        ASSERT.assertNotNull(expected);
        ASSERT.assertNotNull(actual);

        ASSERT.assertTrue(expected.canRead());
        ASSERT.assertTrue(actual.canRead());

        BufferedReader expectedData = new BufferedReader(
                new InputStreamReader(new FileInputStream(expected)));
        BufferedReader actualData = new BufferedReader(
                new InputStreamReader(new FileInputStream(actual)));
        assertEquals(expectedData, actualData);
    }
}
