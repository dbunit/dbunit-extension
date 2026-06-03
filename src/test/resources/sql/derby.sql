-----------------------------------------------------------------------------
-- TEST_TABLE
-----------------------------------------------------------------------------
CREATE TABLE TEST_TABLE
  (COLUMN0 VARCHAR(32),
   COLUMN1 VARCHAR(32),
   COLUMN2 VARCHAR(32),
   COLUMN3 VARCHAR(32));

-----------------------------------------------------------------------------
-- SECOND_TABLE
-----------------------------------------------------------------------------

CREATE TABLE SECOND_TABLE
  (COLUMN0 VARCHAR(32),
   COLUMN1 VARCHAR(32),
   COLUMN2 VARCHAR(32),
   COLUMN3 VARCHAR(32));

-----------------------------------------------------------------------------
-- EMPTY_TABLE
-----------------------------------------------------------------------------

CREATE TABLE EMPTY_TABLE
  (COLUMN0 VARCHAR(32),
   COLUMN1 VARCHAR(32),
   COLUMN2 VARCHAR(32),
   COLUMN3 VARCHAR(32));

-----------------------------------------------------------------------------
-- ESCAPED TABLE
-----------------------------------------------------------------------------

--CREATE TABLE "ESCAPED TABLE"
--  (COLUMN0 VARCHAR(32),
--   COLUMN1 VARCHAR(32),
--   COLUMN2 VARCHAR(32),
--   "COLUMN 3" VARCHAR(32));

-----------------------------------------------------------------------------
-- PK_TABLE
-----------------------------------------------------------------------------

CREATE TABLE PK_TABLE
  (PK0 NUMERIC(31, 0) NOT NULL,
   PK1 NUMERIC(31, 0) NOT NULL,
   PK2 NUMERIC(31, 0) NOT NULL,
   NORMAL0 VARCHAR(32),
   NORMAL1 VARCHAR(32), PRIMARY KEY (PK0, PK1, PK2));

-----------------------------------------------------------------------------
-- ONLY_PK_TABLE
-----------------------------------------------------------------------------

CREATE TABLE ONLY_PK_TABLE
  (PK0 NUMERIC(31, 0) NOT NULL PRIMARY KEY);

-----------------------------------------------------------------------------
-- EMPTY_MULTITYPE_TABLE
-----------------------------------------------------------------------------

CREATE TABLE EMPTY_MULTITYPE_TABLE
  (VARCHAR_COL VARCHAR(32),
   NUMERIC_COL NUMERIC(31, 0),
   TIMESTAMP_COL TIMESTAMP,
   VARBINARY_COL BLOB(254));



