package org.sakaiproject.authz.impl;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityAdvisor.SecurityAdvice;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.impl.DbSiteService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;

public class OncourseSecurityAdvisor implements SecurityAdvisor {
	
	private static Log M_log = LogFactory.getLog(OncourseSecurityAdvisor.class);

	public OncourseSecurityAdvisor() {
		M_log.info("OncourseSecurityAdvisor registered.");
	}

	public SecurityAdvice isAllowed(String userId, String function,
			String entityRef) {
		
		SecurityAdvice rv = SecurityAdvice.PASS;
	
		
		// Oncourse Admin Tools users are specified in the Classic ocSystem ADMIN_RIGHTS table
		// This advisor allow these users to do whatever they want in sites that they administer
		
		User u = null;
		
		try {
			u = UserDirectoryService.getUserByEid(userId);
		} catch (UserNotDefinedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(u == null) return rv;
		
		Reference ref = EntityManager.newReference(entityRef);
		
		M_log.info("checkAuthzAdminTools: userId: "+userId+ " function: "+function+" entityRef: "+entityRef);
		
		M_log.info("checkAuthzAdminTools: userId: "+userId+ " function: "+function+" entityRef: "+entityRef);
		
		
		
		return rv;

		//set(String type, String subType, String id, String container, String context)
		
		/*
		
		if(!SiteService.APPLICATION_ID.equals(ref.getType())) {
			
			if(SecurityService.unlock(u, "site.upd", "/site/"+ref.getContext())) {
			
			  rv = SecurityAdvice.ALLOWED;
			
			}
			
		}
		
		
			
		if(!entityRef.substring(0,6).equals("/site/")) {
			
			String refParts[] = entityRef.split("/");
			
			if(refParts.length > 3) {
				
				if(entityRef.substring(0,7).equals("/realm/")) {
					
					if(SecurityService.unlock(u, "site.upd", "/site/"+refParts[4])) {
						
						//return ???
						
					}
					
				} else {
				
					if(SecurityService.unlock(u, "site.upd", "/site/"+refParts[3])) {
						
						//return ???
						
					}
				
				}
			}
			
			//return false;
			
		}
		
		String siteId = entityRef.replaceFirst("/site/","");
	
		Site site = null;
		
	    try {
			site = SiteService.getSite(siteId);
		} catch (IdUnusedException e) {
			//M_log.error(this+": IdUnusedException: "+e);
			//return false;
		}	
	
		ResourceProperties properties = site.getProperties();
		
		String classicId = properties.getProperty("site-oncourse-course-id");
		String adminAuthorization = properties.getProperty("oncourse-admin-authorization");
		
		if(classicId == null && adminAuthorization == null) {
			
			//return false;
			
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
*/		
	}

}
