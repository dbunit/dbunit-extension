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

import org.opentest4j.AssertionFailedError;

/**
 * Adapter that lets dbunit create JUnit failure objects.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class JUnitFailureFactory implements FailureFactory {
    @Override
    public Error createFailure(final String message, final String expected, final String actual) {
        // Return the org.opentest4j.AssertionFailedError object
        // TODO Junit5 update something changed the message returned does not include
        // the actual and exected
        // adding it here for now.
        return new DbComparisonFailure(
                message,
                expected, actual);
    }

    @Override
    public Error createFailure(final String message) {
        // Return the org.opentest4j.AssertionFailedError object
        return new AssertionFailedError(message);
    }
}
