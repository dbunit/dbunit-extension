/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;

class SpringAnnotationLookupTest
{
    @Test
    void testFind_annotationOnMethodOnly_returnsMethodAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new MethodLevelTearDown(), "aTestMethod");

        final DbUnitTearDownOperation found = lookup.find(DbUnitTearDownOperation.class);

        assertThat(found).as("An annotation on the test method must be found.").isNotNull();
        assertThat(found.value()).as("The method's own operation must be returned.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_annotationOnMethodAndClass_methodWins() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ClassAndMethodLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("A method-level annotation must win over a class-level one.")
                .isEqualTo(DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFind_annotationOnClassOnly_returnsClassAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new ClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("A class-level annotation must be found when the method has none.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_annotationOnSuperclass_returnsInheritedAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new SubclassOfClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("A class-level annotation must be found through the superclass.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_composedAnnotationOnClass_returnsMetaAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("An annotation meta-present on a composed class annotation must be found.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_composedAnnotationOnMethod_returnsMetaAnnotation() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedMethodLevelTearDown(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("An annotation meta-present on a composed method annotation must be found.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_overridingMethodDeclaresNothing_ignoresOverriddenMethodsAnnotation()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new OverridingWithoutAnnotation(), "aTestMethod");

        assertThat(lookup.find(DbUnitExpected.class))
                .as("The method is searched on its own, as under DbUnitExtension, so an"
                        + " overriding test method must not inherit the @DbUnitExpected of the"
                        + " method it overrides.")
                .isNull();
    }

    @Test
    void testFind_nestedClassWithoutAnnotation_returnsEnclosingClassesAnnotation()
            throws Exception
    {
        final EnclosingWithTearDown enclosing = new EnclosingWithTearDown();
        final SpringAnnotationLookup lookup = lookupFor(enclosing.new Nested(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("A @Nested class inherits its enclosing class's annotations, as Spring"
                        + " inherits the rest of the enclosing configuration.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFind_nothingDeclared_returnsNull() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new NothingDeclared(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class))
                .as("With no annotation on the method or class, there is nothing to find.")
                .isNull();
    }

    @Test
    void testFind_nestedClassOverridingEnclosingConfiguration_ignoresEnclosingClassesAnnotation()
            throws Exception
    {
        final EnclosingForOverride enclosing = new EnclosingForOverride();
        final SpringAnnotationLookup lookup =
                lookupFor(enclosing.new NestedOverriding(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class))
                .as("A @Nested class annotated @NestedTestConfiguration(OVERRIDE) inherits"
                        + " nothing from its enclosing class, annotations included.")
                .isNull();
    }

    @Test
    void testFindAll_annotationOnMethodAndClass_methodFirst() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ClassAndMethodLevelTearDown(), "aTestMethod");

        assertThat(lookup.findAll(DbUnitTearDownOperation.class))
                .extracting(DbUnitTearDownOperation::value)
                .as("Every declaration is returned, the method-level one first.")
                .containsExactly(DbUnitOperation.TRUNCATE_TABLE, DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFindAll_composedOnSubclassAndDirectOnSuperclass_closestClassFirst() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedOnSubclassOverDirectOnSuperclass(), "aTestMethod");

        assertThat(lookup.findAll(DbUnitTearDownOperation.class))
                .extracting(DbUnitTearDownOperation::value)
                .as("The subclass's composed declaration is nearer than the superclass's own.")
                .containsExactly(DbUnitOperation.DELETE_ALL, DbUnitOperation.TRUNCATE_TABLE);
    }

    @Test
    void testFindAll_nestedClassWithoutAnnotation_returnsEnclosingClassesAnnotation()
            throws Exception
    {
        final EnclosingWithTearDown enclosing = new EnclosingWithTearDown();
        final SpringAnnotationLookup lookup = lookupFor(enclosing.new Nested(), "aTestMethod");

        assertThat(lookup.findAll(DbUnitTearDownOperation.class))
                .extracting(DbUnitTearDownOperation::value)
                .containsExactly(DbUnitOperation.DELETE_ALL);
    }

    @Test
    void testFindAll_nothingDeclared_returnsEmpty() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new NothingDeclared(), "aTestMethod");

        assertThat(lookup.findAll(DbUnitTearDownOperation.class)).isEmpty();
    }

    @Test
    void testFrom_subclassDeclaresOnlyAFailureHandler_keepsTheBaseClassLoaderAndProperties()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new SubclassDeclaringAFailureHandler(), "aTestMethod");

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(lookup);

        assertThat(config.getFailureHandler()).as("The subclass's own attribute applies.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
        assertThat(config.getDataFileLoader()).as("The base class's loader is kept.")
                .isInstanceOf(FlatXmlDataFileLoader.class);
        assertThat(config.getDatabaseConfigProperties())
                .as("The base class's properties are kept.")
                .containsEntry(DatabaseConfig.PROPERTY_BATCH_SIZE, "50");
    }

    @Test
    void testFrom_composedAnnotationAndOwnDeclaration_mergesBoth() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedAndOwnDeclaration(), "aTestMethod");

        final AnnotatedTestConfiguration config =
                AnnotatedTestConfiguration.from(lookup);

        assertThat(config.getFailureHandler()).as("The class's own declaration applies.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
        assertThat(config.getDataFileLoader())
                .as("The composed annotation's loader is kept, not discarded by the own one.")
                .isInstanceOf(FlatXmlDataFileLoader.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnTestMethod_returnsTheClassDeclaringTheMethod()
            throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new MethodLevelTearDown(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("The class declaring the annotated test method declares the annotation.")
                .isEqualTo(MethodLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnInheritedTestMethod_returnsTheSuperclassDeclaringIt()
            throws Exception
    {
        final Method inheritedMethod = MethodLevelTearDown.class.getDeclaredMethod("aTestMethod");
        final SpringAnnotationLookup lookup =
                lookupFor(new InheritsMethodLevelTearDown(), inheritedMethod);

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("An annotation on an inherited test method is declared by the superclass"
                        + " that declares the method, not by the concrete test class.")
                .isEqualTo(MethodLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_methodLevelAndSuperclassClassLevel_returnsTheClassDeclaringTheMethod()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new MethodLevelOverSuperclassClassLevel(), "anotherTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("The method-level annotation wins, so the class declaring the method is the"
                        + " declaring class, not the superclass carrying a class-level one.")
                .isEqualTo(MethodLevelOverSuperclassClassLevel.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnTestClass_returnsTheTestClass() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new ClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("A class-level annotation is declared by the class carrying it.")
                .isEqualTo(ClassLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnSuperclass_returnsTheSuperclass() throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new SubclassOfClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("An annotation inherited from a superclass is declared by that superclass.")
                .isEqualTo(ClassLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnImplementedInterface_returnsTheInterface()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ImplementsTearDownInterface(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("An annotation found on an interface the test class implements is declared"
                        + " by that interface.")
                .isEqualTo(TearDownInterface.class);
    }

    @Test
    void testFindDeclaringClass_composedAnnotationOnTestClass_returnsTheTestClass()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("An annotation found through a composed annotation is declared by the class"
                        + " carrying the composed annotation.")
                .isEqualTo(ComposedClassLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_composedAnnotationOnSuperclass_returnsTheSuperclass()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new SubclassOfComposedClassLevelTearDown(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("An annotation found through a composed annotation on a superclass is"
                        + " declared by that superclass, not by the annotation type or the"
                        + " concrete test class.")
                .isEqualTo(ComposedClassLevelTearDown.class);
    }

    @Test
    void testFindDeclaringClass_annotationOnEnclosingClassOfNestedClass_returnsTheEnclosingClass()
            throws Exception
    {
        final EnclosingWithTearDown enclosing = new EnclosingWithTearDown();
        final SpringAnnotationLookup lookup = lookupFor(enclosing.new Nested(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("An annotation a @Nested class inherits from its enclosing class is declared"
                        + " by the enclosing class.")
                .isEqualTo(EnclosingWithTearDown.class);
    }

    @Test
    void testFindDeclaringClass_nestedClassOverridingEnclosingConfiguration_returnsTheTestClass()
            throws Exception
    {
        final EnclosingForOverride enclosing = new EnclosingForOverride();
        final SpringAnnotationLookup lookup =
                lookupFor(enclosing.new NestedOverriding(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("With nothing found, as the enclosing class's annotation is not inherited,"
                        + " the declaring class is the test class.")
                .isEqualTo(EnclosingForOverride.NestedOverriding.class);
    }

    @Test
    void testFindDeclaringClass_nothingDeclared_returnsTheTestClass() throws Exception
    {
        final SpringAnnotationLookup lookup = lookupFor(new NothingDeclared(), "aTestMethod");

        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("With no such annotation, the declaring class is the test class.")
                .isEqualTo(NothingDeclared.class);
    }

    @Test
    void testFindDeclaringClass_directOnSuperclassAndComposedOnTestClass_namesTheDeclarationFindReturns()
            throws Exception
    {
        final SpringAnnotationLookup lookup =
                lookupFor(new ComposedOnSubclassOverDirectOnSuperclass(), "aTestMethod");

        assertThat(lookup.find(DbUnitTearDownOperation.class).value())
                .as("Spring searches the closest class first, so the annotation composed on the"
                        + " test class wins over the one its superclass declares directly.")
                .isEqualTo(DbUnitOperation.DELETE_ALL);
        assertThat(lookup.findDeclaringClass(DbUnitTearDownOperation.class))
                .as("The declaring class must belong to that same declaration, or a dataset path"
                        + " would resolve next to a class that never declared it.")
                .isEqualTo(ComposedOnSubclassOverDirectOnSuperclass.class);
    }

    private static SpringAnnotationLookup lookupFor(final Object testInstance,
            final String testMethodName) throws Exception
    {
        final FakeTestContext context = new FakeTestContext(testInstance.getClass())
                .beginMethod(testInstance, testMethodName);
        return new SpringAnnotationLookup(context);
    }

    private static SpringAnnotationLookup lookupFor(final Object testInstance,
            final Method testMethod)
    {
        final FakeTestContext context =
                new FakeTestContext(testInstance.getClass()).beginMethod(testInstance, testMethod);
        return new SpringAnnotationLookup(context);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    @interface DeletesAllRows
    {
    }

    static class MethodLevelTearDown
    {
        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class ClassAndMethodLevelTearDown
    {
        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class ClassLevelTearDown
    {
        void aTestMethod()
        {
        }
    }

    static class SubclassOfClassLevelTearDown extends ClassLevelTearDown
    {
        @Override
        void aTestMethod()
        {
        }
    }

    @DeletesAllRows
    static class ComposedClassLevelTearDown
    {
        void aTestMethod()
        {
        }
    }

    static class ComposedMethodLevelTearDown
    {
        @DeletesAllRows
        void aTestMethod()
        {
        }
    }

    static class ExpectedOnMethod
    {
        @DbUnitExpected("expected.xml")
        void aTestMethod()
        {
        }
    }

    static class OverridingWithoutAnnotation extends ExpectedOnMethod
    {
        @Override
        void aTestMethod()
        {
        }
    }

    /**
     * The inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class EnclosingWithTearDown
    {
        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithTearDown.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class InheritsMethodLevelTearDown extends MethodLevelTearDown
    {
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class ClassLevelTearDownForSubclassMethod
    {
    }

    static class MethodLevelOverSuperclassClassLevel extends ClassLevelTearDownForSubclassMethod
    {
        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        void anotherTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    interface TearDownInterface
    {
    }

    static class ImplementsTearDownInterface implements TearDownInterface
    {
        void aTestMethod()
        {
        }
    }

    static class SubclassOfComposedClassLevelTearDown extends ComposedClassLevelTearDown
    {
        @Override
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
    static class DirectTearDownOnSuperclass
    {
    }

    @DeletesAllRows
    static class ComposedOnSubclassOverDirectOnSuperclass extends DirectTearDownOnSuperclass
    {
        void aTestMethod()
        {
        }
    }

    /**
     * The inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class EnclosingForOverride
    {
        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        class NestedOverriding
        {
            Object enclosing()
            {
                return EnclosingForOverride.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class NothingDeclared
    {
        void aTestMethod()
        {
        }
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    static class BaseDeclaringLoaderAndProperties
    {
        void aTestMethod()
        {
        }
    }

    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    static class SubclassDeclaringAFailureHandler extends BaseDeclaringLoaderAndProperties
    {
        @Override
        void aTestMethod()
        {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
    @interface ReadsFlatXml
    {
    }

    @ReadsFlatXml
    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    static class ComposedAndOwnDeclaration
    {
        void aTestMethod()
        {
        }
    }
}
