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

package org.dbunit.dataset.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.URL;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.dataset.common.handlers.EscapeHandler;
import org.dbunit.dataset.common.handlers.IllegalInputCharacterException;
import org.dbunit.dataset.common.handlers.IsAlnumHandler;
import org.dbunit.dataset.common.handlers.Pipeline;
import org.dbunit.dataset.common.handlers.PipelineException;
import org.dbunit.dataset.common.handlers.QuoteHandler;
import org.dbunit.dataset.common.handlers.SeparatorHandler;
import org.dbunit.dataset.common.handlers.TransparentHandler;
import org.dbunit.dataset.common.handlers.WhitespacesHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2 (Sep 12, 2004)
 */
public class CsvParserImpl implements CsvParser {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CsvParserImpl.class);

    private Pipeline pipeline;

    public CsvParserImpl() {
        resetThePipeline();
    }

    private void resetThePipeline() {
        logger.debug("resetThePipeline() - start");

        pipeline = new Pipeline();
        getPipeline().putFront(SeparatorHandler.ENDPIECE());
        getPipeline().putFront(EscapeHandler.ACCEPT());
        getPipeline().putFront(IsAlnumHandler.QUOTE());
        getPipeline().putFront(QuoteHandler.QUOTE());
        getPipeline().putFront(EscapeHandler.ESCAPE());
        getPipeline().putFront(WhitespacesHandler.IGNORE());
        getPipeline().putFront(TransparentHandler.IGNORE());
    }

    public List parse(String csv) throws PipelineException, IllegalInputCharacterException {
        logger.debug("parse(csv={}) - start", csv);

        getPipeline().resetProducts();
        CharacterIterator iterator = new StringCharacterIterator(csv);
        for (char c = iterator.first(); c != CharacterIterator.DONE; c = iterator.next()) {
            getPipeline().handle(c);
        }
        getPipeline().noMoreInput();
        getPipeline().thePieceIsDone();
        return getPipeline().getProducts();
    }

    public List parse(File file) throws IOException, CsvParserException {
        logger.debug("parse(file={}) - start", file);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        try {
            return parse(reader, file.getAbsolutePath().toString());
        }
        finally {
            reader.close();
        }
    }
    
    public List parse(URL url) throws IOException, CsvParserException {
        logger.debug("parse(url={}) - start", url);

        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        try {
            return parse(reader, url.toString());
        }
        finally {
            reader.close();
        }
    }
    
    public List parse(Reader reader, String source) throws IOException, CsvParserException {
        logger.debug("parse(reader={}, source={}) - start", reader, source);

        LineNumberReader lineNumberReader = new LineNumberReader(reader);
        List rows = new ArrayList();
        List columnsInFirstLine = parseFirstLine(lineNumberReader, source, rows);
        parseTheData(columnsInFirstLine, lineNumberReader, rows);
        return rows;
    }

//    private List parseFirstLine(LineNumberReader lineNumberReader, File file, List rows) throws IOException, CsvParserException {
//        if(logger.isDebugEnabled())
//            logger.debug("parseFirstLine(lineNumberReader={}, file={}, rows={}) - start", 
//                new Object[]{lineNumberReader, file, rows} );
//
//    	return parseFirstLine(lineNumberReader, file.getAbsolutePath().toString(), rows);
//    }

    /** 
     * parse the first line of data from the given source 
     */
    private List parseFirstLine(LineNumberReader lineNumberReader, String source, List rows) throws IOException, CsvParserException {
        if(logger.isDebugEnabled())
            logger.debug("parseFirstLine(lineNumberReader={}, source={}, rows={}) - start", 
                new Object[]{lineNumberReader,source,rows});

        String firstLine = lineNumberReader.readLine();
        if (firstLine == null)
            throw new CsvParserException("The first line of " + source + " is null");

        final List columnsInFirstLine = parse(firstLine);
        rows.add(columnsInFirstLine);
        return columnsInFirstLine;
    }

    private void parseTheData(final List columnsInFirstLine, LineNumberReader lineNumberReader, List rows) throws IOException, CsvParserException {
        logger.debug("parseTheData(columnsInFirstLine={}, lineNumberReader={}, rows={}) - start", 
                new Object[] {columnsInFirstLine, lineNumberReader, rows} );

        int nColumns = columnsInFirstLine.size();
        List columns;
        while ((columns = collectExpectedNumberOfColumns(nColumns, lineNumberReader)) != null) {
            rows.add(columns);
        }
    }

    private List collectExpectedNumberOfColumns(int expectedNumberOfColumns, LineNumberReader lineNumberReader) throws IOException, CsvParserException  {
        if(logger.isDebugEnabled())
            logger.debug("collectExpectedNumberOfColumns(expectedNumberOfColumns={}, lineNumberReader={}) - start",
                String.valueOf(expectedNumberOfColumns), lineNumberReader);

        List columns = null;
        int columnsCollectedSoFar = 0;
        final StringBuilder buffer = new StringBuilder();
        String anotherLine = lineNumberReader.readLine();
        if(anotherLine == null)
            return null;
        boolean shouldProceed = false;
        while (columnsCollectedSoFar < expectedNumberOfColumns) {
            try {
                buffer.append(anotherLine);
                columns = parse(buffer.toString());
                columnsCollectedSoFar = columns.size();
            } catch (IllegalStateException e) {
                resetThePipeline();
                anotherLine = lineNumberReader.readLine();
                if(anotherLine == null)
                    break;
                buffer.append("\n");
                shouldProceed = true;
            }
            if (!shouldProceed)
                break;
        }
        if (columnsCollectedSoFar != expectedNumberOfColumns) {
            String message = new StringBuilder("Expected ").append(expectedNumberOfColumns)
                    .append(" columns on line ").append(lineNumberReader.getLineNumber())
                    .append(", got ").append(columnsCollectedSoFar).append(". Offending line: ").append(buffer).toString();
            throw new CsvParserException(message);
        }
        return columns;
    }

    Pipeline getPipeline() {
        logger.debug("getPipeline() - start");

        return pipeline;
    }

    void setPipeline(Pipeline pipeline) {
        logger.debug("setPipeline(pipeline={}) - start", pipeline);

        this.pipeline = pipeline;
    }
}
