package org.sakaiproject.authz.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

public abstract class OncourseSecurity extends SakaiSecurity {

	protected static Cache m_AdminToolsUserCache = null;
	protected static Cache m_AdminToolsRightsCache = null;
	protected static Cache m_AdminToolsParticipant = null;

	private int admin_tools_user_cache_duration = 60 * 15;
	private int admin_tools_user_participant_duration = 60 * 5;

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


	class AdminRightsResult {

		AdminRightsResult(String deptList, String campusList) {

			this.adminDeptList = deptList;
			this.adminCampusList = campusList;


		}

		String adminDeptList;
		String adminCampusList;

	}

	public void init() {

		super.init();

		admin_tools_user_cache_duration = ServerConfigurationService.getInt("admin.tools.user.cache.duration", 60 * 15);
		admin_tools_user_participant_duration = ServerConfigurationService.getInt("admin.tools.user.participant.cache.duration", 60 * 5);


		// build a synchronized map for the oncoursedb cache
		m_AdminToolsUserCache = memoryService().newHardCache();
		m_AdminToolsRightsCache = memoryService().newHardCache();
		m_AdminToolsParticipant = memoryService().newHardCache();
	}

	public boolean unlock(User user, String function, String entityRef)
	{
//		pick up the current user if needed
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
			if(M_log.isDebugEnabled())
				M_log.debug(this+"checkAdminToolsUser() ["+user.getEid()+"]: TRUE");

			if(checkAuthzAdminTools(user, function, entityRef)) {
				if(M_log.isDebugEnabled())
					M_log.debug(this+"checkAuthzAdminTools() ["+user.getEid()+"]: TRUE");
				return true;
			}

		}

		boolean result =  super.unlock(user, function, entityRef);

		if(M_log.isDebugEnabled())
			M_log.debug("super.unlock: "+result);

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

	//protected boolean isAdminToolsUser(User user) {
	public boolean isAdminToolsUser(User user) {

		if (m_AdminToolsUserCache == null) {

			M_log.warn(this+": m_AdminToolsUserCache not initialized!");
			return false;
		}

		if (m_AdminToolsUserCache.containsKey(user.getId())) {

			Boolean isAdminToolsUser = (Boolean) m_AdminToolsUserCache.getExpiredOrNot(user.getId());

			if (isAdminToolsUser != null) {
				if(M_log.isDebugEnabled())
					M_log.debug(this+": cache hit: "+user.getId()+"="+isAdminToolsUser.booleanValue());
				return isAdminToolsUser.booleanValue();

			}

		}

		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet result = null;

		try {
			conn = SqlService.borrowConnection();

			String sql = "SELECT USER_ID FROM SAKAI_REALM SR, SAKAI_REALM_RL_GR SRRG WHERE SR.REALM_ID = '/site/admintools' "+
			"and SR.REALM_KEY=SRRG.REALM_KEY and SRRG.USER_ID = ?";

			statement = conn.prepareStatement(sql);

			statement.setString(1, user.getId());

			result = statement.executeQuery();

			if(result != null && result.next()) {

				Boolean isAdminToolsUser = new Boolean(true);

				m_AdminToolsUserCache.put(user.getId(), isAdminToolsUser, admin_tools_user_cache_duration);
				if(M_log.isDebugEnabled())
					M_log.debug(this+": cache miss: "+user.getId()+"="+isAdminToolsUser.booleanValue());
				return true;

			} else {

				Boolean isAdminToolsUser = new Boolean(false);

				m_AdminToolsUserCache.put(user.getId(), isAdminToolsUser, admin_tools_user_cache_duration);
				if(M_log.isDebugEnabled())
					M_log.debug(this+": cache miss: "+user.getId()+"="+isAdminToolsUser.booleanValue());
				return false;

			}


		} catch (SQLException e) {
			M_log.error(this+": isAdminToolsUser: "+e);
		} finally {

			try{
				if(result!= null) { result.close(); }
				if(statement != null) { statement.close(); }
				if(conn != null) { SqlService.returnConnection(conn); }
			} catch (SQLException e) {
				M_log.error(this+": isAdminToolsUser: "+e);
			}

		}


		return false;
	}

