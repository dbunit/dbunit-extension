/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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
package org.dbunit.database.search;

import org.dbunit.util.search.Edge;


/**
 * 
 * FIXME remove - duplicates {@link ForeignKeyRelationshipEdge}
 * @author Felipe Leme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 * @deprecated this class duplicates {@link ForeignKeyRelationshipEdge} and should be removed
 */
public class FKRelationshipEdge extends Edge {

  // TODO: support multiple columns
  private String fkColumn;
  private String pkColumn;
  
  /**
   * Creates an edge representing a FK.
   *
   * @param tableFrom table that has the FK.
   * @param tableTo table that has the PK.
   * @param fkColumn name of the FK column on tableFrom.
   * @param pkColumn name of the PK column on tableTo.
   */
  public FKRelationshipEdge(String tableFrom, String tableTo, String fkColumn, String pkColumn) {
    super(tableFrom, tableTo);
    this.fkColumn = fkColumn;
    this.pkColumn = pkColumn;
  }

  /**
   * Gets the name of the foreign key column in the relationship.
   *
   * @return name of the foreign key column in the relationship.
   */
  public String getFKColumn() {
    return fkColumn;
  }

  /**
   * Gets the name of the primary key column in the relationship.
   *
   * @return name of the primary key column in the relationship.
   */
  public String getPKColumn() {
    return pkColumn;
  }
  
  public String toString() {
    return getFrom() + "(" + getFKColumn() + ")->" + getTo() + "(" + getPKColumn() + ")";
  }
  
  // TODO: hashcode and equals

}
