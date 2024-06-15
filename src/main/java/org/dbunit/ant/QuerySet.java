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

package org.dbunit.ant;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.types.FilterSet;
import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.QueryDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This element is a container for Queries. It facilitates reuse
 * through references. Using Ant 1.6 and greater, references can be
 * defined in a single build file and <i>import</i>ed into many others.
 * An example of where this is useful follows:
 * <p>
 * In our database
 * we have INDIVIDUALS which must have an associated NAME_INFO and
 * at least one IND_ADDRESS. The developer creating a dataset for
 * his/her tests probably won't know all the details of what relationships are
 * expected, and if he did, its an error prone and repetitive task
 * to create the correct SQL for entities in each dataset.
 * Missing a related table, not only creates invalid data for your tests,
 * but also is likely to cause DBUnit setUp() failures from foreign key
 * constraint violation errors.
 * (example: If a previous test had inserted INDIVIDUALS
 * and NAME_INFO and my test tries to delete only the INDIVIDUALS, the
 * NAME_INFO.IND_ID constraint would be violated)
 * <p>
 * <p>
 * Each queryset is internally converted to a <code>QueryDataSet</code> and then
 * combined using a <code>CompositeDataSet</code>. This means that you can use
 * more than one <code>query</code> element for any given table provided they
 * are nested within separate <code>queryset</code>s.
 * <p>
 * Usage:
 *
 * <pre>
 * &lt;!-- ======== Define the reusable reference ========== --&gt;
 *
 * &lt;queryset id="individuals"&gt;
 *    &lt;query name="INDIVIDUALS" sql="
 *      SELECT * FROM INDIVIDUALS WHERE IND_ID IN (@subQuery@)"/&gt;
 *
 *    &lt;query name="NAME_INFO" sql="
 *      SELECT B.* FROM INDIVIDUALS A, NAME_INFO B
 *      WHERE A.IND_ID IN (@subQuery@)
 *      AND B.IND_ID = A.IND_ID"/&gt;
 *
 *    &lt;query name="IND_ADDRESSES" sql="
 *      SELECT B.* FROM INDIVIDUALS A, IND_ADDRESSES B
 *      WHERE A.IND_ID IN (@subQuery@)
 *      AND B.IND_ID = A.IND_ID"/&gt;
 * &lt;/queryset&gt;
 *
 * &lt;!-- ========= Use the reference ====================== --&gt;
 *
 * &lt;dbunit driver="${jdbcDriver}"
 *     url="${jdbcURL}" userid="${jdbcUser}" password="${jdbcPassword}"&gt;
 *   &lt;export dest="${dest}"&gt;
 *   &lt;queryset refid="individuals"&gt;
 *      &lt;filterset&gt;
 *        &lt;filter token="subQuery" value="
 *          SELECT IND_ID FROM INDIVIDUALS WHERE USER_NAME = 'UNKNOWN'"/&gt;
 *      &lt;/filterset&gt;
 *   &lt;/queryset&gt;
 *
 *   &lt;/export&gt;
 * &lt;/dbunit&gt;
 *
 * </pre>
 *
 * @author Lenny Marks lenny@aps.org
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0 (Sep. 13 2004)
 */
public class QuerySet extends ProjectComponent
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(QuerySet.class);

    private String id;
    private String refid;
    private List queries = new ArrayList();
    private List filterSets = new ArrayList();

    private static String ERR_MSG =
            "Cannot specify 'id' and 'refid' attributes together in queryset.";

    public QuerySet() {
        super();
    }

    public void addQuery(final Query query) {
        logger.debug("addQuery(query={}) - start", query);

        queries.add(query);
    }

    public void addFilterSet(final FilterSet filterSet) {
        logger.debug("addFilterSet(filterSet={}) - start", filterSet);

        filterSets.add(filterSet);
    }

    public String getId() {
        return id;
    }

    public String getRefid() {
        return refid;
    }

    public void setId(final String string) throws BuildException {
        logger.debug("setId(string={}) - start", string);

        if(refid != null) throw new BuildException(ERR_MSG);
        id = string;
    }

    public void setRefid(final String string) throws BuildException {
        logger.debug("setRefid(string={}) - start", string);

        if(id != null) throw new BuildException(ERR_MSG);
        refid = string;
    }

    public List getQueries() {
        logger.debug("getQueries() - start");

        final Iterator i = queries.iterator();
        while(i.hasNext()) {
            final Query query = (Query)i.next();
            replaceTokens(query);
        }

        return queries;

    }

    private void replaceTokens(final Query query) {
        logger.debug("replaceTokens(query={}) - start", query);

        final Iterator i = filterSets.iterator();
        while(i.hasNext()) {
            final FilterSet filterSet = (FilterSet)i.next();
            query.setSql(filterSet.replaceTokens(query.getSql()));
        }
    }


    public void copyQueriesFrom(final QuerySet referenced) {
        logger.debug("copyQueriesFrom(referenced={}) - start", referenced);

        final Iterator i = referenced.queries.iterator();
        while(i.hasNext()) {
            addQuery((Query)i.next());
        }
    }

    public QueryDataSet getQueryDataSet(final IDatabaseConnection connection)
            throws SQLException, AmbiguousTableNameException
    {
        logger.debug("getQueryDataSet(connection={}) - start", connection);

        //incorporate queries from referenced query-set
        final String refid = getRefid();
        if(refid != null) {
            final QuerySet referenced = (QuerySet)getProject().getReference(refid);
            copyQueriesFrom(referenced);
        }

        final QueryDataSet partialDataSet = new QueryDataSet(connection);

        final Iterator queriesIter = getQueries().iterator();
        while(queriesIter.hasNext()) {
            final Query query = (Query)queriesIter.next();
            partialDataSet.addTable(query.getName(), query.getSql());
        }

        return partialDataSet;

    }

}