package org.sakaiproject.authz.impl;

import java.util.Collection;




/**
 * methods for accessing authz data in a mysql database.
 */
public class DbAuthzGroupSqlMySql extends DbAuthzGroupSqlDefault {

   /**
    * returns the sql statement to delete the realm functions from the sakai_realm_rl_fn table for a given realm id..
    */
   public String getDeleteRealmRoleFunction() {
      return "delete SAKAI_REALM_RL_FN from SAKAI_REALM_RL_FN inner join SAKAI_REALM on SAKAI_REALM_RL_FN.REALM_KEY = SAKAI_REALM.REALM_KEY where REALM_ID = ?";
   }

   /**
    * returns the sql statement to delete the realm functions from the sakai_realm_rl_fn table for a given realm id and user.
    */
/*   public String getDeleteRealmRoleFunctionForUser() {
      return "delete     SAKAI_REALM_RL_GR "                                                  +
             "from       SAKAI_REALM_RL_GR "                                                  +
             "inner join SAKAI_REALM on SAKAI_REALM_RL_GR.REALM_KEY = SAKAI_REALM.REALM_KEY " +
             "where      REALM_ID = ? and USER_ID = ?";
   }
  */

   /**
    * returns the sql statement to delete the realm groups from the sakai_realm_rl_gr table for a given realm id.
    */
   public String getDeleteRealmRoleGroup() {
      return "delete SAKAI_REALM_RL_GR from SAKAI_REALM_RL_GR inner join SAKAI_REALM on SAKAI_REALM_RL_GR.REALM_KEY = SAKAI_REALM.REALM_KEY where REALM_ID = ?";
   }

   /**
    * returns the sql statement to delete the realm providers from the sakai_realm_provider table for a given realm id.
    */
   public String getDeleteRealmProvider() {
//      return "delete SAKAI_REALM_PROVIDER from SAKAI_REALM_PROVIDER inner join SAKAI_REALM on SAKAI_REALM_PROVIDER.REALM_KEY = SAKAI_REALM.REALM_KEY where REALM_ID = ?";
       return "delete SAKAI_REALM_PROVIDER from SAKAI_REALM_PROVIDER inner join SAKAI_REALM on SAKAI_REALM_PROVIDER.REALM_KEY = SAKAI_REALM.REALM_KEY where REALM_ID = ?";
   }

   /**
    * returns the sql statement to delete the realm role description from the sakai_realm_role_desc table for a given realm id.
    */
/*   public String getDeleteRealmRoleDescription() {
      return "delete SAKAI_REALM_ROLE_DESC from SAKAI_REALM_ROLE_DESC inner join SAKAI_REALM on SAKAI_REALM_ROLE_DESC.REALM_KEY = SAKAI_REALM.REALM_KEY where REALM_ID = ?";
   }
  */

   /**
    * returns the beginning part of the sql statement to determine if a user is allowed to perform a given function.
    */
/*   public String getIsAllowed2BeginSql(String realmIdList) {
      return "select count(1) " +
             "from   SAKAI_REALM_RL_FN, SAKAI_REALM force index (AK_SAKAI_REALM_ID) " +
             "where  SAKAI_REALM_RL_FN.REALM_KEY = SAKAI_REALM.REALM_KEY and SAKAI_REALM.REALM_ID in " + realmIdList + " ";
   }
  */

