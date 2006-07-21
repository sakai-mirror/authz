package org.sakaiproject.authz.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

public abstract class OncourseSecurity extends SakaiSecurity {

	/** Our logger. */
	private static Log M_log = LogFactory.getLog(OncourseSecurity.class);
	
	/**
	 * @return the ThreadLocalManager collaborator.
	 */
	protected abstract ThreadLocalManager threadLocalManager();

	/**
	 * @return the AuthzGroupService collaborator.
	 */
	protected abstract AuthzGroupService authzGroupService();

	/**
	 * @return the UserDirectoryService collaborator.
	 */
	protected abstract UserDirectoryService userDirectoryService();

	/**
	 * @return the MemoryService collaborator.
	 */
	protected abstract MemoryService memoryService();

	/**
	 * @return the EntityManager collaborator.
	 */
	protected abstract EntityManager entityManager();

	public boolean unlock(User user, String function, String entityRef)
	{
		
//		 pick up the current user if needed
		if (user == null)
		{
			user = userDirectoryService().getCurrentUser();
		}

		// make sure we have complete parameters
		if (user == null || function == null || entityRef == null)
		{
			M_log.warn("unlock(): null: " + user + " " + function + " " + entityRef);
			return false;
		}
	
		// Oncourse CL - check to see if this user is authorized by AdminTools
		if(isAdminToolsUser(user)) {
			
			M_log.info(this+"checkAdminToolsUser() ["+user.getEid()+"]: TRUE");
			
			if(checkAuthzAdminTools(user, function, entityRef)) {
				
				M_log.info(this+"checkAuthzAdminTools() ["+user.getEid()+"]: TRUE");
				return true;
			}
			
		}
		
		boolean result =  super.unlock(user, function, entityRef);
	   
		M_log.info("super.unlock: "+result);
		
		return result;
		
	}
	
	/*
	public boolean unlock(User user, String function, String entityRef)
	{
	
//		 pick up the current user if needed
		if (user == null)
		{
			user = userDirectoryService().getCurrentUser();
		}

		// make sure we have complete parameters
		if (user == null || function == null || entityRef == null)
		{
			M_log.warn("unlock(): null: " + user + " " + function + " " + entityRef);
			return false;
		}

		
		//if isAdminToolsUser() {
		
		//	checkAuthzGroupsWithAdminTools();
		
		//}
		
		
		
		Reference ref = entityManager().newReference(entityRef);
		
		M_log.info("checkAuthzAdminTools: userEid: "+user.getEid()+ " function: "+function+" entityRef: "+entityRef);
		
		M_log.info(
		  "id = "+ref.getId()		
		+ "type = "+ref.getType()
		+ "subtype = "+ref.getSubType()
		+ "container = "+ref.getContainer()
		+ "context = "+ref.getContext()
		);
		
		
		return super.unlock(user, function, entityRef);
		
	}
*/
	
	protected boolean isAdminToolsUser(User user) {
		
		//TODO: SELECT * FROM SAKAI_SITE WHERE SITE_ID = '!admin'
		// if user is in !admin group return true
		// cache this result
		
		
		
		//if(checkAuthzGroups(user.getId(), "site.visit", "/site/admintools")) {
		//	M_log.info(this+"checkAdminToolsUser(): TRUE");
		//	return true;
		//}
		
		//M_log.info(this+"checkAdminToolsUser(): FALSE");		
		//return false;
		
		return true;
	}
	
