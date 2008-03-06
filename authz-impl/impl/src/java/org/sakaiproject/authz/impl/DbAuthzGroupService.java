/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006 2007 The Sakai Foundation.
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

import java.sql.Connection;
import java.sql.ResultSet;
//ONC-341
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.db.api.SqlServiceDeadlockException;
import org.sakaiproject.javax.PagingPosition;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.BaseDbFlatStorage;
import org.sakaiproject.util.BaseResourceProperties;
import org.sakaiproject.util.BaseResourcePropertiesEdit;
import org.sakaiproject.util.StringUtil;

/**
 * <p>
 * DbAuthzGroupService is an extension of the BaseAuthzGroupService with database storage.
 * </p>
 */
public abstract class DbAuthzGroupService extends BaseAuthzGroupService
{
	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(DbAuthzGroupService.class);

	/** All the event functions we know exist on the db. */
	protected Collection m_functionCache = new HashSet();

	/** All the event role names we know exist on the db. */
	protected Collection m_roleNameCache = new HashSet();

	/** Table name for realms. */
	protected String m_realmTableName = "SAKAI_REALM";

	/** Table name for realm properties. */
	protected String m_realmPropTableName = "SAKAI_REALM_PROPERTY";

	/** ID field for realm. */
	protected String m_realmIdFieldName = "REALM_ID";

	/** AuthzGroup dbid field. */
	protected String m_realmDbidField = "REALM_KEY";

	/** All "fields" for realm reading. */
	protected String[] m_realmReadFieldNames = { "REALM_ID", "PROVIDER_ID",
			"(select MAX(ROLE_NAME) from SAKAI_REALM_ROLE where ROLE_KEY = MAINTAIN_ROLE)", "CREATEDBY", "MODIFIEDBY", "CREATEDON",
			"MODIFIEDON", "REALM_KEY" };

	/** All "fields" for realm update. */
	protected String[] m_realmUpdateFieldNames = { "REALM_ID", "PROVIDER_ID",
			"MAINTAIN_ROLE = (select MAX(ROLE_KEY) from SAKAI_REALM_ROLE where ROLE_NAME = ?)", "CREATEDBY", "MODIFIEDBY",
			"CREATEDON", "MODIFIEDON" };

	/** All "fields" for realm insert. */
	protected String[] m_realmInsertFieldNames = { "REALM_ID", "PROVIDER_ID", "MAINTAIN_ROLE", "CREATEDBY", "MODIFIEDBY",
			"CREATEDON", "MODIFIEDON" };

	/** All "field values" for realm insert. */
	protected String[] m_realmInsertValueNames = { "?", "?", "(select MAX(ROLE_KEY) from SAKAI_REALM_ROLE where ROLE_NAME = ?)",
			"?", "?", "?", "?" };

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Dependencies
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * @return the ServerConfigurationService collaborator.
	 */
	protected abstract SqlService sqlService();

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Configuration
	 *********************************************************************************************************************************************************************************************************************************************************/

	/** If true, we do our locks in the remote database, otherwise we do them here. */
	protected boolean m_useExternalLocks = true;

	/**
	 * Configuration: set the external locks value.
	 * 
	 * @param value
	 *        The external locks value.
	 */
	public void setExternalLocks(String value)
	{
		m_useExternalLocks = new Boolean(value).booleanValue();
	}

	/** Configuration: to run the ddl on init or not. */
	protected boolean m_autoDdl = false;

