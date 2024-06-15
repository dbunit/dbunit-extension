/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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
package org.dbunit.database.search;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.BeforeEach;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 28, 2005
 */
public class ImportNodesFilterSearchCallbackTest
        extends AbstractMetaDataBasedSearchCallbackTestCase
{

    public static final String SQL_FILE = "hypersonic_import.sql";

    public static final String[][] SINGLE_INPUT =
            new String[][] {new String[] {"A"}, new String[] {"B"},
                    new String[] {"C"}, new String[] {"D"}, new String[] {"E"},
                    new String[] {"F"}, new String[] {"G"}, new String[] {"H"}};

    public static final String[][] COMPOUND_INPUT =
            new String[][] {new String[] {"A", "H"}, new String[] {"H", "A"},
                    new String[] {"A", "B"}, new String[] {"B", "A"},
                    new String[] {"B", "A", "A", "B", "D", "G"}};

    public static final String[][] SINGLE_OUTPUT = new String[][] {
            new String[] {"G", "D", "A"},
            new String[] {"G", "D", "A", "F", "C", "E", "H", "B"},
            new String[] {"G", "D", "A", "F", "C"},
            new String[] {"A", "G", "D"}, new String[] {"G", "D", "A", "E"},
            new String[] {"A", "G", "D", "F"}, new String[] {"D", "A", "G"},
            new String[] {"H"},};

    public static final String[][] COMPOUND_OUTPUT =
            new String[][] {new String[] {"G", "D", "A", "H"},
                    new String[] {"H", "G", "D", "A"},
                    new String[] {"G", "D", "A", "F", "C", "E", "H", "B"},
                    new String[] {"G", "D", "A", "F", "C", "E", "H", "B"},
                    new String[] {"G", "D", "A", "F", "C", "E", "H", "B"}};

    @BeforeEach
    public void setup() throws Exception
    {
        super.setUpConnectionWithFile(SQL_FILE);
    }

    @Override
    protected String[][] getInput()
    {
        final String[][] input =
                new String[SINGLE_INPUT.length + COMPOUND_INPUT.length][];
        System.arraycopy(SINGLE_INPUT, 0, input, 0, SINGLE_INPUT.length);
        System.arraycopy(COMPOUND_INPUT, 0, input, SINGLE_INPUT.length,
                COMPOUND_INPUT.length);
        return input;
    }

    @Override
    protected String[][] getExpectedOutput()
    {
        final String[][] output =
                new String[SINGLE_OUTPUT.length + COMPOUND_OUTPUT.length][];
        System.arraycopy(SINGLE_OUTPUT, 0, output, 0, SINGLE_OUTPUT.length);
        System.arraycopy(COMPOUND_OUTPUT, 0, output, SINGLE_OUTPUT.length,
                COMPOUND_OUTPUT.length);
        return output;
    }

    @Override
    protected AbstractMetaDataBasedSearchCallback getCallback(
            final IDatabaseConnection connection)
    {
        return new ImportedKeysSearchCallback(connection);
    }

}
