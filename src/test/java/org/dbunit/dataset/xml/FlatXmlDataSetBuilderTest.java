package org.dbunit.dataset.xml;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.net.MalformedURLException;

import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

class FlatXmlDataSetBuilderTest
{
    private final FlatXmlDataSetBuilder sut = new FlatXmlDataSetBuilder();

    @Test
    void testBuild_File_$InTableName_Fails()
            throws MalformedURLException, DataSetException
    {
        final String fileName = "/xml/flatXmlDataSet$Test.xml";
        final InputStream inputStream =
                getClass().getResourceAsStream(fileName);
        assertThrows(DataSetException.class, () -> sut.build(inputStream));
    }
}
