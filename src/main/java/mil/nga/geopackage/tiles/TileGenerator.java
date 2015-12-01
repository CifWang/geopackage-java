package mil.nga.geopackage.tiles;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.core.contents.Contents;
import mil.nga.geopackage.core.contents.ContentsDao;
import mil.nga.geopackage.core.srs.SpatialReferenceSystemDao;
import mil.nga.geopackage.io.GeoPackageProgress;
import mil.nga.geopackage.projection.Projection;
import mil.nga.geopackage.projection.ProjectionConstants;
import mil.nga.geopackage.projection.ProjectionFactory;
import mil.nga.geopackage.projection.ProjectionTransform;
import mil.nga.geopackage.tiles.matrix.TileMatrix;
import mil.nga.geopackage.tiles.matrix.TileMatrixDao;
import mil.nga.geopackage.tiles.matrix.TileMatrixKey;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSet;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSetDao;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.tiles.user.TileResultSet;
import mil.nga.geopackage.tiles.user.TileRow;
import mil.nga.geopackage.tiles.user.TileTable;

/**
 * Creates a set of tiles within a GeoPackage
 *
 * @author osbornb
 * @since 1.1.2
 */
public abstract class TileGenerator {

	/**
	 * Logger
	 */
	private static final Logger LOGGER = Logger.getLogger(TileGenerator.class
			.getName());

	/**
	 * Projection transformation from WGS84 to Web Mercator
	 */
	private static final ProjectionTransform wgs84ToWebMercatorTransform = ProjectionFactory
			.getProjection(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM)
			.getTransformation(ProjectionConstants.EPSG_WEB_MERCATOR);

