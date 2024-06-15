package org.dbunit.database;

import java.sql.SQLException;

import org.dbunit.database.search.TablesDependencyHelper;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.search.SearchException;

public abstract class AbstractImportedKeysFilteredByPKsTestCase
        extends AbstractSearchCallbackFilteredByPKsTestCase
{

    @Override
    protected IDataSet getDataset()
            throws SQLException, SearchException, DataSetException
    {
        final IDataSet dataset =
                TablesDependencyHelper.getDataset(getConnection(), getInput());
        return dataset;
    }

}