	protected boolean checkAuthzAdminTools(User u, String function, String entityRef) {
		
		String userId = u.getId();
		
		M_log.info(this+" checkAuthzAdminTools: userId: "+userId+ " function: "+function+" entityRef: "+entityRef);
		
		if(!entityRef.substring(0,6).equals("/site/")) {
		
			//TODO: Clean up this implementation - use Reference instead of custom parse	
			Reference ref = entityManager().newReference(entityRef);
			
			M_log.info("checkAuthzAdminTools: userEid: "+u.getEid()+ " function: "+function+" entityRef: "+entityRef);
			
			M_log.info(
			  "id = "+ref.getId()		
			+ " type = "+ref.getType()
			+ " subtype = "+ref.getSubType()
			+ " container = "+ref.getContainer()
			+ " context = "+ref.getContext()
			);
			
			String refParts[] = entityRef.split("/");
			
			if(refParts.length > 3) {
				
				if(entityRef.substring(0,7).equals("/realm/")) {
					
					return unlock(u, "site.upd", "/site/"+refParts[4]);
					
				} else {
				
					return unlock(u, "site.upd", "/site/"+refParts[3]);
				
				}
			}
			
			return false;
			
		}
		
		String siteId = entityRef.replaceFirst("/site/","");
	
		Site site = null;
		
	    try {
			site = SiteService.getSite(siteId);
		} catch (IdUnusedException e) {
			M_log.error(this+": IdUnusedException: "+e);
			return false;
		}	
	
		ResourceProperties properties = site.getProperties();
		
		String classicId = properties.getProperty("site-oncourse-course-id");
		String adminAuthorization = properties.getProperty("oncourse-admin-authorization");
		
		if(classicId == null && adminAuthorization == null) {
			
			return false;
			
		}
		
		String adminCampusList = null;
		String adminDeptList = null;
		
		String sql = "SELECT CAMPUS,DEPT FROM ADMIN_RIGHTS WHERE USER_ID = '"+userId+"'";

		Connection conn;
		try {
			conn = getOncourseConnection();
			
			Statement statement = conn.createStatement();
			
			ResultSet result = statement.executeQuery(sql);
			
			if(result.next()) {
				
				adminCampusList = result.getString("CAMPUS");
				adminDeptList = result.getString("DEPT");
				
				
			} else {
				
				return false;
				
			}
			
		
	    result.close();
		statement.close();
		conn.close();
			
		} catch (SQLException e) {
			M_log.error("SQLException: "+e);
			return false;
		}
		
		M_log.info(this+" checkAuthzAdminTools: classicId: "+classicId);
		M_log.info(this+" checkAuthzAdminTools: adminCampusList: "+adminCampusList);
		M_log.info(this+" checkAuthzAdminTools: adminDeptList: "+adminDeptList);
		
		String courseCampus = null;
		String courseDept = null;
		
		if(classicId != null) {
		
			String classicIdList[] = classicId.split("-");
	
		
			courseCampus = classicIdList[2];
			courseDept = classicIdList[3];
		} else {
			
			String adminAuthList[] = adminAuthorization.split("-");
			
			courseCampus = adminAuthList[0];
			courseDept = adminAuthList[1];
			
		}
		
		
		String adminCampus[] = adminCampusList.split(",");
		String adminDept[]   = adminDeptList.split(",");
		
		
		
		for(int i=0; i < adminCampus.length; i++) {
			
			for(int j=0; j < adminDept.length; j++) {
				
				
				M_log.info(this+" checkAuthzAdminTools: try to match: "+adminCampus[i]+"-"+adminDept[j]);

				if(adminCampus[i].equals(courseCampus) || "%".equals(adminCampus[i])) {
					
					if(adminDept[j].equals(courseDept) || "%".equals(adminDept[j])) {
			
					
						M_log.info(this+" checkAuthzAdminTools: MATCH");
					    return true;
					}
					
				}
				
			}
			
			
		}
		
		
		
		return false;
		
	}

	private Connection getOncourseConnection() throws SQLException {
		
	    
		
		String driver = ServerConfigurationService.getString("oncourse.driver");
		String connect = ServerConfigurationService.getString("oncourse.connect.ocsystem");
		String user = ServerConfigurationService.getString("oncourse.user");
		String password = ServerConfigurationService.getString("oncourse.pw");

		try {
			Class.forName(driver).newInstance();
		} catch (Exception e1) {
			M_log.error(this+": error registering MSSQL JDBC driver: "+e1);
		}

		


		return DriverManager.getConnection(connect, user, password);
		
		
		
	}

	

}