	/**
	 * Configuration: to run the ddl on init or not.
	 * 
	 * @param value
	 *        the auto ddl value.
	 */
	public void setAutoDdl(String value)
	{
		m_autoDdl = new Boolean(value).booleanValue();
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Init and Destroy
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		try
		{
			// if we are auto-creating our schema, check and create
			if (m_autoDdl)
			{
				sqlService().ddl(this.getClass().getClassLoader(), "sakai_realm");
				sqlService().ddl(this.getClass().getClassLoader(), "sakai_realm_2_4_0_001");
			}

			super.init();

			// pre-cache role and function names
			cacheRoleNames();
			cacheFunctionNames();

			M_log.info("init(): table: " + m_realmTableName + " external locks: " + m_useExternalLocks);
		}
		catch (Throwable t)
		{
			M_log.warn("init(): ", t);
		}
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * BaseAuthzGroupService extensions
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Construct a Storage object.
	 * 
	 * @return The new storage object.
	 */
	protected Storage newStorage()
	{
		return new DbStorage();

	} // newStorage

	/**
	 * Check / assure this role name is defined.
	 * 
	 * @param name
	 *        the role name.
	 */
	protected void checkRoleName(String name)
	{
		if (name == null) return;
		name = name.intern();

		// check the cache to see if the role name already exists
		if (m_roleNameCache.contains(name)) return;

		// see if we have it in the db
		String statement = "select count(1) from SAKAI_REALM_ROLE where ROLE_NAME = ?";
		Object[] fields = new Object[1];
		fields[0] = name;

		List results = sqlService().dbRead(statement, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int count = result.getInt(1);
					return new Integer(count);
				}
				catch (SQLException ignore)
				{
					return null;
				}
			}
		});

		boolean rv = false;
		if (!results.isEmpty())
		{
			rv = ((Integer) results.get(0)).intValue() > 0;
		}

		// write if we didn't find it
		if (!rv)
		{
			if ("oracle".equals(sqlService().getVendor()))
			{
				statement = "insert into SAKAI_REALM_ROLE (ROLE_KEY, ROLE_NAME) values (SAKAI_REALM_ROLE_SEQ.NEXTVAL, ?)";
			}
			else if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "insert into SAKAI_REALM_ROLE (ROLE_KEY, ROLE_NAME) values (DEFAULT, ?)";
			}
			else
			// if ("hsqldb".equals(m_sql.getVendor()))
			{
				statement = "insert into SAKAI_REALM_ROLE (ROLE_KEY, ROLE_NAME) values (NEXT VALUE FOR SAKAI_REALM_ROLE_SEQ, ?)";
			}

			// write, but if it fails, we don't really care - it will fail if another app server has just written this role name
			sqlService().dbWriteFailQuiet(null, statement, fields);
		}

		synchronized (m_roleNameCache)
		{
			m_roleNameCache.add(name);
		}
	}

	/**
	 * Read all the role records, caching them
	 */
	protected void cacheRoleNames()
	{
		synchronized (m_roleNameCache)
		{
			String statement = "select ROLE_NAME from SAKAI_REALM_ROLE";
			List results = sqlService().dbRead(statement, null, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String name = result.getString(1);
						m_roleNameCache.add(name);
					}
					catch (SQLException ignore)
					{
					}

					return null;
				}
			});
		}
	}

	/**
	 * Check / assure this function name is defined.
	 * 
	 * @param name
	 *        the role name.
	 */
	protected void checkFunctionName(String name)
	{
		if (name == null) return;
		name = name.intern();

		// check the cache to see if the function name already exists
		if (m_functionCache.contains(name)) return;

		// see if we have this on the db
		String statement = "select count(1) from SAKAI_REALM_FUNCTION where FUNCTION_NAME = ?";
		Object[] fields = new Object[1];
		fields[0] = name;

		List results = sqlService().dbRead(statement, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int count = result.getInt(1);
					return new Integer(count);
				}
				catch (SQLException ignore)
				{
					return null;
				}
			}
		});

		boolean rv = false;
		if (!results.isEmpty())
		{
			rv = ((Integer) results.get(0)).intValue() > 0;
		}

		// write if we didn't find it
		if (!rv)
		{
			if ("oracle".equals(sqlService().getVendor()))
			{
				statement = "insert into SAKAI_REALM_FUNCTION (FUNCTION_KEY, FUNCTION_NAME) values (SAKAI_REALM_FUNCTION_SEQ.NEXTVAL, ?)";
			}
			else if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "insert into SAKAI_REALM_FUNCTION (FUNCTION_KEY, FUNCTION_NAME) values (DEFAULT, ?)";
			}
			else
			// if ("hsqldb".equals(m_sql.getVendor()))
			{
				statement = "insert into SAKAI_REALM_FUNCTION (FUNCTION_KEY, FUNCTION_NAME) values (NEXT VALUE FOR SAKAI_REALM_FUNCTION_SEQ, ?)";
			}

			// write, but if it fails, we don't really care - it will fail if another app server has just written this function
			sqlService().dbWriteFailQuiet(null, statement, fields);
		}

		// cache the existance of the function name
		synchronized (m_functionCache)
		{
			m_functionCache.add(name);
		}
	}

	/**
	 * Read all the function records, caching them
	 */
	protected void cacheFunctionNames()
	{
		synchronized (m_functionCache)
		{
			String statement = "select FUNCTION_NAME from SAKAI_REALM_FUNCTION";
			List results = sqlService().dbRead(statement, null, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String name = result.getString(1);
						m_functionCache.add(name);
					}
					catch (SQLException ignore)
					{
					}

					return null;
				}
			});
		}
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Storage implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Covers for the BaseXmlFileStorage, providing AuthzGroup and RealmEdit parameters
	 */
	protected class DbStorage extends BaseDbFlatStorage implements Storage, SqlReader
	{
		/**
		 * Construct.
		 */
		public DbStorage()
		{
			super(m_realmTableName, m_realmIdFieldName, m_realmReadFieldNames, m_realmPropTableName, m_useExternalLocks, null,
					sqlService());
			m_reader = this;

			setDbidField(m_realmDbidField);
			setWriteFields(m_realmUpdateFieldNames, m_realmInsertFieldNames, m_realmInsertValueNames);

			setLocking(false);

			// setSortField(m_realmSortField, null);
		}

		public boolean check(String id)
		{
			return super.checkResource(id);
		}

		public AuthzGroup get(String id)
		{
			return get(null, id);
		}

		protected AuthzGroup get(Connection conn, String id)
		{
			// read the base
			BaseAuthzGroup rv = (BaseAuthzGroup) super.getResource(conn, id);

			completeGet(conn, rv, false);

			return rv;
		}

		/**
		 * Complete the read process once the basic realm info has been read
		 * 
		 * @param realm
		 *        The real to complete
		 */
		public void completeGet(BaseAuthzGroup realm)
		{
			completeGet(null, realm, false);
		}

		/**
		 * Complete the read process once the basic realm info has been read
		 * 
		 * @param conn
		 *        optional SQL connection to use.
		 * @param realm
		 *        The real to complete.
		 * @param updateProvider
		 *        if true, update and store the provider info.
		 */
		protected void completeGet(Connection conn, final BaseAuthzGroup realm, boolean updateProvider)
		{
			if (realm == null) return;

			if (!realm.m_lazy) return;
			realm.m_lazy = false;

			// update the db and realm with latest provider
			if (updateProvider)
			{
				refreshAuthzGroup(realm);
			}

			// read the properties
			if (((BaseResourceProperties) realm.m_properties).isLazy())
			{
				((BaseResourcePropertiesEdit) realm.m_properties).setLazy(false);
				super.readProperties(conn, realm.getKey(), realm.m_properties);
			}

			// read the roles and role functions
			String sql = "SELECT SAKAI_REALM_ROLE.ROLE_NAME, SAKAI_REALM_FUNCTION.FUNCTION_NAME FROM SAKAI_REALM_RL_FN"
					+ " INNER JOIN SAKAI_REALM ON SAKAI_REALM.REALM_KEY = SAKAI_REALM_RL_FN.REALM_KEY AND SAKAI_REALM.REALM_ID = ?"
					+ " INNER JOIN SAKAI_REALM_ROLE ON SAKAI_REALM_ROLE.ROLE_KEY = SAKAI_REALM_RL_FN.ROLE_KEY"
					+ " INNER JOIN SAKAI_REALM_FUNCTION ON SAKAI_REALM_FUNCTION.FUNCTION_KEY = SAKAI_REALM_RL_FN.FUNCTION_KEY";
			Object fields[] = new Object[1];
			fields[0] = realm.getId();
			List all = m_sql.dbRead(conn, sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						// get the fields
						String roleName = result.getString(1);
						String functionName = result.getString(2);

						// make the role if needed
						BaseRole role = (BaseRole) realm.m_roles.get(roleName);
						if (role == null)
						{
							role = new BaseRole(roleName);
							realm.m_roles.put(role.getId(), role);
						}

						// add the function to the role
						role.allowFunction(functionName);

						return null;
					}
					catch (SQLException ignore)
					{
						return null;
					}
				}
			});

			// read the role descriptions
			sql = "SELECT SAKAI_REALM_ROLE.ROLE_NAME, SAKAI_REALM_ROLE_DESC.DESCRIPTION, SAKAI_REALM_ROLE_DESC.PROVIDER_ONLY"
					+ " FROM SAKAI_REALM_ROLE_DESC"
					+ " INNER JOIN SAKAI_REALM ON SAKAI_REALM.REALM_KEY = SAKAI_REALM_ROLE_DESC.REALM_KEY AND SAKAI_REALM.REALM_ID = ?"
					+ " INNER JOIN SAKAI_REALM_ROLE ON SAKAI_REALM_ROLE.ROLE_KEY = SAKAI_REALM_ROLE_DESC.ROLE_KEY";
			m_sql.dbRead(conn, sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						// get the fields
						String roleName = result.getString(1);
						String description = result.getString(2);
						boolean providerOnly = "1".equals(result.getString(3));

						// find the role - create it if needed
						// Note: if the role does not yet exist, it has no functions
						BaseRole role = (BaseRole) realm.m_roles.get(roleName);
						if (role == null)
						{
							role = new BaseRole(roleName);
							realm.m_roles.put(role.getId(), role);
						}

						// set the description
						role.setDescription(description);
						
						// set the provider only flag
						role.setProviderOnly(providerOnly);

						return null;
					}
					catch (SQLException ignore)
					{
						return null;
					}
				}
			});

			// read the role grants
			sql = "SELECT SAKAI_REALM_ROLE.ROLE_NAME, SAKAI_REALM_RL_GR.USER_ID, SAKAI_REALM_RL_GR.ACTIVE, SAKAI_REALM_RL_GR.PROVIDED"
					+ " FROM SAKAI_REALM_RL_GR"
					+ " INNER JOIN SAKAI_REALM ON SAKAI_REALM.REALM_KEY = SAKAI_REALM_RL_GR.REALM_KEY AND SAKAI_REALM.REALM_ID = ?"
					+ " INNER JOIN SAKAI_REALM_ROLE ON SAKAI_REALM_ROLE.ROLE_KEY = SAKAI_REALM_RL_GR.ROLE_KEY";			
			all = m_sql.dbRead(conn, sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						// get the fields
						String roleName = result.getString(1);
						String userId = result.getString(2);
						String active = result.getString(3);
						String provided = result.getString(4);

						// give the user one and only one role grant - there should be no second...
						BaseMember grant = (BaseMember) realm.m_userGrants.get(userId);
						if (grant == null)
						{
							// find the role - if it does not exist, create it for this grant
							// NOTE: it would have no functions or description
							BaseRole role = (BaseRole) realm.m_roles.get(roleName);
							if (role == null)
							{
								role = new BaseRole(roleName);
								realm.m_roles.put(role.getId(), role);
							}

							grant = new BaseMember(role, "1".equals(active), "1".equals(provided), userId);

							realm.m_userGrants.put(userId, grant);
						}
						else
						{
							M_log.warn("completeGet: additional user - role grant: " + userId + " " + roleName);
						}

						return null;
					}
					catch (SQLException ignore)
					{
						return null;
					}
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		public List getAuthzGroups(String criteria, PagingPosition page)
		{
			List rv = null;

			if (criteria != null)
			{
				criteria = "%" + criteria + "%";
				String where = "( UPPER(REALM_ID) like UPPER(?) or UPPER(PROVIDER_ID) like UPPER(?) )";
				Object[] fields = new Object[2];
				fields[0] = criteria;
				fields[1] = criteria;

				// paging
				if (page != null)
				{
					// adjust to the size of the set found
					// page.validate(rv.size());

					rv = getSelectedResources(where, fields, page.getFirst(), page.getLast());
				}
				else
				{
					rv = getSelectedResources(where, fields);
				}
			}

			else
			{
				// paging
				if (page != null)
				{
					// adjust to the size of the set found
					// page.validate(rv.size());

					rv = getAllResources(page.getFirst(), page.getLast());
				}
				else
				{
					rv = getAllResources();
				}
			}

			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public int countAuthzGroups(String criteria)
		{
			int rv = 0;

			if (criteria != null)
			{
				criteria = "%" + criteria + "%";
				String where = "( UPPER(REALM_ID) like UPPER(?) or UPPER(PROVIDER_ID) like UPPER(?) )";
				Object[] fields = new Object[2];
				fields[0] = criteria;
				fields[1] = criteria;

				rv = countSelectedResources(where, fields);
			}

			else
			{
				rv = countAllResources();
			}

			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public Set getProviderIds(String authzGroupId)
		{
			String statement = "select srp.PROVIDER_ID from SAKAI_REALM sr INNER JOIN SAKAI_REALM_PROVIDER srp on sr.REALM_KEY = srp.REALM_KEY where sr.REALM_ID=?";
			List results = sqlService().dbRead(statement, new Object[] {authzGroupId}, null);	
			if(results == null)
			{
				return new HashSet();
			}
			return new HashSet(results);
		}

		/**
		 * {@inheritDoc}
		 */
		public Set getAuthzGroupIds(String providerId)
		{
			String statement = "select sr.REALM_ID from SAKAI_REALM sr INNER JOIN SAKAI_REALM_PROVIDER srp on sr.REALM_KEY = srp.REALM_KEY where srp.PROVIDER_ID=?";
			List results = sqlService().dbRead(statement, new Object[] {providerId}, null);	
			if(results == null)
			{
				return new HashSet();
			}
			return new HashSet(results);
		}
		
		/**
		 * {@inheritDoc}
		 */
		public Set getAuthzGroupsIsAllowed(String userId, String lock, Collection azGroups)
		{
			// further limited to only those authz groups in the azGroups parameter if not null

			// if azGroups is not null, but empty, we can short-circut and return an empty set
			// or if the lock is null
			if (((azGroups != null) && azGroups.isEmpty()) || lock == null)
			{
				return new HashSet();
			}

			// Just like unlock, except we use all realms and get their ids
			// Note: consider over all realms just those realms where there's a grant of a role that satisfies the lock
			// Ignore realms where anon or auth satisfy the lock.

			boolean auth = (userId != null) && (!userDirectoryService().getAnonymousUser().getId().equals(userId));
			String sql = "";
			StringBuffer sqlBuf = null;

			// Assemble SQL
			sqlBuf = new StringBuffer();
			sqlBuf.append("select SR.REALM_ID ");
			sqlBuf.append("from SAKAI_REALM_FUNCTION SRF ");
			sqlBuf.append("inner join SAKAI_REALM_RL_FN SRRF on SRF.FUNCTION_KEY = SRRF.FUNCTION_KEY ");
			sqlBuf.append("inner join SAKAI_REALM_RL_GR SRRG on SRRF.ROLE_KEY = SRRG.ROLE_KEY and SRRF.REALM_KEY = SRRG.REALM_KEY ");
			sqlBuf.append("inner join SAKAI_REALM SR on SRRF.REALM_KEY = SR.REALM_KEY ");
			sqlBuf.append("where SRF.FUNCTION_NAME = ? ");
			sqlBuf.append("and SRRG.USER_ID = ? ");
			sqlBuf.append("and SRRG.ACTIVE = '1' ");

			if (azGroups != null)
			{
				sqlBuf.append("and SR.REALM_ID in (");
				for (int i = 0; i < azGroups.size() - 1; i++)
				{
					sqlBuf.append("?,");
				}
				sqlBuf.append("?) ");
			}

			sql = sqlBuf.toString();

			int size = 2;
			if (azGroups != null)
			{
				size += azGroups.size();
			}
			Object[] fields = new Object[size];
			fields[0] = lock;
			fields[1] = userId;
			if (azGroups != null)
			{
				int pos = 2;
				for (Iterator i = azGroups.iterator(); i.hasNext();)
				{
					fields[pos++] = i.next();
				}
			}

			// Get resultset
			List results = m_sql.dbRead(sql, fields, null);
			Set rv = new HashSet();
			rv.addAll(results);

			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public AuthzGroup put(String id)
		{
			BaseAuthzGroup rv = (BaseAuthzGroup) super.putResource(id, fields(id, null, false));
			if (rv != null)
			{
				rv.activate();
			}

			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public AuthzGroup edit(String id)
		{
			BaseAuthzGroup edit = (BaseAuthzGroup) super.editResource(id);

			if (edit != null)
			{
				edit.activate();
				completeGet(null, edit, true);
			}

			return edit;
		}

		/**
		 * @inheritDoc
		 */
		public void save(final AuthzGroup edit)
		{
			// pre-check the roles and functions to make sure they are all defined
			for (Iterator iRoles = ((BaseAuthzGroup) edit).m_roles.values().iterator(); iRoles.hasNext();)
			{
				Role role = (Role) iRoles.next();

				// make sure the role name is defined / define it
				checkRoleName(role.getId());

				for (Iterator iFunctions = role.getAllowedFunctions().iterator(); iFunctions.hasNext();)
				{
					String function = (String) iFunctions.next();

					// make sure the role name is defined / define it
					checkFunctionName(function);
				}
			}

			// run our save code in a transaction that will restart on deadlock
			// if deadlock retry fails, or any other error occurs, a runtime error will be thrown
			m_sql.transact(new Runnable()
			{
				public void run()
				{
					saveTx(edit);					
				}
			}, "azg:" + edit.getId());

			// update with the provider
			refreshAuthzGroup((BaseAuthzGroup) edit);
		}

		/**
		 * The transaction code to save the azg.
		 * 
		 * @param edit
		 *        The azg to save.
		 */
		protected void saveTx(AuthzGroup edit)
		{
			// update SAKAI_REALM_RL_FN: read, diff with the edit, add and delete
			save_REALM_RL_FN(edit);

			// update SAKAI_REALM_RL_GR
			save_REALM_RL_GR(edit);

			// update SAKAI_REALM_PROVIDER
			save_REALM_PROVIDER(edit);

			// update SAKAI_REALM_ROLE_DESC
			save_REALM_ROLE_DESC(edit);

			// update the main realm table and properties
			super.commitResource(edit, fields(edit.getId(), ((BaseAuthzGroup) edit), true), edit.getProperties(), ((BaseAuthzGroup) edit)
					.getKey());
		}

		protected void save_REALM_RL_FN(AuthzGroup azg)
		{
			// add what we have in the azg, unless we see it in the db
			final Set<RoleAndFunction> toAdd = new HashSet<RoleAndFunction>();
			for (Iterator iRoles = ((BaseAuthzGroup) azg).m_roles.values().iterator(); iRoles.hasNext();)
			{
				Role role = (Role) iRoles.next();
				for (Iterator iFunctions = role.getAllowedFunctions().iterator(); iFunctions.hasNext();)
				{
					String function = (String) iFunctions.next();
					toAdd.add(new RoleAndFunction(role.getId(), function));
				}
			}

			// delete anything we see in the db we don't have in the azg
			final Set<RoleAndFunction> toDelete = new HashSet<RoleAndFunction>();

			// read what we have there now
			String sql = "SELECT RR.ROLE_NAME, RF.FUNCTION_NAME FROM SAKAI_REALM_RL_FN RRF"
					+ " INNER JOIN SAKAI_REALM R ON RRF.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
					+ " INNER JOIN SAKAI_REALM_ROLE RR ON RRF.ROLE_KEY = RR.ROLE_KEY"
					+ " INNER JOIN SAKAI_REALM_FUNCTION RF ON RRF.FUNCTION_KEY = RF.FUNCTION_KEY";
			Object fields[] = new Object[1];
			fields[0] = caseId(azg.getId());
			m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String role = result.getString(1);
						String function = result.getString(2);
						RoleAndFunction raf = new RoleAndFunction(role, function);

						// if we have it in the set toAdd, we can remove it (it's alredy on the db)
						if (toAdd.contains(raf))
						{
							toAdd.remove(raf);
						}

						// if we don't have it in the azg, we need to delete it
						else
						{
							toDelete.add(raf);
						}
					}
					catch (Throwable e)
					{
						M_log.warn("save_REALM_RL_FN: " + e.toString());
					}

					return null;
				}
			});

			fields = new Object[3];
			fields[0] = caseId(azg.getId());

			// delete what we need to
			if ("mysql".equals(sqlService().getVendor()))
			{
				sql = "DELETE RRF FROM SAKAI_REALM_RL_FN RRF"
						+ " INNER JOIN SAKAI_REALM R ON RRF.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
						+ " INNER JOIN SAKAI_REALM_ROLE RR ON RRF.ROLE_KEY = RR.ROLE_KEY AND RR.ROLE_NAME = ?"
						+ " INNER JOIN SAKAI_REALM_FUNCTION RF ON RRF.FUNCTION_KEY = RF.FUNCTION_KEY AND RF.FUNCTION_NAME = ?";
			}
			else
			{
				sql = "DELETE FROM SAKAI_REALM_RL_FN" 
					+ " WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)"
					+ " AND ROLE_KEY IN (SELECT ROLE_KEY FROM SAKAI_REALM_ROLE WHERE ROLE_NAME = ?)"
					+ " AND FUNCTION_KEY IN (SELECT FUNCTION_KEY FROM SAKAI_REALM_FUNCTION WHERE FUNCTION_NAME = ?)";
				
			}
			for (RoleAndFunction raf : toDelete)
			{
				fields[1] = raf.role;
				fields[2] = raf.function;
				m_sql.dbWrite(sql, fields);
			}

			// add what we need to
			sql = "INSERT INTO SAKAI_REALM_RL_FN (REALM_KEY, ROLE_KEY, FUNCTION_KEY) VALUES ("
					+ " (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)," 
					+ " (SELECT ROLE_KEY FROM SAKAI_REALM_ROLE WHERE ROLE_NAME = ?),"
					+ " (SELECT FUNCTION_KEY FROM SAKAI_REALM_FUNCTION WHERE FUNCTION_NAME = ?))";
			for (RoleAndFunction raf : toAdd)
			{
				fields[1] = raf.role;
				fields[2] = raf.function;
				m_sql.dbWrite(sql, fields);
			}
		}

		protected void save_REALM_RL_GR(AuthzGroup azg)
		{
			// add what we have in the azg, unless we see it in the db
			final Set<UserAndRole> toAdd = new HashSet<UserAndRole>();
			for (Iterator i = ((BaseAuthzGroup) azg).m_userGrants.entrySet().iterator(); i.hasNext();)
			{
				Map.Entry entry = (Map.Entry) i.next();
				Member grant = (Member) entry.getValue();
				toAdd.add(new UserAndRole(grant.getUserId(), grant.getRole().getId(), grant.isActive(), grant.isProvided()));
			}

			// delete anything we see in the db we don't have in the azg
			final Set<UserAndRole> toDelete = new HashSet<UserAndRole>();

			// read what we have there now
			String sql = "SELECT RRG.USER_ID, RR.ROLE_NAME, RRG.ACTIVE, RRG.PROVIDED FROM SAKAI_REALM_RL_GR RRG "
					+ " INNER JOIN SAKAI_REALM R ON RRG.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
					+ " INNER JOIN SAKAI_REALM_ROLE RR ON RRG.ROLE_KEY = RR.ROLE_KEY";	
			Object fields[] = new Object[1];
			fields[0] = caseId(azg.getId());
			m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String userId = result.getString(1);
						String role = result.getString(2);
						boolean active = "1".equals(result.getString(3));
						boolean provided = "1".equals(result.getString(4));
						UserAndRole uar = new UserAndRole(userId, role, active, provided);

						// if we have it in the set toAdd, we can remove it (it's alredy on the db)
						if (toAdd.contains(uar))
						{
							toAdd.remove(uar);
						}

						// if we don't have it in the azg, we need to delete it
						else
						{
							toDelete.add(uar);
						}
					}
					catch (Throwable e)
					{
						M_log.warn("save_REALM_RL_GR: " + e.toString());
					}

					return null;
				}
			});

			fields = new Object[5];
			fields[0] = caseId(azg.getId());

			// delete what we need to
			if ("mysql".equals(sqlService().getVendor()))
			{
				sql = "DELETE RRG FROM SAKAI_REALM_RL_GR RRG"
						+ " INNER JOIN SAKAI_REALM R ON RRG.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
						+ " INNER JOIN SAKAI_REALM_ROLE RR ON RRG.ROLE_KEY = RR.ROLE_KEY AND RR.ROLE_NAME = ?"
						+ " WHERE RRG.USER_ID = ? AND RRG.ACTIVE = ? AND RRG.PROVIDED = ?";
			}
			else
			{
				sql = "DELETE FROM SAKAI_REALM_RL_GR"
					+ " WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)"
					+ " AND ROLE_KEY IN (SELECT ROLE_KEY FROM SAKAI_REALM_ROLE WHERE ROLE_NAME = ?)"
					+ " AND USER_ID = ? AND ACTIVE = ? AND PROVIDED = ?";				
			}
			for (UserAndRole uar : toDelete)
			{
				fields[1] = uar.role;
				fields[2] = uar.userId;
				fields[3] = uar.active ? "1" : "0";
				fields[4] = uar.provided ? "1" : "0";
				m_sql.dbWrite(sql, fields);
			}			

			// add what we need to
			sql = "INSERT INTO SAKAI_REALM_RL_GR (REALM_KEY, USER_ID, ROLE_KEY, ACTIVE, PROVIDED) VALUES ("
					+ " (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?), ?, "
					+ " (SELECT ROLE_KEY FROM SAKAI_REALM_ROLE WHERE ROLE_NAME = ?), ?, ?)";
			for (UserAndRole uar : toAdd)
			{
				fields[1] = uar.userId;
				fields[2] = uar.role;
				fields[3] = uar.active ? "1" : "0";
				fields[4] = uar.provided ? "1" : "0";
				m_sql.dbWrite(sql, fields);
			}
		}

		protected void save_REALM_PROVIDER(AuthzGroup azg)
		{
			// we we are not provider, delete any for this realm
			if ((azg.getProviderGroupId() == null) || (m_provider == null))
			{
				String sql = null;
				if ("mysql".equals(sqlService().getVendor()))
				{
					sql = "DELETE RP FROM SAKAI_REALM_PROVIDER RP"
							+ " INNER JOIN SAKAI_REALM R ON RP.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?";
				}
				else
				{
					sql = "DELETE FROM SAKAI_REALM_PROVIDER"
						+ " WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)";					
				}
				Object[] fields = new Object[1];
				fields[0] = caseId(azg.getId());
				m_sql.dbWrite(sql, fields);
				return;
			}

			// add what we have in the azg, unless we see it in the db
			final Set<String> toAdd = new HashSet<String>();
			String[] ids = m_provider.unpackId(azg.getProviderGroupId());
			if (ids != null)
			{
				for (String id : ids)
				{
					toAdd.add(id);
				}
			}

			// delete anything we see in the db we don't have in the azg
			final Set<String> toDelete = new HashSet<String>();

			// read what we have there now
			String sql = "SELECT RP.PROVIDER_ID FROM SAKAI_REALM_PROVIDER RP"
					+ " INNER JOIN SAKAI_REALM R ON RP.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?";
			Object fields[] = new Object[1];
			fields[0] = caseId(azg.getId());
			m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String provider = result.getString(1);

						// if we have it in the set toAdd, we can remove it (it's alredy on the db)
						if (toAdd.contains(provider))
						{
							toAdd.remove(provider);
						}

						// if we don't have it in the azg, we need to delete it
						else
						{
							toDelete.add(provider);
						}
					}
					catch (Throwable e)
					{
						M_log.warn("save_REALM_PROVIDER: " + e.toString());
					}

					return null;
				}
			});

			fields = new Object[2];
			fields[0] = caseId(azg.getId());

			// delete what we need to
			if ("mysql".equals(sqlService().getVendor()))
			{	
				sql = "DELETE RP FROM SAKAI_REALM_PROVIDER RP"
						+ " INNER JOIN SAKAI_REALM R ON RP.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
						+ " WHERE RP.PROVIDER_ID = ?";
			}
			else
			{
				sql = "DELETE FROM SAKAI_REALM_PROVIDER"
					+ " WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)"
					+ " AND PROVIDER_ID = ?";
			}
			for (String provider : toDelete)
			{
				fields[1] = provider;
				m_sql.dbWrite(sql, fields);
			}			

			// add what we need to
			sql = "INSERT INTO SAKAI_REALM_PROVIDER (REALM_KEY, PROVIDER_ID) VALUES ("
					+ " (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?), ?)";
			for (String provider : toAdd)
			{
				fields[1] = provider;
				m_sql.dbWrite(sql, fields);
			}
		}

		protected void save_REALM_ROLE_DESC(AuthzGroup azg)
		{
			// add what we have in the azg, unless we see it in the db
			final Set<RoleAndDescription> toAdd = new HashSet<RoleAndDescription>();
			for (Iterator iRoles = ((BaseAuthzGroup) azg).m_roles.values().iterator(); iRoles.hasNext();)
			{
				Role role = (Role) iRoles.next();
				toAdd.add(new RoleAndDescription(role.getId(), role.getDescription(), role.isProviderOnly()));
			}

			// delete anything we see in the db we don't have in the azg
			final Set<RoleAndDescription> toDelete = new HashSet<RoleAndDescription>();

			// read what we have there now
			String sql = "SELECT RR.ROLE_NAME, RRD.DESCRIPTION, RRD.PROVIDER_ONLY FROM SAKAI_REALM_ROLE_DESC RRD"
					+ " INNER JOIN SAKAI_REALM R ON RRD.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
					+ " INNER JOIN SAKAI_REALM_ROLE RR ON RRD.ROLE_KEY = RR.ROLE_KEY";
			Object fields[] = new Object[1];
			fields[0] = caseId(azg.getId());
			m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String role = result.getString(1);
						String description = result.getString(2);
						boolean providerOnly = "1".equals(result.getString(3));
						RoleAndDescription rad = new RoleAndDescription(role, description, providerOnly);

						// if we have it in the set toAdd, we can remove it (it's alredy on the db)
						if (toAdd.contains(rad))
						{
							toAdd.remove(rad);
						}

						// if we don't have it in the azg, we need to delete it
						else
						{
							toDelete.add(rad);
						}
					}
					catch (Throwable e)
					{
						M_log.warn("save_REALM_ROLE_DESC: " + e.toString());
					}

					return null;
				}
			});

			fields = new Object[2];
			fields[0] = caseId(azg.getId());

			// delete what we need to
			if ("mysql".equals(sqlService().getVendor()))
			{
				sql = "DELETE RRD FROM SAKAI_REALM_ROLE_DESC RRD"
						+ " INNER JOIN SAKAI_REALM R ON RRD.REALM_KEY = R.REALM_KEY AND R.REALM_ID = ?"
						+ " INNER JOIN SAKAI_REALM_ROLE RR ON RRD.ROLE_KEY = RR.ROLE_KEY AND RR.ROLE_NAME = ?";
			}
			else
			{
				sql = "DELETE FROM SAKAI_REALM_ROLE_DESC"
					+ " WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)"
					+ " AND ROLE_KEY IN (SELECT ROLE_KEY FROM SAKAI_REALM_ROLE WHERE ROLE_NAME = ?)";
			}
			for (RoleAndDescription rad : toDelete)
			{
				fields[1] = rad.role;
				m_sql.dbWrite(sql, fields);
			}			

			fields = new Object[4];
			fields[0] = caseId(azg.getId());

			// add what we need to
			sql = "INSERT INTO SAKAI_REALM_ROLE_DESC (REALM_KEY, ROLE_KEY, DESCRIPTION, PROVIDER_ONLY) VALUES ("
					+ " (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)," 
					+ " (SELECT ROLE_KEY FROM SAKAI_REALM_ROLE WHERE ROLE_NAME = ?), ?, ?)";
			for (RoleAndDescription rad : toAdd)
			{
				fields[1] = rad.role;
				fields[2] = rad.description;
				fields[3] = rad.providerOnly ? "1" : "0";
				m_sql.dbWrite(sql, fields);
			}
		}

		public void cancel(AuthzGroup edit)
		{
			super.cancelResource(edit);
		}

		public void remove(final AuthzGroup edit)
		{
			// in a transaction
			m_sql.transact(new Runnable()
			{
				public void run()
				{
					removeTx(edit);					
				}
			}, "azgRemove:" + edit.getId());
		}

		/**
		 * Transaction code for removing the azg.
		 */
		protected void removeTx(AuthzGroup edit)
		{
			// delete all the role functions, auth grants, anon grants, role grants, fucntion grants
			// and then the realm and release the lock.

			// delete the role functions, role grants, provider entries
			Object fields[] = new Object[1];
			fields[0] = caseId(edit.getId());

			String statement = null;
			
			if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "DELETE SAKAI_REALM_RL_FN FROM SAKAI_REALM_RL_FN INNER JOIN SAKAI_REALM ON SAKAI_REALM_RL_FN.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
			}
			else
			{
				statement = "DELETE FROM SAKAI_REALM_RL_FN WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)";
			}
			m_sql.dbWrite(statement, fields);

			if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "DELETE SAKAI_REALM_RL_GR FROM SAKAI_REALM_RL_GR INNER JOIN SAKAI_REALM ON SAKAI_REALM_RL_GR.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
			}
			else
			{
				statement = "DELETE FROM SAKAI_REALM_RL_GR WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)";
			}
			m_sql.dbWrite(statement, fields);

			if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "DELETE SAKAI_REALM_PROVIDER FROM SAKAI_REALM_PROVIDER INNER JOIN SAKAI_REALM ON SAKAI_REALM_PROVIDER.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
			}
			else
			{
				statement = "DELETE FROM SAKAI_REALM_PROVIDER WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)";
			}
			m_sql.dbWrite(statement, fields);

			if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "DELETE SAKAI_REALM_ROLE_DESC FROM SAKAI_REALM_ROLE_DESC INNER JOIN SAKAI_REALM ON SAKAI_REALM_ROLE_DESC.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?";
			}
			else
			{
				statement = "DELETE FROM SAKAI_REALM_ROLE_DESC WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)";
			}
			m_sql.dbWrite(statement, fields);

			// delete the realm and properties
			super.removeResource(edit, ((BaseAuthzGroup) edit).getKey());
		}

		/**
		 * Get the fields for the database from the edit for this id, and the id again at the end if needed
		 * 
		 * @param id
		 *        The resource id
		 * @param edit
		 *        The edit (may be null in a new)
		 * @param idAgain
		 *        If true, include the id field again at the end, else don't.
		 * @return The fields for the database.
		 */
		protected Object[] fields(String id, BaseAuthzGroup edit, boolean idAgain)
		{
			Object[] rv = new Object[idAgain ? 8 : 7];
			rv[0] = caseId(id);
			if (idAgain)
			{
				rv[7] = rv[0];
			}

			if (edit == null)
			{
				String current = sessionManager().getCurrentSessionUserId();

				// if no current user, since we are working up a new user record, use the user id as creator...
				if (current == null) current = "";

				Time now = timeService().newTime();

				rv[1] = "";
				rv[2] = "";
				rv[3] = current;
				rv[4] = current;
				rv[5] = now;
				rv[6] = now;
			}

			else
			{
				rv[1] = StringUtil.trimToZero(edit.m_providerRealmId);
				rv[2] = StringUtil.trimToZero(edit.m_maintainRole);
				rv[3] = StringUtil.trimToZero(edit.m_createdUserId);
				rv[4] = StringUtil.trimToZero(edit.m_lastModifiedUserId);
				rv[5] = edit.getCreatedTime();
				rv[6] = edit.getModifiedTime();
			}

			return rv;
		}

		/**
		 * Read from the result one set of fields to create a Resource.
		 * 
		 * @param result
		 *        The Sql query result.
		 * @return The Resource object.
		 */
		public Object readSqlResultRecord(ResultSet result)
		{
			//ONC-341
			if(result != null)
			{
				try
				{
					ResultSetMetaData meta = result.getMetaData();
					if(meta != null)
					{
						int colCount = meta.getColumnCount();
						if(colCount < 8)
						{
							M_log.error("Error in DbAuthzGroupService.readSqlResultRecord: total column less than 8!");
							return null;
						}
					}
				}
				catch(SQLException e)
				{
					M_log.error("Error in DbAuthzGroupService.readSqlResultRecord:" + e.getMessage(), e);
				}
			}
			try
			{
				String id = result.getString(1);
				String providerId = result.getString(2);
				String maintainRole = result.getString(3);
				String createdBy = result.getString(4);
				String modifiedBy = result.getString(5);
				java.sql.Timestamp ts = result.getTimestamp(6, sqlService().getCal());
				Time createdOn = null;
				if (ts != null)
				{
					createdOn = timeService().newTime(ts.getTime());
				}
				ts = result.getTimestamp(7, sqlService().getCal());
				Time modifiedOn = null;
				if (ts != null)
				{
					modifiedOn = timeService().newTime(ts.getTime());
				}

				// the special local integer 'db' id field, read after the field list
				Integer dbid = new Integer(result.getInt(8));

				// create the Resource from these fields
				return new BaseAuthzGroup(dbid, id, providerId, maintainRole, createdBy, createdOn, modifiedBy, modifiedOn);
			}
			catch (SQLException e)
			{
				//ONC-341
				M_log.warn("readSqlResultRecord: " + e, e);
				return null;
			}
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean isAllowed(String userId, String lock, String realmId)
		{
			if ((lock == null) || (realmId == null)) return false;

			// does the user have any roles granted that include this lock, based on grants or anon/auth?
			boolean auth = (userId != null) && (!userDirectoryService().getAnonymousUser().getId().equals(userId));

			String statement = "select count(1) "
					+ "from "
					+ "  SAKAI_REALM_RL_FN MAINTABLE "
					+ "     LEFT JOIN SAKAI_REALM_RL_GR GRANTED_ROLES "
					+ "        ON (MAINTABLE.REALM_KEY = GRANTED_ROLES.REALM_KEY AND MAINTABLE.ROLE_KEY = GRANTED_ROLES.ROLE_KEY), "
					+ "  SAKAI_REALM REALMS, " + "  SAKAI_REALM_ROLE ROLES, "
					+ "  SAKAI_REALM_FUNCTION FUNCTIONS "
					+ "where "
					+
					// our criteria
					"  ( " + "    ROLES.ROLE_NAME in('" + ANON_ROLE + "'" + (auth ? ",'" + AUTH_ROLE + "'" : "") + ") " + "    or "
					+ "    ( " + "      GRANTED_ROLES.USER_ID = ? " + "      AND GRANTED_ROLES.ACTIVE = 1 " + "    ) " + "  )"
					+ "  AND FUNCTIONS.FUNCTION_NAME = ? " + "  AND REALMS.REALM_ID in (?) "
					+
					// for the join
					"  AND MAINTABLE.REALM_KEY = REALMS.REALM_KEY " + "  AND MAINTABLE.FUNCTION_KEY = FUNCTIONS.FUNCTION_KEY "
					+ "  AND MAINTABLE.ROLE_KEY = ROLES.ROLE_KEY ";

			Object[] fields = new Object[3];
			fields[0] = userId;
			fields[1] = lock;
			fields[2] = realmId;

			List resultsNew = m_sql.dbRead(statement, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						int count = result.getInt(1);
						return new Integer(count);
					}
					catch (SQLException ignore)
					{
						return null;
					}
				}
			});

			boolean rvNew = false;
			int countNew = -1;
			if (!resultsNew.isEmpty())
			{
				countNew = ((Integer) resultsNew.get(0)).intValue();
				rvNew = countNew > 0;
			}

			return rvNew;
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean isAllowed(String userId, String lock, Collection realms)
		{
			if (lock == null) return false;

			boolean auth = (userId != null) && (!userDirectoryService().getAnonymousUser().getId().equals(userId));

			if (realms == null || realms.size() < 1)
			{
				M_log.warn("isAllowed(): called with no realms: lock: " + lock + " user: " + userId);
				if (M_log.isDebugEnabled())
				{
					try
					{
						throw new Exception();
					}
					catch (Exception e)
					{
						M_log.debug("isAllowed():", e);
					}
				}
				return false;
			}

			String inClause = orInClause(realms.size(), "SAKAI_REALM.REALM_ID");

			String statement = null;

			// any of the grant or role realms
			if ("mysql".equals(sqlService().getVendor()))
			{
				statement = "select count(1) from SAKAI_REALM_RL_FN,SAKAI_REALM force index "
						+ "(AK_SAKAI_REALM_ID) where SAKAI_REALM_RL_FN.REALM_KEY = SAKAI_REALM.REALM_KEY "
						+ "and " + inClause;
			}
			else
			// oracle and hsql
			{
				statement = "select count(1) from SAKAI_REALM_RL_FN "
						+ "where REALM_KEY in (select REALM_KEY from SAKAI_REALM where " + inClause + ")";
			}
			statement = statement
					+ " and FUNCTION_KEY in (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = ?) "
					+ "and (ROLE_KEY in "
					+ "(select ROLE_KEY from SAKAI_REALM_RL_GR where ACTIVE = '1' and USER_ID = ? "
					+
					// granted in any of the grant or role realms
					"and REALM_KEY in (select REALM_KEY from SAKAI_REALM where " + inClause + ")) "
					+ "or ROLE_KEY in (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = '" + ANON_ROLE + "') "
					+ (auth ? "or ROLE_KEY in (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = '" + AUTH_ROLE + "') " : "")
					+ ")";

			Object[] fields = new Object[2 + (2 * realms.size())];
			int pos = 0;
			for (Iterator i = realms.iterator(); i.hasNext();)
			{
				String role = (String) i.next();
				fields[pos++] = role;
			}
			fields[pos++] = lock;
			fields[pos++] = userId;
			for (Iterator i = realms.iterator(); i.hasNext();)
			{
				String role = (String) i.next();
				fields[pos++] = role;
			}

			List results = m_sql.dbRead(statement, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						int count = result.getInt(1);
						return new Integer(count);
					}
					catch (SQLException ignore)
					{
						return null;
					}
				}
			});

			boolean rv = false;
			int count = -1;
			if (!results.isEmpty())
			{
				count = ((Integer) results.get(0)).intValue();
				rv = count > 0;
			}

			return rv;

			// return rvNew;
		}

		/**
		 * {@inheritDoc}
		 */
		public Set getUsersIsAllowed(String lock, Collection realms)
		{
			if ((lock == null) || (realms == null) || (realms.isEmpty())) return new HashSet();

			String sql = "";
			StringBuffer sqlBuf = null;
			
			// Assemble SQL
			sqlBuf = new StringBuffer();
			sqlBuf.append("select SRRG.USER_ID ");
			sqlBuf.append("from SAKAI_REALM_RL_GR SRRG ");
			if ("mysql".equals(sqlService().getVendor()))
			{
				sqlBuf.append("inner join SAKAI_REALM SR force index (AK_SAKAI_REALM_ID) ON SRRG.REALM_KEY = SR.REALM_KEY ");
			}
			else
			// oracle and hsql
			{
				sqlBuf.append("inner join SAKAI_REALM SR ON SRRG.REALM_KEY = SR.REALM_KEY ");
			}
			sqlBuf.append("where " + orInClause(realms.size(), "SR.REALM_ID") + " ");
			sqlBuf.append("and SRRG.ACTIVE = '1' ");
			sqlBuf.append("and SRRG.ROLE_KEY in ");
			sqlBuf.append("(select SRRF.ROLE_KEY ");
			sqlBuf.append("from SAKAI_REALM_RL_FN SRRF ");
			sqlBuf.append("inner join SAKAI_REALM_FUNCTION SRF ON SRRF.FUNCTION_KEY = SRF.FUNCTION_KEY ");
			if ("mysql".equals(sqlService().getVendor()))
			{
				sqlBuf.append("inner join SAKAI_REALM SR1 force index (AK_SAKAI_REALM_ID) ON SRRF.REALM_KEY = SR1.REALM_KEY ");
			}
			else
			// oracle and hsql
			{
				sqlBuf.append("inner join SAKAI_REALM SR1 ON SRRF.REALM_KEY = SR1.REALM_KEY ");
			}
			sqlBuf.append("where SRF.FUNCTION_NAME = ? ");
			sqlBuf.append("and " + orInClause(realms.size(), "SR1.REALM_ID") + ")");
			sql = sqlBuf.toString();

			Object[] fields = new Object[1 + (2 * realms.size())];
			int pos = 0;
			for (Iterator i = realms.iterator(); i.hasNext();)
			{
				String roleRealm = (String) i.next();
				fields[pos++] = roleRealm;
			}
			fields[pos++] = lock;
			for (Iterator i = realms.iterator(); i.hasNext();)
			{
				String roleRealm = (String) i.next();
				fields[pos++] = roleRealm;
			}

			// read the strings
			List results = m_sql.dbRead(sql, fields, null);

			// prepare the return
			Set rv = new HashSet();
			rv.addAll(results);
			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public Set getAllowedFunctions(String role, Collection realms)
		{
			if ((role == null) || (realms == null) || (realms.isEmpty())) return new HashSet();

			String sql = "";
			StringBuffer sqlBuf = null;

			// Assemble SQL
			sqlBuf = new StringBuffer();
			sqlBuf.append("select DISTINCT FUNCTION_NAME ");
			sqlBuf.append("from SAKAI_REALM_FUNCTION SRF ");
			sqlBuf.append("inner join SAKAI_REALM_RL_FN SRRF on SRF.FUNCTION_KEY = SRRF.FUNCTION_KEY ");
			sqlBuf.append("inner join SAKAI_REALM_ROLE SRR on SRRF.ROLE_KEY = SRR.ROLE_KEY ");
			sqlBuf.append("inner join SAKAI_REALM SR on SRRF.REALM_KEY = SR.REALM_KEY ");
			sqlBuf.append("where SRR.ROLE_NAME = ? ");
			sqlBuf.append("and " + orInClause(realms.size(), "SR.REALM_ID"));
			sql = sqlBuf.toString();

			Object[] fields = new Object[1 + realms.size()];
			fields[0] = role;
			int pos = 1;
			for (Iterator i = realms.iterator(); i.hasNext();)
			{
				String roleRealm = (String) i.next();
				fields[pos++] = roleRealm;
			}

			// read the strings
			List results = m_sql.dbRead(sql, fields, null);

			// prepare the return
			Set rv = new HashSet();
			rv.addAll(results);
			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public void refreshUser(String userId, Map providerGrants)
		{
			if (userId == null) return;

			String sql = "";
			String sqlParam = "";
			StringBuffer sqlBuf = null;
			StringBuffer sqlParamBuf = null;

			// read this user's grants from all realms
			sqlBuf = new StringBuffer();
			sqlBuf.append("select SRRG.REALM_KEY, SRR.ROLE_NAME, SRRG.ACTIVE, SRRG.PROVIDED ");
			sqlBuf.append("from SAKAI_REALM_ROLE SRR ");
			sqlBuf.append("inner join SAKAI_REALM_RL_GR SRRG on SRR.ROLE_KEY = SRRG.ROLE_KEY ");
			sqlBuf.append("where SRRG.USER_ID = ?");
			sql = sqlBuf.toString();

			Object[] fields = new Object[1];
			fields[0] = userId;

			List grants = m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						int realmKey = result.getInt(1);
						String roleName = result.getString(2);
						String active = result.getString(3);
						String provided = result.getString(4);
						return new RealmAndRole(new Integer(realmKey), roleName, "1".equals(active), "1".equals(provided));
					}
					catch (Throwable ignore)
					{
						return null;
					}
				}
			});

			// make a map, realm id -> role granted, each for provider and non-provider (or inactive)
			Map<Integer, String> existing = new HashMap<Integer, String>();
			Map<Integer, String> nonProvider = new HashMap<Integer, String>();
			for (Iterator i = grants.iterator(); i.hasNext();)
			{
				RealmAndRole rar = (RealmAndRole) i.next();
				// active and provided are the currently stored provider grants
				if (rar.active && rar.provided)
				{
					if (existing.containsKey(rar.realmId))
					{
						M_log.warn("refreshUser: duplicate realm id found in provider grants: " + rar.realmId);
					}
					else
					{
						existing.put(rar.realmId, rar.role);
					}
				}

				// inactive or not provided are the currently stored internal grants - not to be overwritten by provider info
				else
				{
					if (nonProvider.containsKey(rar.realmId))
					{
						M_log.warn("refreshUser: duplicate realm id found in nonProvider grants: " + rar.realmId);
					}
					else
					{
						nonProvider.put(rar.realmId, rar.role);
					}
				}
			}

			// compute the user's realm roles based on the new provider information
			// same map form as existing, realm id -> role granted
			Map<Integer, String> target = new HashMap<Integer, String>();

			// for each realm that has a provider in the map, and does not have a grant for the user,
			// add the active provided grant with the map's role.

			if ((providerGrants != null) && (providerGrants.size() > 0))
			{
				// get all the realms that have providers in the map, with their full provider id

				// Assemble SQL. Note: distinct must be used because one cannot establish an equijoin between
				// SRP.PROVIDER_ID and SR.PROVIDER_ID as the values in SRP.PROVIDER_ID often include
				// additional concatenated course values. It may be worth reviewing this strategy.

				sqlBuf = new StringBuffer();
				sqlBuf.append("select distinct SRP.REALM_KEY, SR.PROVIDER_ID ");
				sqlBuf.append("from SAKAI_REALM_PROVIDER SRP ");
				sqlBuf.append("inner join SAKAI_REALM SR on SRP.REALM_KEY = SR.REALM_KEY ");
				sqlBuf.append("where " + orInClause(providerGrants.size(), "SRP.PROVIDER_ID"));
				sql = sqlBuf.toString();

				Object[] fieldsx = new Object[providerGrants.size()];
				int pos = 0;
				for (Iterator f = providerGrants.keySet().iterator(); f.hasNext();)
				{
					String providerId = (String) f.next();
					fieldsx[pos++] = providerId;
				}
				List realms = m_sql.dbRead(sql, fieldsx, new SqlReader()
				{
					public Object readSqlResultRecord(ResultSet result)
					{
						try
						{
							int id = result.getInt(1);
							String provider = result.getString(2);
							return new RealmAndProvider(new Integer(id), provider);
						}
						catch (Throwable ignore)
						{
							return null;
						}
					}
				});

				if ((realms != null) && (realms.size() > 0))
				{
					for (Iterator r = realms.iterator(); r.hasNext();)
					{
						RealmAndProvider rp = (RealmAndProvider) r.next();
						String role = (String) providerGrants.get(rp.providerId);
						if (role != null)
						{
							if (target.containsKey(rp.realmId))
							{
								M_log.warn("refreshUser: duplicate realm id computed for new grants: " + rp.realmId);
							}
							else
							{
								target.put(rp.realmId, role);
							}
						}
					}
				}
			}

			// compute the records we need to delete: every existing not in target or not matching target's role
			List<Integer> toDelete = new Vector<Integer>();
			for (Iterator i = existing.entrySet().iterator(); i.hasNext();)
			{
				Map.Entry entry = (Map.Entry) i.next();
				Integer realmId = (Integer) entry.getKey();
				String role = (String) entry.getValue();

				String targetRole = (String) target.get(realmId);
				if ((targetRole == null) || (!targetRole.equals(role)))
				{
					toDelete.add(realmId);
				}
			}

			// compute the records we need to add: every target not in existing, or not matching's existing's role
			// we don't insert target grants that would override internal grants
			List<RealmAndRole> toInsert = new Vector<RealmAndRole>();
			for (Iterator i = target.entrySet().iterator(); i.hasNext();)
			{
				Map.Entry entry = (Map.Entry) i.next();
				Integer realmId = (Integer) entry.getKey();
				String role = (String) entry.getValue();

				String existingRole = (String) existing.get(realmId);
				String nonProviderRole = (String) nonProvider.get(realmId);
				if ((nonProviderRole == null) && ((existingRole == null) || (!existingRole.equals(role))))
				{
					toInsert.add(new RealmAndRole(realmId, role, true, true));
				}
			}

			// if any, do it
			if ((toDelete.size() > 0) || (toInsert.size() > 0))
			{
				// do these each in their own transaction, to avoid possible deadlock
				// caused by transactions modifying more than one row at a time.

				// delete
				sql = "delete from SAKAI_REALM_RL_GR where REALM_KEY = ? and USER_ID = ?";
				fields = new Object[2];
				fields[1] = userId;
				for (Iterator i = toDelete.iterator(); i.hasNext();)
				{
					Integer realmId = (Integer) i.next();
					fields[0] = realmId;
					m_sql.dbWrite(sql, fields);
				}

				// insert
				sql = "insert into SAKAI_REALM_RL_GR (REALM_KEY, USER_ID, ROLE_KEY, ACTIVE, PROVIDED) "
						+ "values (?, ?, (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = ?), '1', '1')";
				fields = new Object[3];
				fields[1] = userId;
				for (Iterator i = toInsert.iterator(); i.hasNext();)
				{
					RealmAndRole rar = (RealmAndRole) i.next();
					fields[0] = rar.realmId;
					fields[2] = rar.role;

					m_sql.dbWrite(sql, fields);
				}
			}
		}

		/**
		 * {@inheritDoc}
		 */
		public void refreshAuthzGroup(BaseAuthzGroup realm)
		{
			if ((realm == null) || (m_provider == null)) return;

			String sql = "";
			StringBuffer sqlBuf = null;

			// Note: the realm is still lazy - we have the realm id but don't need to worry about changing grants

			// get the latest userEid -> role name map from the provider
			Map target = m_provider.getUserRolesForGroup(realm.getProviderGroupId());

			// read the realm's grants
			sqlBuf = new StringBuffer();
			sqlBuf.append("select SRRG.USER_ID, SRR.ROLE_NAME, SRRG.ACTIVE, SRRG.PROVIDED ");
			sqlBuf.append("from SAKAI_REALM_RL_GR SRRG ");
			sqlBuf.append("inner join SAKAI_REALM SR on SRRG. REALM_KEY = SR. REALM_KEY ");
			sqlBuf.append("inner join SAKAI_REALM_ROLE SRR on SRRG.ROLE_KEY = SRR.ROLE_KEY ");
			sqlBuf.append("where SR.REALM_ID = ?");
			sql = sqlBuf.toString();

			Object[] fields = new Object[1];
			fields[0] = caseId(realm.getId());

			List grants = m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						String userId = result.getString(1);
						String roleName = result.getString(2);
						String active = result.getString(3);
						String provided = result.getString(4);
						return new UserAndRole(userId, roleName, "1".equals(active), "1".equals(provided));
					}
					catch (Throwable ignore)
					{
						return null;
					}
				}
			});

			// make a map, user id -> role granted, each for provider and non-provider (or inactive)
			Map<String, String> existing = new HashMap<String, String>();
			Map<String, String> nonProvider = new HashMap<String, String>();
			for (Iterator i = grants.iterator(); i.hasNext();)
			{
				UserAndRole uar = (UserAndRole) i.next();

				// active and provided are the currently stored provider grants
				if (uar.active && uar.provided)
				{
					if (existing.containsKey(uar.userId))
					{
						M_log.warn("refreshRealm: duplicate user id found in provider grants: " + uar.userId);
					}
					else
					{
						existing.put(uar.userId, uar.role);
					}
				}

				// inactive or not provided are the currently stored internal grants - not to be overwritten by provider info
				else
				{
					if (nonProvider.containsKey(uar.userId))
					{
						M_log.warn("refreshRealm: duplicate user id found in nonProvider grants: " + uar.userId);
					}
					else
					{
						nonProvider.put(uar.userId, uar.role);
					}
				}
			}

			// compute the records we need to delete: every existing not in target or not matching target's role
			List<String> toDelete = new Vector<String>();
			for (Iterator i = existing.entrySet().iterator(); i.hasNext();)
			{
				Map.Entry entry = (Map.Entry) i.next();
				String userId = (String) entry.getKey();
				String role = (String) entry.getValue();

				try
				{
					String userEid = userDirectoryService().getUserEid(userId);
					String targetRole = (String) target.get(userEid);
					if ((targetRole == null) || (!targetRole.equals(role)))
					{
						toDelete.add(userId);
					}
				}
				catch (UserNotDefinedException e)
				{
					M_log.warn("refreshAuthzGroup: cannot find eid for user: " + userId);
				}
			}

			// compute the records we need to add: every target not in existing, or not matching's existing's role
			// we don't insert target grants that would override internal grants
			List<UserAndRole> toInsert = new Vector<UserAndRole>();
			for (Iterator i = target.entrySet().iterator(); i.hasNext();)
			{
				Map.Entry entry = (Map.Entry) i.next();
				String userEid = (String) entry.getKey();
				try
				{
					String userId = userDirectoryService().getUserId(userEid);

					String role = (String) entry.getValue();
	
					String existingRole = (String) existing.get(userId);
					String nonProviderRole = (String) nonProvider.get(userId);
					if ((nonProviderRole == null) && ((existingRole == null) || (!existingRole.equals(role))))
					{
						toInsert.add(new UserAndRole(userId, role, true, true));
					}
				}
				catch (UserNotDefinedException e)
				{
					M_log.warn("refreshAuthzGroup: cannot find id for user eid: " + userEid);
				}
			}

			// if any, do it
			if ((toDelete.size() > 0) || (toInsert.size() > 0))
			{
				// do these each in their own transaction, to avoid possible deadlock
				// caused by transactions modifying more than one row at a time.

				// delete
				if ("mysql".equals(sqlService().getVendor()))
				{
					sql = "DELETE SAKAI_REALM_RL_GR FROM SAKAI_REALM_RL_GR INNER JOIN SAKAI_REALM"
						+ " ON SAKAI_REALM_RL_GR.REALM_KEY = SAKAI_REALM.REALM_KEY AND SAKAI_REALM.REALM_ID = ?"
						+ " WHERE SAKAI_REALM_RL_GR.USER_ID = ?";
				}
				else
				{
					sql = "DELETE FROM SAKAI_REALM_RL_GR "
						+ " WHERE REALM_KEY IN (SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?)"
						+ " AND USER_ID = ?";
				}
				fields = new Object[2];
				fields[0] = caseId(realm.getId());
				for (Iterator i = toDelete.iterator(); i.hasNext();)
				{
					String userId = (String) i.next();
					fields[1] = userId;
					m_sql.dbWrite(sql, fields);
				}

				// insert
				sql = "insert into SAKAI_REALM_RL_GR (REALM_KEY, USER_ID, ROLE_KEY, ACTIVE, PROVIDED)"
						+ " values ((select REALM_KEY from SAKAI_REALM where REALM_ID = ?), ?, (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = ?), '1', '1')";
				fields = new Object[3];
				fields[0] = caseId(realm.getId());
				for (Iterator i = toInsert.iterator(); i.hasNext();)
				{
					UserAndRole uar = (UserAndRole) i.next();
					fields[1] = uar.userId;
					fields[2] = uar.role;

					m_sql.dbWrite(sql, fields);
				}
			}
		}

		public class RealmAndProvider
		{
			public Integer realmId;

			public String providerId;

			public RealmAndProvider(Integer id, String provider)
			{
				this.realmId = id;
				this.providerId = provider;
			}
		}

		public class RealmAndRole
		{
			public Integer realmId;

			public String role;

			boolean active;

			boolean provided;

			public RealmAndRole(Integer id, String role, boolean active, boolean provided)
			{
				this.realmId = id;
				this.role = role;
				this.active = active;
				this.provided = provided;
			}

			public boolean equals(Object obj)
			{
				if (!(obj instanceof RealmAndRole)) return false;
				if (this == obj) return true;
				RealmAndRole other = (RealmAndRole) obj;
				if (StringUtil.different(this.role, other.role)) return false;
				if (this.provided != other.provided) return false;
				if (this.active != other.active) return false;
				if (((this.realmId == null) && (other.realmId != null))
						|| ((this.realmId != null) && (other.realmId == null))
						|| ((this.realmId != null) && (other.realmId != null) && (!this.realmId.equals(other.realmId)))) return false;
				return true;
			}
			
			public int hashCode()
			{
				return (this.role + Boolean.valueOf(this.provided).toString() + Boolean.valueOf(this.active).toString() + this.realmId).hashCode();
			}
		}

		public class UserAndRole
		{
			public String userId;

			public String role;

			boolean active;

			boolean provided;

			public UserAndRole(String userId, String role, boolean active, boolean provided)
			{
				this.userId = userId;
				this.role = role;
				this.active = active;
				this.provided = provided;
			}

			public boolean equals(Object obj)
			{
				if (!(obj instanceof UserAndRole)) return false;
				if (this == obj) return true;
				UserAndRole other = (UserAndRole) obj;
				if (StringUtil.different(this.role, other.role)) return false;
				if (this.provided != other.provided) return false;
				if (this.active != other.active) return false;
				if (StringUtil.different(this.userId, other.userId)) return false;
				return true;
			}
			
			public int hashCode()
			{
				return (this.role + Boolean.valueOf(this.provided).toString() + Boolean.valueOf(this.active).toString() + this.userId).hashCode();
			}
		}
		
		public class RoleAndFunction
		{
			public String role;

			public String function;

			public RoleAndFunction(String role, String function)
			{
				this.role = role;
				this.function = function;
			}

			public boolean equals(Object obj)
			{
				if (!(obj instanceof RoleAndFunction)) return false;
				if (this == obj) return true;
				RoleAndFunction other = (RoleAndFunction) obj;
				if (StringUtil.different(this.role, other.role)) return false;
				if (StringUtil.different(this.function, other.function)) return false;
				return true;
			}
			
			public int hashCode()
			{
				return (this.role + this.function).hashCode();
			}
		}

		public class RoleAndDescription
		{
			public String role;

			public String description;
			
			public boolean providerOnly;

			public RoleAndDescription(String role, String description, boolean providerOnly)
			{
				this.role = role;
				this.description = description;
				this.providerOnly = providerOnly;
			}

			public boolean equals(Object obj)
			{
				if (!(obj instanceof RoleAndDescription)) return false;
				if (this == obj) return true;
				RoleAndDescription other = (RoleAndDescription) obj;
				if (StringUtil.different(this.role, other.role)) return false;
				if (StringUtil.different(this.description, other.description)) return false;
				if (this.providerOnly != other.providerOnly) return false;
				return true;
			}
			
			public int hashCode()
			{
				return (this.role + this.description + Boolean.valueOf(this.providerOnly).toString()).hashCode();
			}
		}

		/**
		 * {@inheritDoc}
		 */
		public String getUserRole(String userId, String azGroupId)
		{
			if ((userId == null) || (azGroupId == null)) return null;

			String sql = "select SRR.ROLE_NAME from SAKAI_REALM_RL_GR SRRG "
					+ "inner join SAKAI_REALM SR on SRRG.REALM_KEY = SR.REALM_KEY "
					+ "inner join SAKAI_REALM_ROLE SRR on SRRG.ROLE_KEY = SRR.ROLE_KEY "
					+ "where SR.REALM_ID = ? and SRRG.USER_ID = ? and SRRG.ACTIVE = '1'";

			Object[] fields = new Object[2];
			fields[0] = azGroupId;
			fields[1] = userId;

			// read the string
			List results = m_sql.dbRead(sql, fields, null);

			// prepare the return
			String rv = null;
			if ((results != null) && (!results.isEmpty()))
			{
				rv = (String) results.get(0);
				if (results.size() > 1)
				{
					M_log.warn("getUserRole: user: " + userId + " multiple roles");
				}
			}

			return rv;
		}

		/**
		 * {@inheritDoc}
		 */
		public Map getUsersRole(Collection userIds, String azGroupId)
		{
			if ((userIds == null) || (userIds.isEmpty()) || (azGroupId == null))
			{
				return new HashMap();
			}

			String inClause = orInClause(userIds.size(), "SRRG.USER_ID");

			String sql = "select SRRG.USER_ID, SRR.ROLE_NAME from SAKAI_REALM_RL_GR SRRG "
					+ "inner join SAKAI_REALM SR on SRRG.REALM_KEY = SR.REALM_KEY "
					+ "inner join SAKAI_REALM_ROLE SRR on SRRG.ROLE_KEY = SRR.ROLE_KEY " + "where SR.REALM_ID = ? and " + inClause
					+ " and SRRG.ACTIVE = '1'";

			Object[] fields = new Object[1 + userIds.size()];
			fields[0] = azGroupId;
			int pos = 1;
			for (Iterator i = userIds.iterator(); i.hasNext();)
			{
				fields[pos++] = i.next();
			}

			// the return
			final Map rv = new HashMap();

			// read
			m_sql.dbRead(sql, fields, new SqlReader()
			{
				public Object readSqlResultRecord(ResultSet result)
				{
					try
					{
						// read the results
						String userId = result.getString(1);
						String role = result.getString(2);

						if ((userId != null) && (role != null))
						{
							rv.put(userId, role);
						}
					}
					catch (Throwable t)
					{
					}

					return null;
				}
			});

			return rv;
		}

	} // DbStorage

	/** To avoide the dreaded ORA-01795 and the like, we need to limit to <100 the items in each in(?, ?, ...) clause, connecting them with ORs. */
	protected final static int MAX_IN_CLAUSE = 99;

	/**
	 * Form a SQL IN() clause, but break it up with ORs to keep the size of each IN below 100
	 * 
	 * @param size
	 *        The size
	 * @param field
	 *        The field name
	 * @return a SQL IN() with ORs clause this large.
	 */
	protected String orInClause(int size, String field)
	{
		// Note: to avoide the dreaded ORA-01795 and the like, we need to limit to <100 the items in each in(?, ?, ...) clause, connecting them with ORs -ggolden
		int ors = size / MAX_IN_CLAUSE;
		int leftover = size - (ors * MAX_IN_CLAUSE);
		StringBuffer buf = new StringBuffer();

		// enclose them all in parens if we have > 1
		if (ors > 0)
		{
			buf.append(" (");
		}

		buf.append(" " + field + " IN ");
		
		// do all the full MAX_IN_CLAUSE '?' in/ors
		if (ors > 0)
		{
			for (int i = 0; i < ors; i++)
			{
				buf.append("(?");
				for (int j = 1; j < MAX_IN_CLAUSE; j++)
				{
					buf.append(",?");
				}
				buf.append(")");

				if (i < ors - 1)
				{
					buf.append(" OR " + field + " IN ");
				}
			}
		}

		// add one more for the extra
		if (leftover > 0)
		{
			if (ors > 0)
			{
				buf.append(" OR " + field + " IN ");
			}
			buf.append("(?");
			for (int i = 1; i < leftover; i++)
			{
				buf.append(",?");
			}
			buf.append(")");
		}

		// enclose them all in parens if we have > 1
		if (ors > 0)
		{
			buf.append(" )");
		}

		return buf.toString();
	}
}
