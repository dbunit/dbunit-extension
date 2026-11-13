package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.junit.jupiter.api.Test;

class TesterFieldsTest
{
    private final TesterFields testerFields = new TesterFields();

    @Test
    void testIsMarked_dbUnitTesterField_returnsTrue() throws Exception
    {
        assertThat(testerFields.isMarked(field(Fields.class, "marked"))).isTrue();
    }

    @Test
    void testIsMarked_dbUnitTestCaseField_returnsTrue() throws Exception
    {
        assertThat(testerFields.isMarked(field(Fields.class, "markedTestCase"))).isTrue();
    }

    @Test
    void testIsMarked_unmarkedTesterField_returnsFalse() throws Exception
    {
        assertThat(testerFields.isMarked(field(Fields.class, "unmarked")))
                .as("A tester field is only marked by an annotation.").isFalse();
    }

    @Test
    void testIsUnmarkedTester_nonStaticTesterField_returnsTrue() throws Exception
    {
        assertThat(testerFields.isUnmarkedTester(field(Fields.class, "unmarked"))).isTrue();
    }

    @Test
    void testIsUnmarkedTester_staticTesterField_returnsFalse() throws Exception
    {
        assertThat(testerFields.isUnmarkedTester(field(Fields.class, "staticTester")))
                .as("The 3.5.0 auto-scan only reads instance fields.").isFalse();
    }

    @Test
    void testIsUnmarkedTester_fieldOfAnotherType_returnsFalse() throws Exception
    {
        assertThat(testerFields.isUnmarkedTester(field(Fields.class, "notATester"))).isFalse();
    }

    @Test
    void testDeclaresMarkedField_markedFieldOnASuperclass_returnsTrue()
    {
        assertThat(testerFields.declaresMarkedField(SubclassOfFields.class))
                .as("The whole hierarchy is searched.").isTrue();
    }

    @Test
    void testDeclaresMarkedField_noMarkedField_returnsFalse()
    {
        assertThat(testerFields.declaresMarkedField(NoMarkedField.class)).isFalse();
    }

    @Test
    void testFindFirst_matchesInSubclassAndSuperclass_returnsTheMostDerivedClassesField()
    {
        final Field found = testerFields.findFirst(SubclassOfFields.class, testerFields::isMarked);

        assertThat(found.getName())
                .as("The nearest declaring class wins.").isEqualTo("subclassMarked");
    }

    @Test
    void testFindFirst_noMatch_returnsNull()
    {
        assertThat(testerFields.findFirst(NoMarkedField.class, testerFields::isMarked)).isNull();
    }

    @Test
    void testFindAll_matchesInSubclassAndSuperclass_returnsEveryOneMostDerivedFirst()
    {
        final List<Field> found = testerFields.findAll(SubclassOfFields.class,
                testerFields::isMarked);

        assertThat(found).extracting(Field::getName)
                .as("Every matching field of the hierarchy, the subclass's first.")
                .startsWith("subclassMarked")
                .containsExactlyInAnyOrder("subclassMarked", "marked", "markedTestCase");
    }

    @Test
    void testDeclaredBy_subclass_leavesOutSuperclassFields()
    {
        final List<Field> found = testerFields.declaredBy(SubclassOfFields.class,
                testerFields::isMarked);

        assertThat(found).extracting(Field::getName)
                .as("Only what the class declares itself.").containsExactly("subclassMarked");
    }

    private static Field field(final Class<?> clazz, final String name) throws Exception
    {
        return clazz.getDeclaredField(name);
    }

    static class Fields
    {
        @DbUnitTester
        IDatabaseTester marked;

        @DbUnitTestCase
        PrepAndExpectedTestCase markedTestCase;

        IDatabaseTester unmarked;

        static IDatabaseTester staticTester;

        String notATester;
    }

    static class SubclassOfFields extends Fields
    {
        @DbUnitTester
        IDatabaseTester subclassMarked;
    }

    static class NoMarkedField
    {
        IDatabaseTester unmarked;
    }
}
