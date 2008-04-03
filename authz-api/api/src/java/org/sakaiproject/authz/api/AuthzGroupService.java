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

package org.sakaiproject.authz.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.javax.PagingPosition;

/**
 * <p>
 * AuthzGroupService manages authorization grops.
 * </p>
 */
public interface AuthzGroupService extends EntityProducer
{
	/** The type string for this application: should not change over time as it may be stored in various parts of persistent entities. */
	static final String APPLICATION_ID = "sakai:authzGroup";

	/** This string starts the references to resources in this service. */
	static final String REFERENCE_ROOT = "/realm";

	/** Name for the event of adding an AuthzGroup. */
	static final String SECURE_ADD_AUTHZ_GROUP = "realm.add";

	/** Name for the event of removing an AuthzGroup. */
	static final String SECURE_REMOVE_AUTHZ_GROUP = "realm.del";

	/** Name for the event of updating an AuthzGroup. */
	static final String SECURE_UPDATE_AUTHZ_GROUP = "realm.upd";

	/** Name for the event of updating ones own relationship in an AuthzGroup. */
	static final String SECURE_UPDATE_OWN_AUTHZ_GROUP = "realm.upd.own";

	/** Standard role name for the anon. role. */
	static final String ANON_ROLE = ".anon";

	/** Standard role name for the auth. role. */
	static final String AUTH_ROLE = ".auth";

	/**
	 * Access a list of AuthzGroups that meet specified criteria, naturally sorted.
	 * 
	 * @param criteria
	 *        Selection criteria: AuthzGroups returned will match this string somewhere in their id, or provider group id.
	 * @param page
	 *        The PagePosition subset of items to return.
	 * @return The List (AuthzGroup) that meet specified criteria.
	 */
	List getAuthzGroups(String criteria, PagingPosition page);

	/**
	 * Access a list of AuthzGroups that meet specified criteria for a specified user_id
	 * 
	 * @param criteria
	 *        Selection criteria: AuthzGroups returned will match this string somewhere in their id
	 * @param user_id
	 *        Return only groups with user_id as a member
	 * @return The List (AuthzGroup) that meet specified criteria.
	 */
	List getAuthzUserGroupIds(String criteria, String user_id);

	/**
	 * Count the AuthzGroups that meet specified criteria.
	 * 
	 * @param criteria
	 *        Selection criteria: AuthzGroups returned will match this string somewhere in their id, or provider group id.
	 * @return The count of AuthzGroups that meet specified criteria.
	 */
	int countAuthzGroups(String criteria);

	/**
	 * Access an AuthzGroup.
	 * 
	 * @param id
	 *        The id string.
	 * @return The AuthzGroup.
	 * @exception GroupNotDefinedException
	 *            if not found.
	 */
	AuthzGroup getAuthzGroup(String id) throws GroupNotDefinedException;

	/**
	 * Check permissions for updating an AuthzGroup.
	 * 
	 * @param id
	 *        The id.
	 * @return true if the user is allowed to update the AuthzGroup, false if not.
	 */
	boolean allowUpdate(String id);

	/**
	 * Save the changes made to the AuthzGroup. The AuthzGroup must already exist, and the user must have permission to update.
	 * 
	 * @param azGroup
	 *        The AuthzGroup to save.
	 * @exception GroupNotDefinedException
	 *            if the AuthzGroup id is not defined.
	 * @exception AuthzPermissionException
	 *            if the current user does not have permission to update the AuthzGroup.
	 */
	void save(AuthzGroup azGroup) throws GroupNotDefinedException, AuthzPermissionException;

	/**
	 * Check permissions for adding an AuthzGroup.
	 * 
	 * @param id
	 *        The authzGroup id.
	 * @return true if the current user is allowed add the AuthzGroup, false if not.
	 */
	boolean allowAdd(String id);

	/**
	 * Add a new AuthzGroup
	 * 
	 * @param id
	 *        The AuthzGroup id.
	 * @return The new AuthzGroup.
	 * @exception GroupIdInvalidException
	 *            if the id is invalid.
	 * @exception GroupAlreadyDefinedException
	 *            if the id is already used.
	 * @exception AuthzPermissionException
	 *            if the current user does not have permission to add the AuthzGroup.
	 */
	AuthzGroup addAuthzGroup(String id) throws GroupIdInvalidException, GroupAlreadyDefinedException, AuthzPermissionException;