	/**
	 * Projection transformation from Web Mercator to WGS84
	 */
	private static final ProjectionTransform webMercatorToWgs84Transform = ProjectionFactory
			.getProjection(ProjectionConstants.EPSG_WEB_MERCATOR)
			.getTransformation(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

	/**
	 * GeoPackage
	 */
	private final GeoPackage geoPackage;

	/**
	 * Table Name
	 */
	private final String tableName;

	/**
	 * Min zoom level
	 */
	private final int minZoom;

	/**
	 * Max zoom level
	 */
	private final int maxZoom;

	/**
	 * Total tile count
	 */
	private Integer tileCount;

	/**
	 * Tile grids by zoom level
	 */
	private final Map<Integer, TileGrid> tileGrids = new HashMap<Integer, TileGrid>();

	/**
	 * Tile bounding box
	 */
	protected BoundingBox boundingBox = new BoundingBox(-180.0, 180.0,
			ProjectionConstants.WEB_MERCATOR_MIN_LAT_RANGE,
			ProjectionConstants.WEB_MERCATOR_MAX_LAT_RANGE);

	/**
	 * Tile matrix set bounding box
	 */
	private BoundingBox tileMatrixSetBoundingBox;

	/**
	 * Compress format
	 */
	private String compressFormat;

	/**
	 * Compress quality
	 */
	private Float compressQuality = null;

	/**
	 * GeoPackage progress
	 */
	private GeoPackageProgress progress;

	/**
	 * True when generating tiles in Google tile format, false when generating
	 * GeoPackage format where rows and columns do not match the Google row &
	 * column coordinates
	 */
	private boolean googleTiles = false;

	/**
	 * Web mercator bounding box
	 */
	private BoundingBox webMercatorBoundingBox;

	/**
	 * Matrix height when GeoPackage tile format
	 */
	private long matrixHeight = 0;

	/**
	 * Matrix width when GeoPackage tile format
	 */
	private long matrixWidth = 0;

	/**
	 * Constructor
	 *
	 * @param geoPackage
	 * @param tableName
	 * @param minZoom
	 * @param maxZoom
	 */
	public TileGenerator(GeoPackage geoPackage, String tableName, int minZoom,
			int maxZoom) {
		geoPackage.verifyWritable();
		this.geoPackage = geoPackage;
		this.tableName = tableName;

		this.minZoom = minZoom;
		this.maxZoom = maxZoom;

	}

	/**
	 * Get the GeoPackage
	 * 
	 * @return GeoPackage
	 */
	public GeoPackage getGeoPackage() {
		return geoPackage;
	}

	/**
	 * Get the table name
	 * 
	 * @return table name
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * Get the min zoom
	 * 
	 * @return min zoom
	 */
	public int getMinZoom() {
		return minZoom;
	}

	/**
	 * Get the max zoom
	 * 
	 * @return
	 */
	public int getMaxZoom() {
		return maxZoom;
	}

	/**
	 * Set the tile bounding box as WGS84
	 *
	 * @param boundingBox
	 *            bounding box in WGS84
	 */
	public void setTileBoundingBox(BoundingBox boundingBox) {
		this.boundingBox = boundingBox;
		this.boundingBox.setMinLatitude(Math.max(boundingBox.getMinLatitude(),
				ProjectionConstants.WEB_MERCATOR_MIN_LAT_RANGE));
		this.boundingBox.setMaxLatitude(Math.min(boundingBox.getMaxLatitude(),
				ProjectionConstants.WEB_MERCATOR_MAX_LAT_RANGE));
	}

	/**
	 * Get the tile bounding box in WGS84 projection
	 * 
	 * @return WGS84 bounding box
	 */
	public BoundingBox getTileBoundingBox() {
		return boundingBox;
	}

	/**
	 * Set the tile bounding box specified in the provided projection
	 * 
	 * @param boundingBox
	 * @param projection
	 */
	public void setTileBoundingBox(BoundingBox boundingBox,
			Projection projection) {
		ProjectionTransform transform = projection
				.getTransformation(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);
		setTileBoundingBox(transform.transform(boundingBox));
	}

	/**
	 * Get the tile bounding box in specified projection
	 * 
	 * @param projection
	 *            requested projection
	 * @return bounding box
	 */
	public BoundingBox getTileBoundingBox(Projection projection) {
		ProjectionTransform transform = ProjectionFactory.getProjection(
				ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM)
				.getTransformation(projection);
		return transform.transform(boundingBox);
	}

	/**
	 * Set the compress format
	 *
	 * @param compressFormat
	 */
	public void setCompressFormat(String compressFormat) {
		this.compressFormat = compressFormat;
	}

	/**
	 * Get the compress format
	 * 
	 * @return compress format
	 */
	public String getCompressFormat() {
		return compressFormat;
	}

	/**
	 * Set the compress quality (0.0 to 1.0). The Compress format must be set
	 * for this to be used.
	 *
	 * @param compressQuality
	 */
	public void setCompressQuality(Float compressQuality) {
		if (compressQuality != null
				&& (compressQuality < 0.0 || compressQuality > 1.0)) {
			throw new GeoPackageException(
					"Compress quality must be between 0.0 and 1.0, not: "
							+ compressQuality);
		}
		this.compressQuality = compressQuality;
	}

	/**
	 * Get the compress quality
	 * 
	 * @return compress quality or null
	 */
	public Float getCompressQuality() {
		return compressQuality;
	}

	/**
	 * Set the progress tracker
	 *
	 * @param progress
	 */
	public void setProgress(GeoPackageProgress progress) {
		this.progress = progress;
	}

	/**
	 * Get the progress tracker
	 * 
	 * @return progress
	 */
	public GeoPackageProgress getProgress() {
		return progress;
	}

	/**
	 * Set the Google Tiles flag to true to generate Google tile format tiles.
	 * Default is false
	 *
	 * @param googleTiles
	 */
	public void setGoogleTiles(boolean googleTiles) {
		this.googleTiles = googleTiles;
	}

	/**
	 * Is the Google Tiles flag set to generate Google tile format tiles.
	 * 
	 * @return true if Google Tiles format, false if GeoPackage
	 */
	public boolean isGoogleTiles() {
		return googleTiles;
	}

	/**
	 * Get the tile count of tiles to be generated
	 *
	 * @return
	 */
	public int getTileCount() {
		if (tileCount == null) {
			int count = 0;
			BoundingBox requestWebMercatorBoundingBox = TileBoundingBoxUtils
					.toWebMercator(boundingBox);
			for (int zoom = minZoom; zoom <= maxZoom; zoom++) {
				// Get the tile grid that includes the entire bounding box
				TileGrid tileGrid = TileBoundingBoxUtils.getTileGrid(
						requestWebMercatorBoundingBox, zoom);
				count += tileGrid.count();
				tileGrids.put(zoom, tileGrid);
			}

			tileCount = count;
		}
		return tileCount;
	}

	/**
	 * Generate the tiles
	 *
	 * @return tiles created
	 * @throws java.sql.SQLException
	 * @throws java.io.IOException
	 */
	public int generateTiles() throws SQLException, IOException {

		int totalCount = getTileCount();

		// Set the max progress count
		if (progress != null) {
			progress.setMax(totalCount);
		}

		int count = 0;
		boolean update = false;

		// Get the web mercator projection of the requested bounding box
		BoundingBox requestWebMercatorBoundingBox = TileBoundingBoxUtils
				.toWebMercator(boundingBox);

		// Adjust the tile matrix set and web mercator bounds
		adjustBounds(requestWebMercatorBoundingBox, minZoom);

		// Create a new tile matrix or update an existing
		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();
		TileMatrixSet tileMatrixSet = null;
		if (!tileMatrixSetDao.isTableExists()
				|| !tileMatrixSetDao.idExists(tableName)) {
			// Create the web mercator srs if needed
			SpatialReferenceSystemDao srsDao = geoPackage
					.getSpatialReferenceSystemDao();
			srsDao.getOrCreate(ProjectionConstants.EPSG_WEB_MERCATOR);
			// Create the tile table
			tileMatrixSet = geoPackage.createTileTableWithMetadata(tableName,
					boundingBox,
					ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM,
					webMercatorBoundingBox,
					ProjectionConstants.EPSG_WEB_MERCATOR);
		} else {
			update = true;
			// Query to get the Tile Matrix Set
			tileMatrixSet = tileMatrixSetDao.queryForId(tableName);

			// Update the tile bounds between the existing and this request
			updateTileBounds(tileMatrixSet);
		}

		// Download and create the tiles
		try {
			Contents contents = tileMatrixSet.getContents();
			TileMatrixDao tileMatrixDao = geoPackage.getTileMatrixDao();
			TileDao tileDao = geoPackage.getTileDao(tileMatrixSet);

			// Create the new matrix tiles
			for (int zoom = minZoom; zoom <= maxZoom
					&& (progress == null || progress.isActive()); zoom++) {

				TileGrid localTileGrid = null;

				// Determine the matrix width and height for Google format
				if (googleTiles) {
					matrixWidth = TileBoundingBoxUtils.tilesPerSide(zoom);
					matrixHeight = matrixWidth;
				}
				// Get the local tile grid for GeoPackage format of where the
				// tiles belong
				else {
					localTileGrid = TileBoundingBoxUtils.getTileGrid(
							webMercatorBoundingBox, matrixWidth, matrixHeight,
							requestWebMercatorBoundingBox);
				}

				// Generate the tiles for the zoom level
				TileGrid tileGrid = tileGrids.get(zoom);
				count += generateTiles(tileMatrixDao, tileDao, contents, zoom,
						tileGrid, localTileGrid, matrixWidth, matrixHeight,
						update);

				if (!googleTiles) {
					// Double the matrix width and height for the next level
					matrixWidth *= 2;
					matrixHeight *= 2;
				}
			}

			// Delete the table if cancelled
			if (progress != null && !progress.isActive()
					&& progress.cleanupOnCancel()) {
				geoPackage.deleteTableQuietly(tableName);
				count = 0;
			} else {
				// Update the contents last modified date
				contents.setLastChange(new Date());
				ContentsDao contentsDao = geoPackage.getContentsDao();
				contentsDao.update(contents);
			}
		} catch (RuntimeException e) {
			geoPackage.deleteTableQuietly(tableName);
			throw e;
		} catch (SQLException e) {
			geoPackage.deleteTableQuietly(tableName);
			throw e;
		} catch (IOException e) {
			geoPackage.deleteTableQuietly(tableName);
			throw e;
		}

		return count;
	}

	/**
	 * Adjust the tile matrix set and web mercator bounds
	 *
	 * @param requestWebMercatorBoundingBox
	 * @param zoom
	 */
	private void adjustBounds(BoundingBox requestWebMercatorBoundingBox,
			int zoom) {
		// Google Tile Format
		if (googleTiles) {
			adjustGoogleBounds();
		}
		// GeoPackage Tile Format
		else {
			adjustGeoPackageBounds(requestWebMercatorBoundingBox, zoom);
		}
	}

	/**
	 * Adjust the tile matrix set and web mercator bounds for Google tile format
	 */
	private void adjustGoogleBounds() {
		// Set the tile matrix set bounding box to be the world
		tileMatrixSetBoundingBox = new BoundingBox(-180.0, 180.0,
				ProjectionConstants.WEB_MERCATOR_MIN_LAT_RANGE,
				ProjectionConstants.WEB_MERCATOR_MAX_LAT_RANGE);
		webMercatorBoundingBox = wgs84ToWebMercatorTransform
				.transform(tileMatrixSetBoundingBox);
	}

	/**
	 * Adjust the tile matrix set and web mercator bounds for GeoPackage format.
	 * Determine the tile grid width and height
	 *
	 * @param requestWebMercatorBoundingBox
	 * @param zoom
	 */
	private void adjustGeoPackageBounds(
			BoundingBox requestWebMercatorBoundingBox, int zoom) {
		// Get the fitting tile grid and determine the bounding box that
		// fits it
		TileGrid tileGrid = TileBoundingBoxUtils.getTileGrid(
				requestWebMercatorBoundingBox, zoom);
		webMercatorBoundingBox = TileBoundingBoxUtils
				.getWebMercatorBoundingBox(tileGrid, zoom);
		tileMatrixSetBoundingBox = webMercatorToWgs84Transform
				.transform(webMercatorBoundingBox);
		matrixWidth = tileGrid.getMaxX() + 1 - tileGrid.getMinX();
		matrixHeight = tileGrid.getMaxY() + 1 - tileGrid.getMinY();
	}

	/**
	 * Update the Content and Tile Matrix Set bounds
	 *
	 * @param tileMatrixSet
	 * @throws java.sql.SQLException
	 */
	private void updateTileBounds(TileMatrixSet tileMatrixSet)
			throws SQLException {

		TileDao tileDao = geoPackage.getTileDao(tileMatrixSet);

		if (tileDao.isGoogleTiles()) {
			if (!googleTiles) {
				// If adding GeoPackage tiles to a Google Tile format, add them
				// as Google tiles
				googleTiles = true;
				adjustGoogleBounds();
			}
		} else if (googleTiles) {
			// Can't add Google formatted tiles to GeoPackage tiles
			throw new GeoPackageException(
					"Can not add Google formatted tiles to "
							+ tableName
							+ " which already contains GeoPackage formatted tiles");
		}

		Contents contents = tileMatrixSet.getContents();

		ProjectionTransform transformContentsToWgs84 = ProjectionFactory
				.getProjection(contents.getSrs().getOrganizationCoordsysId())
				.getTransformation(
						ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

		// Combine the existing content and request bounding boxes
		BoundingBox contentsBoundingBox = transformContentsToWgs84
				.transform(contents.getBoundingBox());
		boundingBox = TileBoundingBoxUtils.union(contentsBoundingBox,
				boundingBox);

		// Update the contents if modified
		if (!contentsBoundingBox.equals(boundingBox)) {
			ProjectionTransform transformContentsToProjection = ProjectionFactory
					.getProjection(
							ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM)
					.getTransformation(
							contents.getSrs().getOrganizationCoordsysId());
			contents.setBoundingBox(transformContentsToProjection
					.transform(boundingBox));
			ContentsDao contentsDao = geoPackage.getContentsDao();
			contentsDao.update(contents);
		}

		// If updating GeoPackage format tiles, all existing metadata and tile
		// rows needs to be adjusted
		if (!googleTiles) {

			ProjectionTransform transformTileMatrixSetToWgs84 = ProjectionFactory
					.getProjection(
							tileMatrixSet.getSrs().getOrganizationCoordsysId())
					.getTransformation(
							ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);
			BoundingBox previousTileMatrixSetBoundingBox = transformTileMatrixSetToWgs84
					.transform(tileMatrixSet.getBoundingBox());

			// Adjust the bounds to include the request and existing bounds
			BoundingBox totalBoundingBox = TileBoundingBoxUtils
					.toWebMercator(boundingBox);
			int minNewOrUpdateZoom = Math.min(minZoom,
					(int) tileDao.getMinZoom());
			adjustGeoPackageBounds(totalBoundingBox, minNewOrUpdateZoom);

			// Update the tile matrix set if modified
			if (!previousTileMatrixSetBoundingBox
					.equals(tileMatrixSetBoundingBox)) {
				ProjectionTransform transformTileMatrixSetToProjection = ProjectionFactory
						.getProjection(
								ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM)
						.getTransformation(
								tileMatrixSet.getSrs()
										.getOrganizationCoordsysId());
				tileMatrixSet.setBoundingBox(transformTileMatrixSetToProjection
						.transform(tileMatrixSetBoundingBox));
				TileMatrixSetDao tileMatrixSetDao = geoPackage
						.getTileMatrixSetDao();
				tileMatrixSetDao.update(tileMatrixSet);
			}

			// Get the previous bounding box and new bounding box in web
			// mercator
			ProjectionTransform transformWgs84ToWebMercator = ProjectionFactory
					.getProjection(
							ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM)
					.getTransformation(ProjectionConstants.EPSG_WEB_MERCATOR);
			BoundingBox previousTileMatrixSetWebMercatorBoundingBox = transformWgs84ToWebMercator
					.transform(previousTileMatrixSetBoundingBox);
			BoundingBox tileMatrixSetWebMercatorBoundingBox = transformWgs84ToWebMercator
					.transform(tileMatrixSetBoundingBox);

			TileMatrixDao tileMatrixDao = geoPackage.getTileMatrixDao();

			// Adjust the tile matrix metadata and tile rows at each existing
			// zoom level
			for (long zoom = tileDao.getMinZoom(); zoom <= tileDao.getMaxZoom(); zoom++) {
				TileMatrix tileMatrix = tileDao.getTileMatrix(zoom);
				if (tileMatrix != null) {

					// Determine the new width and height at this level
					long adjustment = (long) Math.pow(2, zoom
							- minNewOrUpdateZoom);
					long zoomMatrixWidth = matrixWidth * adjustment;
					long zoomMatrixHeight = matrixHeight * adjustment;

					// Get the zoom level tile rows, starting with highest rows
					// and columns so when updating we avoid constraint
					// violations
					TileResultSet tileResultSet = tileDao
							.queryForTileDescending(zoom);
					try {
						// Update each tile row at this zoom level
						while (tileResultSet.moveToNext()) {
							TileRow tileRow = tileResultSet.getRow();

							// Get the bounding box of the existing tile
							BoundingBox tileBoundingBox = TileBoundingBoxUtils
									.getWebMercatorBoundingBox(
											previousTileMatrixSetWebMercatorBoundingBox,
											tileMatrix,
											tileRow.getTileColumn(),
											tileRow.getTileRow());

							// Get the mid lat and lon to find the new tile row
							// and column
							double midLatitude = tileBoundingBox
									.getMinLatitude()
									+ ((tileBoundingBox.getMaxLatitude() - tileBoundingBox
											.getMinLatitude()) / 2.0);
							double midLongitude = tileBoundingBox
									.getMinLongitude()
									+ ((tileBoundingBox.getMaxLongitude() - tileBoundingBox
											.getMinLongitude()) / 2.0);

							// Get the new tile row and column with regards to
							// the new bounding box
							long newTileRow = TileBoundingBoxUtils.getTileRow(
									tileMatrixSetWebMercatorBoundingBox,
									zoomMatrixHeight, midLatitude);
							long newTileColumn = TileBoundingBoxUtils
									.getTileColumn(
											tileMatrixSetWebMercatorBoundingBox,
											zoomMatrixWidth, midLongitude);

							// Update the tile row
							tileRow.setTileRow(newTileRow);
							tileRow.setTileColumn(newTileColumn);
							tileDao.update(tileRow);
						}
					} finally {
						tileResultSet.close();
					}

					// Calculate the pixel size
					double pixelXSize = (webMercatorBoundingBox
							.getMaxLongitude() - webMercatorBoundingBox
							.getMinLongitude())
							/ zoomMatrixWidth / tileMatrix.getTileWidth();
					double pixelYSize = (webMercatorBoundingBox
							.getMaxLatitude() - webMercatorBoundingBox
							.getMinLatitude())
							/ zoomMatrixHeight / tileMatrix.getTileHeight();

					// Update the tile matrix
					tileMatrix.setMatrixWidth(zoomMatrixWidth);
					tileMatrix.setMatrixHeight(zoomMatrixHeight);
					tileMatrix.setPixelXSize(pixelXSize);
					tileMatrix.setPixelYSize(pixelYSize);

					tileMatrixDao.update(tileMatrix);
				}
			}

			// Adjust the width and height to the min zoom level of the
			// request
			if (minNewOrUpdateZoom < minZoom) {
				long adjustment = (long) Math.pow(2, minZoom
						- minNewOrUpdateZoom);
				matrixWidth *= adjustment;
				matrixHeight *= adjustment;
			}

		}
	}

	/**
	 * Close the GeoPackage
	 */
	public void close() {
		if (geoPackage != null) {
			geoPackage.close();
		}
	}

	/**
	 * Generate the tiles for the zoom level
	 *
	 * @param tileMatrixDao
	 * @param tileDao
	 * @param contents
	 * @param zoomLevel
	 * @param tileGrid
	 * @param localTileGrid
	 * @param matrixWidth
	 * @param matrixHeight
	 * @param update
	 * @return tile count
	 * @throws java.sql.SQLException
	 * @throws java.io.IOException
	 */
	private int generateTiles(TileMatrixDao tileMatrixDao, TileDao tileDao,
			Contents contents, int zoomLevel, TileGrid tileGrid,
			TileGrid localTileGrid, long matrixWidth, long matrixHeight,
			boolean update) throws SQLException, IOException {

		int count = 0;

		Integer tileWidth = null;
		Integer tileHeight = null;

		// Download and create the tile and each coordinate
		for (long x = tileGrid.getMinX(); x <= tileGrid.getMaxX(); x++) {

			// Check if the progress has been cancelled
			if (progress != null && !progress.isActive()) {
				break;
			}

			for (long y = tileGrid.getMinY(); y <= tileGrid.getMaxY(); y++) {

				// Check if the progress has been cancelled
				if (progress != null && !progress.isActive()) {
					break;
				}

				try {

					// If an update, delete an existing row
					if (update) {
						tileDao.deleteTile(x, y, zoomLevel);
					}

					// Create the tile
					byte[] tileBytes = createTile(zoomLevel, x, y);

					if (tileBytes != null) {

						BufferedImage image = null;

						// Compress the image
						if (compressFormat != null) {
							image = ImageUtils.getImage(tileBytes);
							if (image != null) {
								tileBytes = ImageUtils.writeImageToBytes(image,
										compressFormat, compressQuality);
							}
						}

						// Create a new tile row
						TileRow newRow = tileDao.newRow();
						newRow.setZoomLevel(zoomLevel);

						long tileColumn = x;
						long tileRow = y;

						// Update the column and row to the local tile grid
						// location
						if (localTileGrid != null) {
							tileColumn = (x - tileGrid.getMinX())
									+ localTileGrid.getMinX();
							tileRow = (y - tileGrid.getMinY())
									+ localTileGrid.getMinY();
						}

						newRow.setTileColumn(tileColumn);
						newRow.setTileRow(tileRow);
						newRow.setTileData(tileBytes);
						tileDao.create(newRow);

						count++;

						// Determine the tile width and height
						if (tileWidth == null) {
							if (image == null) {
								image = ImageUtils.getImage(tileBytes);
							}
							if (image != null) {
								tileWidth = image.getWidth();
								tileHeight = image.getHeight();
							}
						}
					}
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Failed to create tile. Zoom: "
							+ zoomLevel + ", x: " + x + ", y: " + y, e);
					// Skip this tile, don't increase count
				}

				// Update the progress count, even on failures
				if (progress != null) {
					progress.addProgress(1);
				}

			}

		}

		// If none of the tiles were translated into a bitmap with dimensions,
		// delete them
		if (tileWidth == null || tileHeight == null) {
			count = 0;

			StringBuilder where = new StringBuilder();

			where.append(tileDao.buildWhere(TileTable.COLUMN_ZOOM_LEVEL,
					zoomLevel));

			where.append(" AND ");
			where.append(tileDao.buildWhere(TileTable.COLUMN_TILE_COLUMN,
					tileGrid.getMinX(), ">="));

			where.append(" AND ");
			where.append(tileDao.buildWhere(TileTable.COLUMN_TILE_COLUMN,
					tileGrid.getMaxX(), "<="));

			where.append(" AND ");
			where.append(tileDao.buildWhere(TileTable.COLUMN_TILE_ROW,
					tileGrid.getMinY(), ">="));

			where.append(" AND ");
			where.append(tileDao.buildWhere(TileTable.COLUMN_TILE_ROW,
					tileGrid.getMaxY(), "<="));

			String[] whereArgs = tileDao.buildWhereArgs(new Object[] {
					zoomLevel, tileGrid.getMinX(), tileGrid.getMaxX(),
					tileGrid.getMinY(), tileGrid.getMaxY() });

			tileDao.delete(where.toString(), whereArgs);

		} else {

			// Check if the tile matrix already exists
			boolean create = true;
			if (update) {
				create = !tileMatrixDao.idExists(new TileMatrixKey(tableName,
						zoomLevel));
			}

			// Create the tile matrix
			if (create) {

				// Calculate meters per pixel
				double pixelXSize = (webMercatorBoundingBox.getMaxLongitude() - webMercatorBoundingBox
						.getMinLongitude()) / matrixWidth / tileWidth;
				double pixelYSize = (webMercatorBoundingBox.getMaxLatitude() - webMercatorBoundingBox
						.getMinLatitude()) / matrixHeight / tileHeight;

				// Create the tile matrix for this zoom level
				TileMatrix tileMatrix = new TileMatrix();
				tileMatrix.setContents(contents);
				tileMatrix.setZoomLevel(zoomLevel);
				tileMatrix.setMatrixWidth(matrixWidth);
				tileMatrix.setMatrixHeight(matrixHeight);
				tileMatrix.setTileWidth(tileWidth);
				tileMatrix.setTileHeight(tileHeight);
				tileMatrix.setPixelXSize(pixelXSize);
				tileMatrix.setPixelYSize(pixelYSize);
				tileMatrixDao.create(tileMatrix);
			}
		}

		return count;
	}

	/**
	 * Create the tile
	 *
	 * @param z
	 * @param x
	 * @param y
	 * @return
	 */
	protected abstract byte[] createTile(int z, long x, long y);

}
