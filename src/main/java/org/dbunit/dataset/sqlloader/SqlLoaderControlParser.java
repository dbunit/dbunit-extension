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
package org.dbunit.dataset.sqlloader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.dbunit.dataset.common.handlers.IllegalInputCharacterException;
import org.dbunit.dataset.common.handlers.PipelineException;

/**
 * Interface of Parser which parses Oracle SQLLoader files.
 * 
 * @author Stephan Strittmatter (stritti AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public interface SqlLoaderControlParser {

    /**
     * Parse.
     * 
     * @param file the file
     * @return the list
     * 
     * @throws IOException
     * @throws SqlLoaderControlParserException
     */
    List parse(File file) throws IOException, SqlLoaderControlParserException;

    /**
     * Parse.
     * 
     * @param url the URL
     * @return the list
     * 
     * @throws IOException
     * @throws SqlLoaderControlParserException
     */
    List parse(URL url) throws IOException, SqlLoaderControlParserException;

    /**
     * Parse.
     * 
     * @param csv the CSV data
     * @return the list
     * 
     * @throws IllegalInputCharacterException
     * @throws PipelineException
     */
    List parse(String csv) throws PipelineException, IllegalInputCharacterException;

    String getTableName();
}