	/**
	 * Add a new AuthzGroup, as a copy of another AuthzGroup (except for id), and give a user "maintain" access based on the other's definition of "maintain".
	 * 
	 * @param id
	 *        The id.
	 * @param other
	 *        The AuthzGroup to copy into this new AuthzGroup.
	 * @param maintainUserId
	 *        Optional user id to get "maintain" access, or null if none.
	 * @return The new AuthzGroup object.
	 * @exception GroupIdInvalidException
	 *            if the id is invalid.
	 * @exception GroupAlreadyDefinedException
	 *            if the id is already used.
	 * @exception AuthzPermissionException
	 *            if the current user does not have permission to add the AuthzGroup.
	 */
	AuthzGroup addAuthzGroup(String id, AuthzGroup other, String maintainUserId) throws GroupIdInvalidException,
			GroupAlreadyDefinedException, AuthzPermissionException;

	/**
	 * Check permissions for removing an AuthzGroup.
	 * 
	 * @param id
	 *        The AuthzGroup id.
	 * @return true if the user is allowed to remove the AuthzGroup, false if not.
	 */
	boolean allowRemove(String id);

	/**
	 * Remove this AuthzGroup.
	 * 
	 * @param azGroup
	 *        The AuthzGroup to remove.
	 * @exception AuthzPermissionException
	 *            if the current user does not have permission to remove this AuthzGroup.
	 */
	void removeAuthzGroup(AuthzGroup azGroup) throws AuthzPermissionException;

	/**
	 * Remove the AuthzGroup with this id, if it exists (fails quietly if not).
	 * 
	 * @param id
	 *        The AuthzGroup id.
	 * @exception AuthzPermissionException
	 *            if the current user does not have permission to remove this AthzGroup.
	 */
	void removeAuthzGroup(String id) throws AuthzPermissionException;

	/**
	 * Access the internal reference which can be used to access the AuthzGroup from within the system.
	 * 
	 * @param id
	 *        The AuthzGroup id.
	 * @return The the internal reference which can be used to access the AuthzGroup from within the system.
	 */
	String authzGroupReference(String id);

	/**
	 * Cause the current user to join the given AuthzGroup with this role, using SECURE_UPDATE_OWN_AUTHZ_GROUP security.
	 * 
	 * @param authzGroupId
	 *        the id of the AuthzGroup.
	 * @param role
	 *        the name of the Role.
	 * @throws GroupNotDefinedException
	 *         if the authzGroupId or role are not defined.
	 * @throws AuthzPermissionException
	 *         if the current user does not have permission to join this AuthzGroup.
	 */
	void joinGroup(String authzGroupId, String role) throws GroupNotDefinedException, AuthzPermissionException;

	/**
	 * Cause the current user to join the given AuthzGroup with this role, using SECURE_UPDATE_OWN_AUTHZ_GROUP security, 
	 * provided that adding this user would not cause the group to exceed the specified size.
	 * 
	 * @param authzGroupId
	 *        the id of the AuthzGroup.
	 * @param role
	 *        the name of the Role.
	 * @param maxSize
	 *        the maximum permitted size of the AuthzGroup.
	 * @throws GroupNotDefinedException
	 *         if the authzGroupId or role are not defined.
	 * @throws AuthzPermissionException
	 *         if the current user does not have permission to join this AuthzGroup.
	 * @throws GroupFullException
	 *         if adding the current user would cause the AuthzGroup to become larger than maxSize.
	 */
	void joinGroup(String authzGroupId, String role, int maxSize) throws GroupNotDefinedException, AuthzPermissionException, GroupFullException;
	
	/**
	 * Cause the current user to unjoin the given AuthzGroup, using SECURE_UPDATE_OWN_AUTHZ_GROUP security.
	 * 
	 * @param authzGroupId
	 *        the id of the AuthzGroup.
	 * @throws GroupNotDefinedException
	 *         if the authzGroupId or role are not defined.
	 * @throws AuthzPermissionException
	 *         if the current user does not have permission to unjoin this site.
	 */
	void unjoinGroup(String authzGroupId) throws GroupNotDefinedException, AuthzPermissionException;

	/**
	 * Check permissions for the current user joining a group.
	 * 
	 * @param id
	 *        The AuthzGroup id.
	 * @return true if the user is allowed to join the group, false if not.
	 */
	boolean allowJoinGroup(String id);

	/**
	 * Check permissions for the current user unjoining a group.
	 * 
	 * @param id
	 *        The AuthzGroup id.
	 * @return true if the user is allowed to unjoin the group, false if not.
	 */
	boolean allowUnjoinGroup(String id);

