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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Parser which parses Oracle SQLLoader files.
 * 
 * @author Stephan Strittmatter (stritti AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class SqlLoaderControlParserImpl implements SqlLoaderControlParser {

    public static final char SEPARATOR_CHAR = ';';
    
    /** The pipeline. */
    private Pipeline pipeline;

    private String tableName;

//    private String fieldTerminator;
//
//    private String fieldEnclosure;

    private boolean hasTrailingNullCols;

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SqlLoaderControlParserImpl.class);

    
    /**
     * The Constructor.
     */
    public SqlLoaderControlParserImpl() {

        resetThePipeline();

    }

    /**
     * Reset the pipeline.
     */
    private void resetThePipeline() {
        logger.debug("resetThePipeline() - start");
        
        this.pipeline = new Pipeline();
        this.pipeline.getPipelineConfig().setSeparatorChar(SEPARATOR_CHAR);
        
        //TODO add this.fieldEnclosure
        getPipeline().putFront(SeparatorHandler.ENDPIECE());
        getPipeline().putFront(EscapeHandler.ACCEPT());
        getPipeline().putFront(IsAlnumHandler.QUOTE());
        getPipeline().putFront(QuoteHandler.QUOTE());
        getPipeline().putFront(EscapeHandler.ESCAPE());
        getPipeline().putFront(WhitespacesHandler.IGNORE());
        getPipeline().putFront(TransparentHandler.IGNORE());

    }

    /**
     * Parse.
     * 
     * @param csv the csv
     * 
     * @return the list
     * 
     * @throws IllegalInputCharacterException the illegal input character exception
     * @throws PipelineException the pipeline exception
     * 
     * @see org.dbunit.dataset.sqlloader.SqlLoaderControlParser#parse(java.lang.String)
     */
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

    /**
     * Parse.
     * 
     * @param url the URL
     * 
     * @return the list
     * 
     * @throws IOException the IO exception
     * @throws SqlLoaderControlParserException the oracle control parser exception
     * 
     * @see org.dbunit.dataset.sqlloader.SqlLoaderControlParser#parse(java.net.URL)
     */
    public List parse(URL url) throws IOException, SqlLoaderControlParserException {
        logger.debug("parse(url={}) - start", url);
        return parse(new File(url.toString()));
    }

    /**
     * Parse.
     * 
     * @param controlFile the source
     * 
     * @return the list of column names as Strings
     * 
     * @throws IOException the IO exception
     * @throws SqlLoaderControlParserException the oracle control parser exception
     * @see org.dbunit.dataset.sqlloader.SqlLoaderControlParser#parse(java.io.File)
     */
    public List parse(File controlFile) 
    throws IOException, SqlLoaderControlParserException 
    {
        logger.debug("parse(controlFile={}) - start", controlFile);

        FileInputStream fis = new FileInputStream(controlFile);

        FileChannel fc = fis.getChannel();

        MappedByteBuffer mbf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
        byte[] barray = new byte[(int) (fc.size())];
        mbf.get(barray);

        String lines = new String(barray); //one big string

        lines = lines.replaceAll("\r", ""); //unify to UNIX style to have easier regexp transformations.

        if (parseForRegexp(lines, "(LOAD\\sDATA).*") != null) {

        	String fileName = parseForRegexp(lines, ".*INFILE\\s'(.*?)'.*");
        	File dataFile = resolveFile(controlFile.getParentFile(), fileName);

            this.tableName = parseForRegexp(lines, ".*INTO\\sTABLE\\s(.*?)\\s.*");

//            this.fieldTerminator = parseForRegexp(lines, ".*TERMINATED BY [\"|'](.*?)[\"|'].*");
//
//            this.fieldEnclosure = parseForRegexp(lines, ".*OPTIONALLY ENCLOSED BY '(.*?)'.*");

            if (parseForRegexp(lines, ".*(TRAILING NULLCOLS).*") != "") {
                this.hasTrailingNullCols = true;
            }
            else {
                this.hasTrailingNullCols = false;
            }
            
            List rows = new ArrayList();
            List columnList = parseColumns(lines, rows);

            LineNumberReader lineNumberReader =
                new LineNumberReader(new InputStreamReader(new FileInputStream(dataFile)));
            try {
                parseTheData(columnList, lineNumberReader, rows);
            }
            finally {
                lineNumberReader.close();
            }

            return rows;
        }
        else {
            throw new SqlLoaderControlParserException("Control file "
                    + controlFile + " not starting using 'LOAD DATA'");
        }
    }

    private File resolveFile(File parentDir, String fileName) {
    	// Initially assume that we have an absolute fileName
    	File dataFile = new File(fileName);
    	
    	// If fileName was not absolute build it using the given parent
    	if(!dataFile.isAbsolute()) {
    		fileName = fileName.replaceAll("\\\\", "/");
    		// remove "./" characters from name at the beginning if needed
    		if(fileName.startsWith("./")){
    			fileName = fileName.substring(2);
    		}
    		// remove "." character from name at the beginning if needed
    		if(fileName.startsWith(".")){
    			fileName = fileName.substring(1);
    		}
    		dataFile = new File(parentDir, fileName);
    	}
    	return dataFile;
	}

	protected String parseForRegexp(String controlFileContent, String regexp) 
    throws IOException 
    {
        logger.debug("parseForRegexp(controlFileContent={}, regexp={}) - start", controlFileContent, regexp);

        if (controlFileContent == null) {
            throw new NullPointerException("control file has no content");
        }

        final Pattern pattern = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        final Matcher matches = pattern.matcher(controlFileContent);

        if (matches.find()) {
            String inFileLine = matches.group(1);

            return inFileLine;
        }
        else {
            return null;
        }
    }

    /**
     * parse the first line of data from the given source.
     * 
     * @param rows the rows
     * @param lineNumberReader the line number reader
     * @param controlFile the source
     * 
     * @return the list of column names as Strings
     * 
     * @throws IOException the IO exception
     * @throws SqlLoaderControlParserException the oracle control parser exception
     */
    private List parseColumns(String controlFileContent, List rows) throws IOException,
    SqlLoaderControlParserException 
    {
        logger.debug("parseColumns(controlFileContent={}, rows={}) - start", controlFileContent, rows);

        List columnList;

        final Pattern pattern =
            Pattern
            .compile(".*FIELDS\\s.*\\(\\n(.*?)\\n\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        final Matcher matches = pattern.matcher(controlFileContent);

        if (matches.find()) {
            String columnFragment = matches.group(1);
            //firstLine = firstLine.replaceAll("(\n|\r)", "");

            columnList = new ArrayList();

            columnFragment = columnFragment.replaceAll("\".*?\"", "");
            columnFragment = columnFragment.replaceAll("\n", "");

            StringTokenizer tok = new StringTokenizer(columnFragment, ",");

            while (tok.hasMoreElements()) {

                String col = (String) tok.nextElement();
                col = parseForRegexp(col, ".*^([a-zA-Z0-9_]*)\\s").trim(); //column is the first part.
                columnList.add(col);
            }

            //columnsInFirstLine = parse(firstLine);
            rows.add(columnList);
        }

        else {
            columnList = null;
        }

        return columnList;
    }

    /**
     * Parses the the data.
     * 
     * @param rows the rows
     * @param columnList the columns in first line
     * @param lineNumberReader the line number reader
     * 
     * @throws IOException the IO exception
     * @throws SqlLoaderControlParserException the oracle control parser exception
     */
    private void parseTheData(final List columnList, LineNumberReader lineNumberReader, List rows)
    throws IOException, SqlLoaderControlParserException 
    {
        if(logger.isDebugEnabled())
            logger.debug("parseTheData(columnList={}, lineNumberReader={}, rows={}) - start", 
                    new Object[] {columnList, lineNumberReader, rows} );

        int nColumns = columnList.size();
        List columns;
        while ((columns = collectExpectedNumberOfColumns(nColumns, lineNumberReader)) != null) {
            rows.add(columns);
        }
    }

    /**
     * Collect expected number of columns.
     * 
     * @param expectedNumberOfColumns the expected number of columns
     * @param lineNumberReader the line number reader
     * 
     * @return the list
     * 
     * @throws IOException the IO exception
     * @throws SqlLoaderControlParserException the oracle control parser exception
     */
    private List collectExpectedNumberOfColumns(
            int expectedNumberOfColumns,
            LineNumberReader lineNumberReader) throws IOException, SqlLoaderControlParserException 
    {
        if(logger.isDebugEnabled())
            logger.debug("collectExpectedNumberOfColumns(expectedNumberOfColumns={}, lineNumberReader={}) - start", 
                String.valueOf(expectedNumberOfColumns), lineNumberReader);

        String anotherLine = lineNumberReader.readLine();
        if (anotherLine == null) {
            return null;
        }

        List columns = null;
        int columnsCollectedSoFar = 0;
        final StringBuilder buffer = new StringBuilder();
        boolean shouldProceed = false;
        while (columnsCollectedSoFar < expectedNumberOfColumns) {
            try {
                buffer.append(anotherLine);
                columns = parse(buffer.toString());
                columnsCollectedSoFar = columns.size();
            }
            catch (IllegalStateException e) {
                resetThePipeline();
                anotherLine = lineNumberReader.readLine();
                if (anotherLine == null) {
                    break;
                }
                buffer.append("\n");
                shouldProceed = true;
            }
            if (!shouldProceed) {
                break;
            }
        }
        
        if (columnsCollectedSoFar != expectedNumberOfColumns) {
            if (this.hasTrailingNullCols) {
                columns.add(SqlLoaderControlProducer.NULL);
            }
            else {

                String message =
                    new StringBuilder("Expected ")
                        .append(expectedNumberOfColumns).append(" columns on line ")
                        .append(lineNumberReader.getLineNumber()).append(", got ")
                        .append(columnsCollectedSoFar).append(". Offending line: ").append(buffer)
                        .toString();
                throw new SqlLoaderControlParserException(message);
            }
        }
        return columns;
    }

    /**
     * Gets the pipeline.
     * 
     * @return the pipeline
     */
    Pipeline getPipeline() 
    {
        return this.pipeline;
    }

    /**
     * Sets the pipeline.
     * 
     * @param pipeline the pipeline
     */
    void setPipeline(Pipeline pipeline) 
    {
        this.pipeline = pipeline;
    }

    public String getTableName() 
    {
        return this.tableName;
    }
}
