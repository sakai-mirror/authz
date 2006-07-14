package org.sakaiproject.authz.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.memory.api.MemoryService;
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


	

}
