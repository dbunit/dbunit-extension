/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

/**
 * Factory to create exceptions for the testing framework to be used,
 * for example JUnit, TestNG or dbunit exceptions.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public interface FailureFactory 
{
    /**
     * Creates a new failure object which can have different types, depending on
     * the testing framework you are currently using (e.g. JUnit, TestNG, ...)
     * @param message The reason for the failure
     * @param expected The expected result
     * @param actual The actual result
     * @return The comparison failure object for this handler (can be JUnit or some other)
     * which can be thrown on an assertion failure
     */
    public Error createFailure(String message, String expected, String actual);

    /**
     * @param message The reason for the failure
     * @return The assertion failure object for this handler (can be JUnit or some other)
     * which can be thrown on an assertion failure
     */
    public Error createFailure(String message);

}
