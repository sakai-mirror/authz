/**********************************************************************************
 * $URL: https://source.sakaiproject.org/svn/authz/tags/sakai_2-3-0/authz-impl/impl/src/java/org/sakaiproject/authz/impl/SakaiSecurityTest.java $
 * $Id: SakaiSecurityTest.java 7320 2006-04-01 20:03:17Z ggolden@umich.edu $
 ***********************************************************************************
 *
 * Copyright (c) 2006 The Sakai Foundation.
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

import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

/**
 * <p>
 * SakaiSecurity extends the Sakai security service providing the dependency injectors for testing.
 * </p>
 */
public class SakaiSecurityTest extends SakaiSecurity
{
	/**
	 * @return the ThreadLocalManager collaborator.
	 */
	protected ThreadLocalManager threadLocalManager()
	{
		return null;
	}

	/**
	 * @return the AuthzGroupService collaborator.
	 */
	protected AuthzGroupService authzGroupService()
	{
		return null;
	}

	/**
	 * @return the UserDirectoryService collaborator.
	 */
	protected UserDirectoryService userDirectoryService()
	{
		return null;
	}

	/**
	 * @return the MemoryService collaborator.
	 */
	protected MemoryService memoryService()
	{
		return null;
	}

	/**
	 * @return the EntityManager collaborator.
	 */
	protected EntityManager entityManager()
	{
		return null;
	}

	public boolean isAdminToolsUser(User user) 
	{
		// TODO Auto-generated method stub
		return false;
	}
}
