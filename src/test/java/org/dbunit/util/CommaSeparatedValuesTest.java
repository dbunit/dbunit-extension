package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommaSeparatedValuesTest
{
    @Test
    void testSplit_valuesWithSurroundingWhitespace_returnsEachTrimmed()
    {
        assertThat(CommaSeparatedValues.split(" AUDIT_* ,HIBERNATE_SEQUENCES,  TEMP_? "))
                .as("Whitespace around a value is not part of it.")
                .containsExactly("AUDIT_*", "HIBERNATE_SEQUENCES", "TEMP_?");
    }

    @Test
    void testSplit_singleValue_returnsIt()
    {
        assertThat(CommaSeparatedValues.split("ACCOUNT")).containsExactly("ACCOUNT");
    }

    @Test
    void testSplit_emptyString_returnsOneEmptyValue()
    {
        assertThat(CommaSeparatedValues.split(""))
                .as("As String.split() does: an empty string is one empty value.")
                .containsExactly("");
    }

    @Test
    void testSplit_trailingCommas_dropsTheTrailingEmptyValues()
    {
        assertThat(CommaSeparatedValues.split("A,B,,"))
                .as("As String.split() does, trailing empty values are dropped.")
                .containsExactly("A", "B");
    }

    @Test
    void testSplit_emptyValueBetweenCommas_keepsItAsAnEmptyValue()
    {
        assertThat(CommaSeparatedValues.split("A,,B")).containsExactly("A", "", "B");
    }
}
