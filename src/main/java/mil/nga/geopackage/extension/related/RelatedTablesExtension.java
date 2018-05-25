package mil.nga.geopackage.extension.related;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.db.CoreSQLUtils;
import mil.nga.geopackage.db.GeoPackageConnection;
import mil.nga.geopackage.user.UserCoreTableReader;
import mil.nga.geopackage.user.custom.UserCustomDao;
import mil.nga.geopackage.user.custom.UserCustomResultSet;

/**
 * Related Tables extension
 * 
 * @author jyutzler
 * @since 3.0.1
 */
public class RelatedTablesExtension extends RelatedTablesCoreExtension {

	/**
	 * GeoPackage connection
	 */
	private GeoPackageConnection connection;

	/**
	 * Constructor
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * 
	 */
	public RelatedTablesExtension(GeoPackage geoPackage) {
		super(geoPackage);
		connection = geoPackage.getConnection();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getPrimaryKeyColumnName(String tableName) {
		String result = null;
		String sql = "PRAGMA table_info(" + CoreSQLUtils.quoteWrap(tableName)
				+ ")";
		ResultSet resultSet = connection.query(sql, null);
		try {
			while (resultSet.next()) {
				if (resultSet.getInt(UserCoreTableReader.PK) == 1) {
					result = resultSet.getString(UserCoreTableReader.NAME);
					break;
				}
			}
		} catch (SQLException e) {
			throw new GeoPackageException("Failed to query for the "
					+ " primary key for table " + tableName, e);
		} finally {
			try {
				resultSet.close();
			} catch (SQLException e) {
				throw new GeoPackageException("Failed to close ResultSet", e);
			}
		}
		if (result == null) {
			throw new GeoPackageException("Found no primary key for table "
					+ tableName);
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dropMappingTable(ExtendedRelation extendedRelation) {
		UserMappingDao userMappingDao = getUserMappingDao(extendedRelation);
		if (userMappingDao != null) {
			userMappingDao.dropTable();
		}
	}

	/**
	 * Get a User Mapping DAO from an extended relation
	 * 
	 * @param extendedRelation
	 *            extended relation
	 * @return user mapping dao
	 */
	public UserMappingDao getUserMappingDao(ExtendedRelation extendedRelation) {
		return getUserMappingDao(extendedRelation.getMappingTableName());
	}

	/**
	 * Get a User Mapping DAO from a table name
	 * 
	 * @param tableName
	 *            mapping table name
	 * @return user mapping dao
	 */
	public UserMappingDao getUserMappingDao(String tableName) {

		if (tableName == null) {
			throw new GeoPackageException(
					"Non null table name is required to create "
							+ UserMappingDao.class.getSimpleName());
		}

		// Read the existing table and create the dao
		UserCustomDao userDao = UserCustomDao.readTable(getGeoPackage()
				.getName(), connection, tableName);
		UserMappingDao dao = new UserMappingDao(userDao);

		return dao;
	}

	/**
	 * Get the related id mappings for the base id
	 * 
	 * @param extendedRelation
	 *            extended relation
	 * @param baseId
	 *            base id
	 * @return IDs representing the matching related IDs
	 */
	public List<Long> getMappingsForBase(ExtendedRelation extendedRelation,
			long baseId) {
		return getMappingsForBase(extendedRelation.getMappingTableName(),
				baseId);
	}

	/**
	 * Get the related id mappings for the base id
	 * 
	 * @param tableName
	 *            mapping table name
	 * @param baseId
	 *            base id
	 * @return IDs representing the matching related IDs
	 */
	public List<Long> getMappingsForBase(String tableName, long baseId) {

		List<Long> relatedIds = new ArrayList<>();

		UserMappingDao userMappingDao = getUserMappingDao(tableName);
		UserCustomResultSet resultSet = userMappingDao.queryForEq(
				UserMappingTable.COLUMN_BASE_ID, baseId);
		try {
			while (resultSet.moveToNext()) {
				UserMappingRow row = userMappingDao.getRow(resultSet);
				relatedIds.add(row.getRelatedId());
			}
		} finally {
			resultSet.close();
		}

		return relatedIds;
	}

	/**
	 * Get the base id mappings for the related id
	 * 
	 * @param extendedRelation
	 *            extended relation
	 * @param relatedId
	 *            related id
	 * @return IDs representing the matching base IDs
	 */
	public List<Long> getMappingsForRelated(ExtendedRelation extendedRelation,
			long relatedId) {
		return getMappingsForRelated(extendedRelation.getMappingTableName(),
				relatedId);
	}

	/**
	 * Get the base id mappings for the related id
	 * 
	 * @param tableName
	 *            mapping table name
	 * @param relatedId
	 *            related id
	 * @return IDs representing the matching base IDs
	 */
	public List<Long> getMappingsForRelated(String tableName, long relatedId) {

		List<Long> baseIds = new ArrayList<>();

		UserMappingDao userMappingDao = getUserMappingDao(tableName);
		UserCustomResultSet resultSet = userMappingDao.queryForEq(
				UserMappingTable.COLUMN_RELATED_ID, relatedId);
		try {
			while (resultSet.moveToNext()) {
				UserMappingRow row = userMappingDao.getRow(resultSet);
				baseIds.add(row.getBaseId());
			}
		} finally {
			resultSet.close();
		}

		return baseIds;
	}

}