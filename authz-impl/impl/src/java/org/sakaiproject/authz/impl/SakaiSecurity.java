/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.authz.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.memory.api.MultiRefCache;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

/**
 * <p>
 * SakaiSecurity is a Sakai security service.
 * </p>
 */
public abstract class SakaiSecurity implements SecurityService
{
	/** Our logger. */
	private static Log M_log = LogFactory.getLog(SakaiSecurity.class);

	/** A cache of calls to the service and the results. */
	protected MultiRefCache m_callCache = null;

	/** ThreadLocalManager key for our SecurityAdvisor Stack. */
	protected final static String ADVISOR_STACK = "SakaiSecurity.advisor.stack";

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Dependencies, configuration, and their setter methods
	 *********************************************************************************************************************************************************************************************************************************************************/

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

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Configuration
	 *********************************************************************************************************************************************************************************************************************************************************/

	/** The # minutes to cache the security answers. 0 disables the cache. */
	protected int m_cacheMinutes = 3;

	/**
	 * Set the # minutes to cache a security answer.
	 * 
	 * @param time
	 *        The # minutes to cache a security answer (as an integer string).
	 */
	public void setCacheMinutes(String time)
	{
		m_cacheMinutes = Integer.parseInt(time);
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Init and Destroy
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		// <= 0 minutes indicates no caching desired
		if (m_cacheMinutes > 0)
		{
			// build a synchronized map for the call cache, automatiaclly checking for expiration every 15 mins.
			m_callCache = memoryService().newMultiRefCache(15 * 60);
		}

		M_log.info("init() - caching minutes: " + m_cacheMinutes);
	}

