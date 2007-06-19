package org.sakaiproject.authz.api;

import java.util.Collection;
import java.util.List;



/**
 * database methods.
 */
public interface DbAuthzGroupSql {

   public String getCountRealmFunctionSql();
   public String getCountRealmRoleFunctionSql   (String anonymousRole, String authorizationRole, boolean authorized);
   public String getCountRealmRoleFunctionSql   (String anonymousRole, String authorizationRole, boolean authorized, String inClause);
   public String getCountRealmRoleFunctionEndSql(String anonymousRole, String authorizationRole, boolean authorized, String inClause);
   public String getCountRealmRoleSql();
   public String getDeleteRealmProvider1Sql();
   public String getDeleteRealmProvider2Sql();
   public String getDeleteRealmRoleDescription1Sql();
   public String getDeleteRealmRoleDescription2Sql();
   public String getDeleteRealmRoleFunction1Sql();
   public String getDeleteRealmRoleFunction2Sql();
   public String getDeleteRealmRoleGroup1Sql();
   public String getDeleteRealmRoleGroup2Sql();
   public String getDeleteRealmRoleGroup3Sql();
   public String getDeleteRealmRoleGroup4Sql();
   public String getInsertRealmFunctionSql();
   public String getInsertRealmProviderSql();
   public String getInsertRealmRoleDescriptionSql();
   public String getInsertRealmRoleFunctionSql();
   public String getInsertRealmRoleGroup1Sql();
   public String getInsertRealmRoleGroup2Sql();
   public String getInsertRealmRoleGroup3Sql();
   public String getInsertRealmRoleSql();
   public String getSelectRealmFunction1Sql();
   public String getSelectRealmFunction2Sql();
   public String getSelectRealmFunctionFunctionNameSql(String inClause);
   public String getSelectRealmIdSql();
   public String getSelectRealmIdSql(Collection azGroups);
   public String getSelectRealmProvider2Sql();
   public String getSelectRealmProviderId1Sql();
   public String getSelectRealmProviderId2Sql();
   public String getSelectRealmProviderSql(String inClause);
   public String getSelectRealmRoleDescriptionSql();
   public String getSelectRealmRoleFunctionSql();
   public String getSelectRealmRoleGroup1Sql();
   public String getSelectRealmRoleGroup2Sql();
   public String getSelectRealmRoleGroup3Sql();
   public String getSelectRealmRoleGroup4Sql();
   public String getSelectRealmRoleGroupUserIdSql(String inClause1, String inClause2);
   public String getSelectRealmRoleNameSql();
   public String getSelectRealmRoleSql();
   public String getSelectRealmUserRoleSql(String inClause);

// dbAuthzGroupSql.
}
