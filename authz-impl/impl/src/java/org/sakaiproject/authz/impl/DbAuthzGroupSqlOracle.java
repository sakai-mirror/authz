package org.sakaiproject.authz.impl;




/**
 * methods for accessing authz data in an oracle database.
 */
public class DbAuthzGroupSqlOracle extends DbAuthzGroupSqlDefault {

   /**
    * returns the sql statement to write a row into the sakai_function_role table.
    */
   public String getInsertRealmFunctionSql() {
      return "insert into SAKAI_REALM_FUNCTION (FUNCTION_KEY, FUNCTION_NAME) values (SAKAI_REALM_FUNCTION_SEQ.NEXTVAL, ?)";
   }

   /**
    * returns the sql statement to write a row into the sakai_realm_role table.
    */
   public String getInsertRealmRoleSql() {
      return "insert into SAKAI_REALM_ROLE (ROLE_KEY, ROLE_NAME) values (SAKAI_REALM_ROLE_SEQ.NEXTVAL, ?)";
   }
}