	/**
	 * Final cleanup.
	 */
	public void destroy()
	{
		M_log.info("destroy()");
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * SecurityService implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * {@inheritDoc}
	 */
	public boolean isSuperUser()
	{
		User user = userDirectoryService().getCurrentUser();
		if (user == null) return false;

		return isSuperUser(user.getId());
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isSuperUser(String userId)
	{
		// if no user or the no-id user (i.e. the anon user)
		if ((userId == null) || (userId.length() == 0)) return false;

		// check the cache
		String command = "super@" + userId;
		if ((m_callCache != null) && (m_callCache.containsKey(command)))
		{
			boolean rv = ((Boolean) m_callCache.get(command)).booleanValue();
			return rv;
		}

		boolean rv = false;

		// these known ids are super
		if (UserDirectoryService.ADMIN_ID.equalsIgnoreCase(userId))
		{
			rv = true;
		}

		else if ("postmaster".equalsIgnoreCase(userId))
		{
			rv = true;
		}

		// if the user has site modification rights in the "!admin" site, welcome aboard!
		else
		{
			// TODO: string constants stolen from site -ggolden
			if (authzGroupService().isAllowed(userId, "site.upd", "/site/!admin"))
			{
				rv = true;
			}
		}

		// cache
		if (m_callCache != null)
		{
			Collection azgIds = new Vector();
			azgIds.add("/site/!admin");
			m_callCache.put(command, Boolean.valueOf(rv), m_cacheMinutes * 60, null, azgIds);
		}

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean unlock(String lock, String resource)
	{
		return unlock(userDirectoryService().getCurrentUser(), lock, resource);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean unlock(User u, String function, String entityRef)
	{
		// pick up the current user if needed
		User user = u;
		if (user == null)
		{
			user = userDirectoryService().getCurrentUser();
		}
		return unlock(user.getId(), function, entityRef);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean unlock(String userId, String function, String entityRef)
	{
		// make sure we have complete parameters
		if (userId == null || function == null || entityRef == null)
		{
			M_log.warn("unlock(): null: " + userId + " " + function + " " + entityRef);
			return false;
		}

		// if super, grant
		if (isSuperUser(userId))
		{
			return true;
		}

		// let the advisors have a crack at it, if we have any
		// Note: this cannot be cached without taking into consideration the exact advisor configuration -ggolden
		if (hasAdvisors())
		{
			SecurityAdvisor.SecurityAdvice advice = adviseIsAllowed(userId, function, entityRef);
			if (advice != SecurityAdvisor.SecurityAdvice.PASS)
			{
				return advice == SecurityAdvisor.SecurityAdvice.ALLOWED;
			}
		}

		// check with the AuthzGroups appropriate for this entity
		return checkAuthzGroups(userId, function, entityRef);
	}

	/**
	 * Check the appropriate AuthzGroups for the answer - this may be cached
	 * 
	 * @param userId
	 *        The user id.
	 * @param function
	 *        The security function.
	 * @param entityRef
	 *        The entity reference string.
	 * @return true if allowed, false if not.
	 */
	protected boolean checkAuthzGroups(String userId, String function, String entityRef)
	{
		// check the cache
		String command = "unlock@" + userId + "@" + function + "@" + entityRef;
		if ((m_callCache != null) && (m_callCache.containsKey(command)))
		{
			boolean rv = ((Boolean) m_callCache.get(command)).booleanValue();
			return rv;
		}

		// make a reference for the entity
		Reference ref = entityManager().newReference(entityRef);

		// get this entity's AuthzGroups
		Collection azgs = ref.getAuthzGroups(userId);
		boolean rv = authzGroupService().isAllowed(userId, function, azgs);

		// cache
		if (m_callCache != null) m_callCache.put(command, Boolean.valueOf(rv), m_cacheMinutes * 60, entityRef, azgs);

		return rv;
	}

	/**
	 * Access the List the Users who can unlock the lock for use with this resource.
	 * 
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource reference string.
	 * @return A List (User) of the users can unlock the lock (may be empty).
	 */
	public List unlockUsers(String lock, String reference)
	{
		if (reference == null)
		{
			M_log.warn("unlockUsers(): null resource: " + lock);
			return new Vector();
		}

		// make a reference for the resource
		Reference ref = entityManager().newReference(reference);

		// get this resource's Realms
		Collection realms = ref.getAuthzGroups();

		// get the users who can unlock in these realms
		List ids = new Vector();
		ids.addAll(authzGroupService().getUsersIsAllowed(lock, realms));

		// convert the set of Users into a sorted list of users
		List users = userDirectoryService().getUsers(ids);
		Collections.sort(users);

		return users;
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * SecurityAdvisor Support
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Get the thread-local security advisor stack, possibly creating it
	 * 
	 * @param force
	 *        if true, create if missing
	 */
	protected Stack getAdvisorStack(boolean force)
	{
		Stack advisors = (Stack) threadLocalManager().get(ADVISOR_STACK);
		if ((advisors == null) && force)
		{
			advisors = new Stack();
			threadLocalManager().set(ADVISOR_STACK, advisors);
		}

		return advisors;
	}

	/**
	 * Remove the thread-local security advisor stack
	 */
	protected void dropAdvisorStack()
	{
		threadLocalManager().set(ADVISOR_STACK, null);
	}

	/**
	 * Check the advisor stack - if anyone declares ALLOWED or NOT_ALLOWED, stop and return that, else, while they PASS, keep checking.
	 * 
	 * @param userId
	 *        The user id.
	 * @param function
	 *        The security function.
	 * @param reference
	 *        The Entity reference.
	 * @return ALLOWED or NOT_ALLOWED if an advisor makes a decision, or PASS if there are no advisors or they cannot make a decision.
	 */
	protected SecurityAdvisor.SecurityAdvice adviseIsAllowed(String userId, String function, String reference)
	{
		Stack advisors = getAdvisorStack(false);
		if ((advisors == null) || (advisors.isEmpty())) return SecurityAdvisor.SecurityAdvice.PASS;

		// a Stack grows to the right - process from top to bottom
		for (int i = advisors.size() - 1; i >= 0; i--)
		{
			SecurityAdvisor advisor = (SecurityAdvisor) advisors.elementAt(i);

			SecurityAdvisor.SecurityAdvice advice = advisor.isAllowed(userId, function, reference);
			if (advice != SecurityAdvisor.SecurityAdvice.PASS)
			{
				return advice;
			}
		}

		return SecurityAdvisor.SecurityAdvice.PASS;
	}

	/**
	 * @inheritDoc
	 */
	public void pushAdvisor(SecurityAdvisor advisor)
	{
		Stack advisors = getAdvisorStack(true);
		advisors.push(advisor);
	}

	/**
	 * @inheritDoc
	 */
	public SecurityAdvisor popAdvisor()
	{
		Stack advisors = getAdvisorStack(false);
		if (advisors == null) return null;

		SecurityAdvisor rv = null;

		if (advisors.size() > 0)
		{
			rv = (SecurityAdvisor) advisors.pop();
		}

		if (advisors.isEmpty())
		{
			dropAdvisorStack();
		}

		return rv;
	}

	/**
	 * @inheritDoc
	 */
	public boolean hasAdvisors()
	{
		Stack advisors = getAdvisorStack(false);
		if (advisors == null) return false;

		return !advisors.isEmpty();
	}

	/**
	 * @inheritDoc
	 */
	public void clearAdvisors()
	{
		dropAdvisorStack();
	}
}
