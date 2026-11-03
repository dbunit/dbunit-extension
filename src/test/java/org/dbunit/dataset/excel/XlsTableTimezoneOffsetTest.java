package org.dbunit.dataset.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Paths;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(SoftAssertionsExtension.class)
class XlsTableTimezoneOffsetTest
{
    private static final String EXCEL_SPREADSHEET =
            "/excel/XlsTableTimezoneOffset.xlsx";
    private static final String TABLE_NAME = "xxx_tz_offset_test";
    private static final String FAIL_MSG =
            "row '%d' column '%s' doesn't match.";

    private ITable table;

    /**
     * Simple test that loads the specified excel file and makes sure that the
     * date is correct whatever the JVM's default time zone is. This test only
     * makes sense when the default time zone is NOT UTC (i.e. has an offset
     * different from 0), otherwise the test would succeed even if the bug is
     * still present. Thus, the test sets the default time zone itself, once
     * for a zone east of UTC, one west of it, one with a half-hour offset and
     * the farthest one east, and restores the original afterward, so it does
     * not depend on how the JVM was started.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Europe/Berlin", "America/Chicago", "Asia/Kolkata",
            "Pacific/Kiritimati"})
    void testTimestampTzOffsets_withNonUtcTimezone_readsCorrectTimestampValues(final String zoneId, final SoftAssertions softly) throws Exception
    {
        final TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        try
        {
            checkTimestampValues(zoneId, softly);
        } finally
        {
            TimeZone.setDefault(original);
        }
    }

    private void checkTimestampValues(final String zoneId, final SoftAssertions softly)
            throws Exception
    {
        // An unknown zone id becomes GMT without a word, which would hide the bug.
        assertThat(TimeZone.getDefault().getRawOffset()).as(
                "The zone %s must have an offset other than 0, or the test cannot show the bug.",
                zoneId).isNotZero();

        final URL spreadsheetUrl = getClass().getResource(EXCEL_SPREADSHEET);
        final URI spreadsheetUri = spreadsheetUrl.toURI();
        final File spreadsheetFile = Paths.get(spreadsheetUri).toFile();
        final XlsDataSet xlsDataSet = new XlsDataSet(spreadsheetFile);

        table = xlsDataSet.getTable(TABLE_NAME);

        // The values returned by ITable.getValue() should match the content of
        // the actual file, regardless of the JVM's default timezone

        checkStringValue(softly, 0, "id", "1");
        checkDateValue(softly, 0, "ts", "2015-03-14 00:00:00");
        checkStringValue(softly, 1, "id", "2");
        checkDateValue(softly, 1, "ts", "2015-03-18 02:00:00");
        checkStringValue(softly, 2, "id", "3");
        checkDateValue(softly, 2, "ts", "2015-12-19 23:00:00");
        softly.assertAll();
    }

    private void checkStringValue(final SoftAssertions softly, final int row,
            final String column, final String expected) throws DataSetException
    {
        final String failMsg = String.format(FAIL_MSG, row, column);
        final String value = getValueAsString(row, column);
        softly.assertThat(value).as(failMsg).isEqualTo(expected);
    }

    private void checkDateValue(final SoftAssertions softly, final int row,
            final String column, final String expected) throws DataSetException
    {
        final String failMsg = String.format(FAIL_MSG, row, column);
        final String value = getValueAsDate(row, column);
        softly.assertThat(value).as(failMsg).isEqualTo(expected);
    }

    private String getValueAsString(final int row, final String column)
            throws DataSetException
    {
        final Object value = table.getValue(row, column);
        return value.toString();
    }

    private String getValueAsDate(final int row, final String column)
            throws DataSetException
    {
        // The format takes the default time zone when it is created, so it
        // is created for each value, after the test has set the zone.
        final DateFormat dateFormat =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateFormat.format(table.getValue(row, column));
    }
}