	/**
	 * Test if this user is allowed to perform the function in the named AuthzGroup.
	 * 
	 * @param userId
	 *        The user id.
	 * @param function
	 *        The function to open.
	 * @param azGroupId
	 *        The AuthzGroup id to consult, if it exists.
	 * @return true if this user is allowed to perform the function in the named AuthzGroup, false if not.
	 */
	boolean isAllowed(String userId, String function, String azGroupId);

	/**
	 * Test if this user is allowed to perform the function in the named AuthzGroups.
	 * 
	 * @param userId
	 *        The user id.
	 * @param function
	 *        The function to open.
	 * @param azGroups
	 *        A collection of AuthzGroup ids to consult.
	 * @return true if this user is allowed to perform the function in the named AuthzGroups, false if not.
	 */
	boolean isAllowed(String userId, String function, Collection azGroups);

	/**
	 * Get the set of user ids of users who are allowed to perform the function in the named AuthzGroups.
	 * 
	 * @param function
	 *        The function to check.
	 * @param azGroups
	 *        A collection of the ids of AuthzGroups to consult.
	 * @return the Set (String) of user ids of users who are allowed to perform the function in the named AuthzGroups.
	 */
	Set getUsersIsAllowed(String function, Collection azGroups);

	/**
	 * Get the set of AuthzGroup ids in which this user is allowed to perform this function.
	 * 
	 * @param userId
	 *        The user id.
	 * @param function
	 *        The function to check.
	 * @param azGroups
	 *        The Collection of AuthzGroup ids to search; if null, search them all.
	 * @return the Set (String) of AuthzGroup ids in which this user is allowed to perform this function.
	 */
	Set getAuthzGroupsIsAllowed(String userId, String function, Collection azGroups);

	/**
	 * Get the set of functions that users with this role in these AuthzGroups are allowed to perform.
	 * 
	 * @param role
	 *        The role name.
	 * @param azGroups
	 *        A collection of AuthzGroup ids to consult.
	 * @return the Set (String) of functions that users with this role in these AuthzGroups are allowed to perform
	 */
	Set getAllowedFunctions(String role, Collection azGroups);

	/**
	 * Get the role name for this user in this AuthzGroup, if the user has membership (the membership gives the user a single role).
	 * 
	 * @param userId
	 *        The user id.
	 * @param function
	 *        The function to open.
	 * @param azGroupId
	 *        The AuthzGroup id to consult, if it exists.
	 * @return the role name for this user in this AuthzGroup, if the user has active membership, or null if not.
	 */
	String getUserRole(String userId, String azGroupId);

	/**
	 * Get the role name for each user in the userIds Collection in this AuthzGroup, for each of these users who have membership (membership gives the user a single role).
	 * 
	 * @param userIds
	 *        The user ids as a Collection of String.
	 * @param function
	 *        The function to open.
	 * @param azGroupId
	 *        The AuthzGroup id to consult, if it exists.
	 * @return A Map (userId (String) -> role name (String)) of role names for each user who have active membership; if the user does not, it will not be in the Map.
	 */
	Map getUsersRole(Collection userIds, String azGroupId);

	/**
	 * Refresh this user's AuthzGroup external definitions.
	 * 
	 * @param userId
	 *        The user id.
	 */
	void refreshUser(String userId);

	/**
	 * Create a new AuthzGroup, as a copy of another AuthzGroup (except for id), and give a user "maintain" access based on the other's definition of "maintain", but do not store - it can be saved with a save() call
	 * 
	 * @param id
	 *        The id.
	 * @param other
	 *        The AuthzGroup to copy into this new AuthzGroup (or null if none).
	 * @param maintainUserId
	 *        Optional user id to get "maintain" access, or null if none.
	 * @return The new AuthzGroup object.
	 * @exception GroupAlreadyDefinedException
	 *            if the id is already used.
	 */
	AuthzGroup newAuthzGroup(String id, AuthzGroup other, String maintainUserId) throws GroupAlreadyDefinedException;
	
	/**
	 * Gets the IDs of the AuthzGroups with the given provider ID.
	 * 
	 * @return The Set of Strings representing authzGroup IDs (such as /site/1234 or /site/1234/group/5678) for all authzGroups with the given providerId.
	 */
	public Set getAuthzGroupIds(String providerId);

	/**
	 * Gets the provider IDs associated with an AuthzGroup.
	 * 
	 * @return The Set of Strings representing external group IDs, as recognized by the GroupProvider implementation, that are associated with the given groupId. These strings
	 * must not be "compound IDs", as defined by the GroupProvider's String[] unpackId(String id) method.
	 */
	public Set getProviderIds(String authzGroupId); 
}
