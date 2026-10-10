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
package org.dbunit.junit.jupiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.junit.jupiter.inheritance.MethodLevelPrepBase;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

class DeclaringClassFinderTest
{
    @Test
    void testFind_annotationOnTheTestMethod_returnsTheClassDeclaringTheMethod() throws Exception
    {
        final Method inherited = MethodLevelPrepBase.class.getMethod("testInheritedMethodWithPrep");
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.of(inherited),
                MethodInheritingSubclass.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class))
                .as("An inherited test method's annotation belongs to the class declaring it.")
                .isEqualTo(MethodLevelPrepBase.class);
    }

    @Test
    void testFind_annotationOnTheTestClass_returnsTheTestClass()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                PrepOnTheClass.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(PrepOnTheClass.class);
    }

    @Test
    void testFind_annotationInheritedFromASuperclass_returnsTheSuperclass()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                SubclassOfPrepOnTheClass.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(PrepOnTheClass.class);
    }

    @Test
    void testFind_annotationCarriedByAComposedAnnotationOnASuperclass_returnsThatSuperclass()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                SubclassOfComposed.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(ComposedOnTheClass.class);
    }

    @Test
    void testFind_nearestOfTwoDeclarations_returnsTheNearerClass()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                RedeclaringSubclass.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(RedeclaringSubclass.class);
    }

    @Test
    void testFind_annotationOnAnEnclosingClass_returnsTheEnclosingClass()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                PrepOnTheClass.Nested.class, Arrays.asList(PrepOnTheClass.class));

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(PrepOnTheClass.class);
    }

    @Test
    void testFind_annotationNowhere_returnsTheTestClass()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                PrepOnTheClass.class, Collections.emptyList());

        assertThat(finder.find(DbUnitExpected.class))
                .as("With no declaration to attribute it to, paths resolve against the test"
                        + " class as they always did.")
                .isEqualTo(PrepOnTheClass.class);
    }

    @Test
    void testFind_interfaceAndSuperclassBothDeclare_returnsTheInterfaceLikeJUnitDoes()
    {
        final DbUnitPrep junitsChoice = AnnotationSupport
                .findAnnotation(ImplementsInterfaceExtendsSuper.class, DbUnitPrep.class).get();
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                ImplementsInterfaceExtendsSuper.class, Collections.emptyList());

        assertThat(junitsChoice.value())
                .as("The premise: JUnit resolves the interface's annotation before the"
                        + " superclass's.")
                .containsExactly("iface.xml");
        assertThat(finder.find(DbUnitPrep.class))
                .as("The class whose package a path is resolved against must be the one"
                        + " declaring the annotation JUnit resolved.")
                .isEqualTo(PrepOnTheInterface.class);
    }

    @Test
    void testFind_onlyAnInterfaceDeclares_returnsTheInterface()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                ImplementsInterfaceOnly.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(PrepOnTheInterface.class);
    }

    @Test
    void testFind_onlyASuperInterfaceDeclares_returnsTheSuperInterface()
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(Optional.empty(),
                ImplementsSubInterface.class, Collections.emptyList());

        assertThat(finder.find(DbUnitPrep.class)).isEqualTo(PrepOnTheInterface.class);
    }

    @DbUnitPrep("prep.xml")
    static class PrepOnTheClass
    {
        static class Nested
        {
        }
    }

    @DbUnitPrep("iface.xml")
    interface PrepOnTheInterface
    {
    }

    interface SubInterfaceWithoutPrep extends PrepOnTheInterface
    {
    }

    static class ImplementsInterfaceExtendsSuper extends PrepOnTheClass
            implements PrepOnTheInterface
    {
    }

    static class ImplementsInterfaceOnly implements PrepOnTheInterface
    {
    }

    static class ImplementsSubInterface implements SubInterfaceWithoutPrep
    {
    }

    static class SubclassOfPrepOnTheClass extends PrepOnTheClass
    {
    }

    @DbUnitPrep("other.xml")
    static class RedeclaringSubclass extends PrepOnTheClass
    {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @DbUnitPrep("composed.xml")
    @interface ComposedPrep
    {
    }

    @ComposedPrep
    static class ComposedOnTheClass
    {
    }

    static class SubclassOfComposed extends ComposedOnTheClass
    {
    }

    static class MethodInheritingSubclass extends MethodLevelPrepBase
    {
    }
}