	protected boolean checkAuthzAdminTools(User u, String function, String entityRef) {

		String userEid = u.getEid();

		if (m_AdminToolsRightsCache == null || m_AdminToolsParticipant == null) {

			M_log.warn(this+": m_AdminToolsRightsCache not initialized! or m_AdminToolsParticipant is not initialized.");
			return false;
		}

		if(M_log.isDebugEnabled())
			M_log.debug(this+" checkAuthzAdminTools: userEid: "+userEid+ " function: "+function+" entityRef: "+entityRef);

		if(!entityRef.substring(0,6).equals("/site/")) {

			//TODO: Clean up this implementation - use Reference instead of custom parse	
			//Reference ref = entityManager().newReference(entityRef);

			if(M_log.isDebugEnabled())
				M_log.debug("checkAuthzAdminTools: userEid: "+u.getEid()+ " function: "+function+" entityRef: "+entityRef);

			/*
			M_log.info(
			  "id = "+ref.getId()		
			+ " type = "+ref.getType()
			+ " subtype = "+ref.getSubType()
			+ " container = "+ref.getContainer()
			+ " context = "+ref.getContext()
			);
			 */

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

		AdminRightsResult adminRightsResult = null;

		String adminCampusList = null;
		String adminDeptList = null;

		if (m_AdminToolsRightsCache.containsKey(u.getId())) {

			adminRightsResult = (AdminRightsResult) m_AdminToolsRightsCache.getExpiredOrNot(u.getId());

		}

		if (adminRightsResult != null) {

			M_log.info(this+": result cache hit: "+u.getId()+"="+adminRightsResult.adminCampusList+" "+adminRightsResult.adminDeptList);

			adminCampusList = adminRightsResult.adminCampusList;
			adminDeptList = adminRightsResult.adminDeptList;

		} else {

			M_log.info(this+": result cache miss: "+u.getId());

			String sql = "SELECT CAMPUS,DEPT FROM ADMIN_RIGHTS WHERE USER_ID = '"+userEid+"'";

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

			adminRightsResult = new AdminRightsResult(adminDeptList, adminCampusList);
			m_AdminToolsRightsCache.put(u.getId(), adminRightsResult, admin_tools_user_cache_duration);

		}

		//if user is a participant for the site as role of Observer, Student, Visitor, he shouldn't have dept admin privilege
		Map adminToolsParticipantMap = null;
		if (m_AdminToolsParticipant.containsKey(u.getId())) 
		{
			adminToolsParticipantMap = (Map) m_AdminToolsParticipant.get(u.getId());
		}
		if(adminToolsParticipantMap != null)
		{
			if(adminToolsParticipantMap.get(siteId) != null && ((Boolean)adminToolsParticipantMap.get(siteId)).booleanValue())
			{
				//if user is a participant on the site, return false - this user shouldn't have dept. admin. privilege
				return false;
			}
			else if(adminToolsParticipantMap.get(siteId) == null)
			{
				Connection conn = null;
				PreparedStatement statement = null;
				ResultSet result = null;
				try 
				{
					conn = SqlService.borrowConnection();
					String sql = "SELECT ROLE_KEY FROM SAKAI_REALM_RL_GR WHERE USER_ID = '" + u.getId() + "' AND REALM_KEY = (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = '/site/" + siteId + "')";			
					statement = conn.prepareStatement(sql);
					result = statement.executeQuery();

					if(result != null && result.next() && (String)result.getString("ROLE_KEY") != null) 
					{
						adminToolsParticipantMap.put(siteId, new Boolean(true));
						m_AdminToolsParticipant.put(u.getId(), adminToolsParticipantMap, admin_tools_user_participant_duration);
						return false;

					}
					else
					{
						adminToolsParticipantMap.put(siteId, new Boolean(false));
						m_AdminToolsParticipant.put(u.getId(), adminToolsParticipantMap, admin_tools_user_participant_duration);
					}
				}
				catch(SQLException e)
				{
					M_log.error("error happens when checking of user is a participant for site (block 1):" + siteId + "--" + u.getEid());
				}
				finally
				{
					if(result != null)
					{
						try
						{
							result.close();
						}
						catch(SQLException sqle)
						{
							M_log.error("error happens when closing result (block 1):" + sqle.getMessage());
						}
					}
					if(statement != null)
					{
						try
						{
							statement.close();
						}
						catch(SQLException sqle)
						{
							M_log.error("error happens when closing statement (block 1):" + sqle.getMessage());
						}
					}
					if(conn != null)
						SqlService.returnConnection(conn);
				}
			}
		}
		else
		{
			adminToolsParticipantMap = new ConcurrentHashMap();
			Connection conn = null;
			PreparedStatement statement = null;
			ResultSet result = null;
			try
			{
				conn = SqlService.borrowConnection();
				String sql = "SELECT ROLE_KEY FROM SAKAI_REALM_RL_GR WHERE USER_ID = '" + u.getId() + "' AND REALM_KEY = (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = '/site/" + siteId + "')";			
				statement = conn.prepareStatement(sql);
				result = statement.executeQuery();

				if(result != null && result.next() && (String)result.getString("ROLE_KEY") != null) 
				{
					adminToolsParticipantMap.put(siteId, new Boolean(true));
					m_AdminToolsParticipant.put(u.getId(), adminToolsParticipantMap, admin_tools_user_participant_duration);
					return false;
				}
				else
				{
					adminToolsParticipantMap.put(siteId, new Boolean(false));
					m_AdminToolsParticipant.put(u.getId(), adminToolsParticipantMap, admin_tools_user_participant_duration);
				}
			}
			catch(SQLException e)
			{
				M_log.error("error happens when checking of user is a participant for site (block 2):" + siteId + "--" + u.getEid());
			}
			finally
			{
				if(result != null)
				{
					try
					{
						result.close();
					}
					catch(SQLException sqle)
					{
						M_log.error("error happens when closing result (block 2):" + sqle.getMessage());
					}
				}
				if(statement != null)
				{
					try
					{
						statement.close();
					}
					catch(SQLException sqle)
					{
						M_log.error("error happens when closing statement (block 2):" + sqle.getMessage());
					}
				}
				if(conn != null)
					SqlService.returnConnection(conn);
			}
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
