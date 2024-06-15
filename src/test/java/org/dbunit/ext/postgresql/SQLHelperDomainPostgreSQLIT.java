package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.StringReader;
import java.sql.Statement;
import java.util.Objects;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xml.sax.InputSource;

/**
 * Testcase for Postgresql to check SQL CREATE DOMAIN with FlatXmlDataSetBuilder
 * to insert a dataset with SQL Domains (user-def-types).
 * 
 * @author Philipp S. (Unwissender2009)
 * @since Nov 23, 2009
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class SQLHelperDomainPostgreSQLIT
{
    private IDatabaseConnection _connection;

    private static final String xmlData = "<?xml version=\"1.0\"?>"
            + "<dataset>" + "<T1 PK=\"1\" STATE=\"is_blabla\"/>" + "</dataset>";

    @BeforeEach
    protected void setUp() throws Exception
    {
        // Load active postgreSQL profile and connection from Maven pom.xml.
        _connection = DatabaseEnvironment.getInstance().getConnection();
        final Statement stat = _connection.getConnection().createStatement();
        // DELETE SQL DOMAIN and Table with DOMAINS
        stat.execute("DROP TABLE  IF EXISTS T1;");
        stat.execute("DROP DOMAIN IF EXISTS MYSTATE;");
        stat.execute("DROP DOMAIN IF EXISTS MYPK;");

        // Create SQL DOMAIN and Table with DOMAINS
        stat.execute(
                "CREATE DOMAIN MYSTATE AS VARCHAR(20) DEFAULT 'is_Valid';");
        stat.execute("CREATE DOMAIN MYPK AS INTEGER DEFAULT 0;");
        stat.execute(
                "CREATE TABLE T1 (PK MYPK,STATE MYSTATE,PRIMARY KEY (PK));");
        stat.close();
        _connection.close();
        _connection = DatabaseEnvironment.getInstance().getConnection();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        if (!Objects.isNull(_connection))
        {
            final Statement cleanStat =
                    _connection.getConnection().createStatement();
            // DELETE SQL OID tables
            cleanStat.execute("DROP TABLE IF EXISTS T1;");
            cleanStat.close();
            _connection.close();

            _connection = null;
        }
    }

    @Test
    void xtestDomainDataTypes() throws Exception
    {
        assertThat(_connection).as("didn't get a connection").isNotNull();

        try
        {
            final ReplacementDataSet dataSet =
                    new ReplacementDataSet(new FlatXmlDataSetBuilder()
                            .build(new InputSource(new StringReader(xmlData))));
            dataSet.addReplacementObject("[NULL]", null);
            dataSet.setStrictReplacement(true);

            // THE TEST -> hopefully with no exception!!!
            DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);

            // Check Types.
            for (int i = 0; i < _connection.createDataSet()
                    .getTableMetaData("T1").getColumns().length; i++)
            {
                final Column c = _connection.createDataSet()
                        .getTableMetaData("T1").getColumns()[i];

                if (c.getSqlTypeName().compareTo("mypk") == 0)
                {
                    assertThat(c.getDataType().getSqlType())
                            .isEqualTo(java.sql.Types.INTEGER);
                } else if (c.getSqlTypeName().compareTo("mystate") == 0)
                {
                    assertThat(c.getDataType().getSqlType())
                            .isEqualTo(java.sql.Types.VARCHAR);
                } else
                {
                    fail("we should not be here");
                }
            }
        } catch (final Exception e)
        {
            assertThat("" + e).isEqualTo(
                    "DatabaseOperation.CLEAN_INSERT... no exception");
        }
    }
}