   /**
    * returns the sql statement to read the realm role names and descriptions from the database given the realm id.
    */
/*   public String getReadRealmRoleAndDescriptionSql() {
      return "select ROLE_NAME, DESCRIPTION, PROVIDER_ONLY "                                    +
             "from   SAKAI_REALM_ROLE inner join SAKAI_REALM_ROLE_DESC inner join SAKAI_REALM " +
             "on     SAKAI_REALM_ROLE.ROLE_KEY = SAKAI_REALM_ROLE_DESC.ROLE_KEY and "           +
             "       SAKAI_REALM.REALM_KEY = SAKAI_REALM_ROLE_DESC.REALM_KEY "                  +
             "where  REALM_ID = ?";
   }
*/
   /**
    * returns the sql statement to read the realm role names and functions from the database given the realm id.
    */
/*   public String getReadRealmRoleAndFunctionSql() {
      return "SELECT SAKAI_REALM_ROLE.ROLE_NAME, "                                             +
             "       SAKAI_REALM_FUNCTION.FUNCTION_NAME "                                      +
             "FROM   SAKAI_REALM_ROLE, SAKAI_REALM_FUNCTION, SAKAI_REALM_RL_FN, SAKAI_REALM "  +
             "WHERE  SAKAI_REALM.REALM_ID              = ?                               AND " +
             "       SAKAI_REALM.REALM_KEY             = SAKAI_REALM_RL_FN.REALM_KEY     AND " +
             "       SAKAI_REALM_ROLE.ROLE_KEY         = SAKAI_REALM_RL_FN.ROLE_KEY      AND " +
             "       SAKAI_REALM_FUNCTION.FUNCTION_KEY = SAKAI_REALM_RL_FN.FUNCTION_KEY";
   }
*/
   /**
    * returns the sql statement to read the realm grants from the database given the realm id.
    */
/*   public String getReadRealmRoleGrantsSql() {
      return "select ROLE_NAME, USER_ID, ACTIVE, PROVIDED "                                         +
             "from   SAKAI_REALM_RL_GR A inner join SAKAI_REALM B inner join SAKAI_REALM_ROLE C "   +
             "on     A.REALM_KEY = B.REALM_KEY and A.ROLE_KEY = C.ROLE_KEY "                        +
             "where  B.REALM_ID = ?";
   }
*/
   /**
    * returns the sql statement to read all users from the sakai_realm_rl_gr table for a given set of realms.
    */
/*   public String getReadRealmUsersForRealmSql(Collection realms) {
      String sqlParam = "";
      StringBuffer sqlBuf = null;
      StringBuffer sqlParamBuf = null;

      // TODO: pre-compute some fields arrays and statements for common roleRealms sizes for efficiency? -ggolden

      // make (?, ?, ?...) for realms size
      sqlParamBuf = new StringBuffer();
      sqlParamBuf.append("(?");
      for (int i = 0; i < realms.size() - 1; i++)
         sqlParamBuf.append(",?");

      sqlParamBuf.append(")");
      sqlParam = sqlParamBuf.toString();

      // Assemble SQL
      sqlBuf = new StringBuffer();
      sqlBuf.append("select SRRG.USER_ID ");
      sqlBuf.append("from SAKAI_REALM_RL_GR SRRG ");
      sqlBuf.append("inner join SAKAI_REALM SR force index (AK_SAKAI_REALM_ID) ON SRRG.REALM_KEY = SR.REALM_KEY ");
      sqlBuf.append("where SR.REALM_ID in " + sqlParam + " ");
      sqlBuf.append("and SRRG.ACTIVE = '1' ");
      sqlBuf.append("and SRRG.ROLE_KEY in ");
      sqlBuf.append("(select SRRF.ROLE_KEY ");
      sqlBuf.append("from SAKAI_REALM_RL_FN SRRF ");
      sqlBuf.append("inner join SAKAI_REALM_FUNCTION SRF ON SRRF.FUNCTION_KEY = SRF.FUNCTION_KEY ");
      sqlBuf.append("inner join SAKAI_REALM SR1 force index (AK_SAKAI_REALM_ID) ON SRRF.REALM_KEY = SR1.REALM_KEY ");
      sqlBuf.append("where SRF.FUNCTION_NAME = ? ");
      sqlBuf.append("and SR1.REALM_ID in  " + sqlParam + ")");

      return sqlBuf.toString();
   }
*/
   /**
    * returns the sql statement to write a row into the sakai_function_role table.
    */
   public String getInsertRealmFunctionSql() {
      return "insert into SAKAI_REALM_FUNCTION (FUNCTION_KEY, FUNCTION_NAME) values (DEFAULT, ?)";
   }

   /**
    * returns the sql statement to write a row into the sakai_realm_role table.
    */
   public String getInsertRealmRoleSql() {
      return "insert into SAKAI_REALM_ROLE (ROLE_KEY, ROLE_NAME) values (DEFAULT, ?)";
   }

   public String getDeleteRealmRoleFunction1Sql() {
      return "DELETE RRF FROM SAKAI_REALM_RL_FN RRF"
               + " INNER JOIN SAKAI_REALM R ON RRF.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
               + " INNER JOIN SAKAI_REALM_ROLE RR ON RRF.ROLE_KEY = RR.ROLE_KEY AND RR.ROLE_NAME = ?"
               + " INNER JOIN SAKAI_REALM_FUNCTION RF ON RRF.FUNCTION_KEY = RF.FUNCTION_KEY AND RF.FUNCTION_NAME = ?";
   }

   public String getDeleteRealmRoleGroup1Sql() {
      return "DELETE RRG FROM SAKAI_REALM_RL_GR RRG"
                + " INNER JOIN SAKAI_REALM R ON RRG.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
                + " INNER JOIN SAKAI_REALM_ROLE RR ON RRG.ROLE_KEY = RR.ROLE_KEY AND RR.ROLE_NAME = ?"
                + " WHERE RRG.USER_ID = ? AND RRG.ACTIVE = ? AND RRG.PROVIDED = ?";
    }

/*    public String getDeleteReamProviderSql() {
       return "DELETE RP FROM SAKAI_REALM_PROVIDER RP INNER JOIN SAKAI_REALM R ON RP.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?";
    }
*/
/*    public String getDeleteReamProvider2Sql() {
       return "DELETE RP FROM SAKAI_REALM_PROVIDER RP INNER JOIN SAKAI_REALM R ON RP.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ? WHERE RP.PROVIDER_ID = ?";
    }
*/
    public String getDeleteRoleDescriptionSql() {
       return "DELETE RRD FROM SAKAI_REALM_ROLE_DESC RRD"
                + " INNER JOIN SAKAI_REALM R ON RRD.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
                + " INNER JOIN SAKAI_REALM_ROLE RR ON RRD.ROLE_KEY = RR.ROLE_KEY AND RR.ROLE_NAME = ?";
    }

    public String getDeleteRealmRoleFunction2Sql() {
       return "DELETE SAKAI_REALM_RL_FN FROM SAKAI_REALM_RL_FN INNER JOIN SAKAI_REALM ON SAKAI_REALM_RL_FN.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
    }

    public String getDeleteRealmRoleGroup2Sql() {
       return "DELETE SAKAI_REALM_RL_GR FROM SAKAI_REALM_RL_GR INNER JOIN SAKAI_REALM ON SAKAI_REALM_RL_GR.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
    }

    public String getDeleteRealmProvider1Sql() {
       return "DELETE SAKAI_REALM_PROVIDER FROM SAKAI_REALM_PROVIDER INNER JOIN SAKAI_REALM ON SAKAI_REALM_PROVIDER.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
    }

    public String getDeleteRealmRoleDescription2Sql() {
       return "DELETE SAKAI_REALM_ROLE_DESC FROM SAKAI_REALM_ROLE_DESC INNER JOIN SAKAI_REALM ON SAKAI_REALM_ROLE_DESC.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
    }

    public String getCountRealmRoleFunctionSql(String anonymousRole, String authorizationRole, boolean authorized, String inClause) {
       return "select count(1) from SAKAI_REALM_RL_FN,SAKAI_REALM force index "
            + "(AK_SAKAI_REALM_ID) where SAKAI_REALM_RL_FN.REALM_KEY = SAKAI_REALM.REALM_KEY "
            + "and " + inClause +  getCountRealmRoleFunctionEndSql(anonymousRole, authorizationRole, authorized, inClause);
    }

    public String getSelectRealmRoleGroupUserIdSql(String inClause1, String inClause2) {
       StringBuffer sqlBuf = new StringBuffer();
        
       sqlBuf.append("select SRRG.USER_ID ");
       sqlBuf.append("from SAKAI_REALM_RL_GR SRRG ");
       sqlBuf.append("inner join SAKAI_REALM SR force index (AK_SAKAI_REALM_ID) ON SRRG.REALM_KEY = SR.REALM_KEY ");
       sqlBuf.append("where " + inClause1 + " ");
       sqlBuf.append("and SRRG.ACTIVE = '1' ");
       sqlBuf.append("and SRRG.ROLE_KEY in ");
       sqlBuf.append("(select SRRF.ROLE_KEY ");
       sqlBuf.append("from SAKAI_REALM_RL_FN SRRF ");
       sqlBuf.append("inner join SAKAI_REALM_FUNCTION SRF ON SRRF.FUNCTION_KEY = SRF.FUNCTION_KEY ");
       sqlBuf.append("inner join SAKAI_REALM SR1 force index (AK_SAKAI_REALM_ID) ON SRRF.REALM_KEY = SR1.REALM_KEY ");
       sqlBuf.append("where SRF.FUNCTION_NAME = ? ");
       sqlBuf.append("and " + inClause2 + ")");
       return sqlBuf.toString();
    }

    public String getDeleteRealmRoleGroup4Sql() {
       return "DELETE SAKAI_REALM_RL_GR FROM SAKAI_REALM_RL_GR INNER JOIN SAKAI_REALM ON SAKAI_REALM_RL_GR.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ? WHERE SAKAI_REALM_RL_GR.USER_ID = ?";
   }
    
}
