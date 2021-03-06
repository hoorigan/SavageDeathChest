package com.winterhaven_mc.deathchest;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Location;


/**
 * SQLite implementation of Datastore
 * for saving death chest block locations
 * @author Tim Savage
 *
 */

public class DataStoreSQLite extends DataStore {

	// reference to main class
	private PluginMain plugin;

	// database connection object
	private Connection connection;


	/**
	 * Class constructor
	 * @param plugin
	 */
	DataStoreSQLite (PluginMain plugin) {

		// reference to main class
		this.plugin = plugin;

		// set datastore type
		this.type = DataStoreType.SQLITE;

		// set filename
		this.filename = "deathchests.db";

	}


	/**
	 * initialize the database connection and
	 * create table if one doesn't already exist
	 * @throws SQLException 
	 * @throws ClassNotFoundException 
	 */
	@Override
	void initialize() throws SQLException, ClassNotFoundException {
		
		// if data store is already initialized, do nothing and return
		if (this.isInitialized()) {
			plugin.getLogger().info(this.getName() + " datastore already initialized.");
			return;
		}

		// sql statement to create table if it doesn't already exist
		final String createBlockTable = "CREATE TABLE IF NOT EXISTS blocks (" +
				"blockid INTEGER PRIMARY KEY, " +
				"ownerid VARCHAR(36) NOT NULL, " +
				"killerid VARCHAR(36), " +
				"worldname VARCHAR(255) NOT NULL, " +
				"x INTEGER, " +
				"y INTEGER, " +
				"z INTEGER, " +
				"expiration INTEGER, " +
				"UNIQUE (worldname,x,y,z) )";

		// register the driver 
		final String jdbcDriverName = "org.sqlite.JDBC";

		Class.forName(jdbcDriverName);

		// create database url
		String deathChestsDb = plugin.getDataFolder() + File.separator + filename;
		String jdbc = "jdbc:sqlite";
		String dbUrl = jdbc + ":" + deathChestsDb;

		// create a database connection
		connection = DriverManager.getConnection(dbUrl);
		Statement statement = connection.createStatement();

		// execute table creation statement
		statement.executeUpdate(createBlockTable);

		// set initialized true
		setInitialized(true);
		
		// output log message
		plugin.getLogger().info(this.getName() + " datastore initialized.");
	}

	@Override
	DeathChestBlock getRecord(Location location) {

		final String sqlGetDeathChestBlock = "SELECT * FROM blocks "
				+ "WHERE worldname = ? "
				+ "AND x = ? "
				+ "AND y = ? "
				+ "AND z = ?";

		DeathChestBlock deathChestBlock = new DeathChestBlock();

		try {

			PreparedStatement preparedStatement = connection.prepareStatement(sqlGetDeathChestBlock);

			preparedStatement.setString(1, location.getWorld().getName());
			preparedStatement.setInt(2, location.getBlockX());
			preparedStatement.setInt(3, location.getBlockY());
			preparedStatement.setInt(4, location.getBlockZ());

			// execute sql query
			ResultSet rs = preparedStatement.executeQuery();

			// only zero or one record can match the unique location
			if (rs.next()) {

				// try to convert owner uuid from stored string, or set to null if invalid uuid
				try {
					deathChestBlock.setOwnerUUID(UUID.fromString(rs.getString("ownerid")));
				}
				catch (Exception e) {
					deathChestBlock.setOwnerUUID((UUID)null);
				}

				// try to convert killer uuid from stored string, or set to null if invalid uuid
				try {
					deathChestBlock.setKillerUUID(UUID.fromString(rs.getString("killerid")));
				}
				catch (Exception e) {
					deathChestBlock.setOwnerUUID((UUID)null);
				}

				// set other fields in deathChestBlock from database
				deathChestBlock.setLocation(location);
				deathChestBlock.setExpiration(rs.getLong("expiration"));
			}

			// return null DeathChestBlock object if no matching location exists in database
			else {
				deathChestBlock = null;
			}

		}
		catch (SQLException e) {

			// output simple error message
			plugin.getLogger().warning("An error occured while fetching a death chest block from the SQLite database.");
			plugin.getLogger().warning(e.getMessage());

			// if debugging is enabled, output stack trace
			if (plugin.debug) {
				e.printStackTrace();
			}
		}

		return deathChestBlock;
	}

	@Override
	ArrayList<DeathChestBlock> getAllRecords() {

		ArrayList<DeathChestBlock> results = new ArrayList<DeathChestBlock>();

		// sql statement to retrieve all records
		final String sqlSelectAllRecords = "SELECT * FROM blocks";

		try {

			PreparedStatement preparedStatement = connection.prepareStatement(sqlSelectAllRecords);

			// execute sql query
			ResultSet rs = preparedStatement.executeQuery();

			while (rs.next()) {

				// create empty DeathChestBlock object
				DeathChestBlock deathChestBlock = new DeathChestBlock();

				// try to convert owner uuid from stored string
				try {
					deathChestBlock.setOwnerUUID(UUID.fromString(rs.getString("ownerid")));
				}
				catch (Exception e) {
					plugin.getLogger().warning("[SQLite getAllRecords] An error occured while trying to set ownerUUID.");
					plugin.getLogger().warning("[SQLite getAllRecords] ownerid string: " + rs.getString("ownerid"));
					plugin.getLogger().warning(e.getLocalizedMessage());
					continue;
				}

				// try to convert killer uuid from stored string, or set to null if invalid uuid
				try {
					deathChestBlock.setKillerUUID(UUID.fromString(rs.getString("killerid")));
				}
				catch (Exception e) {
					deathChestBlock.setKillerUUID(null);
				}

				String worldName = rs.getString("worldname");

				// check that world is valid
				if (plugin.getServer().getWorld(worldName) == null) {

					// world does not exist, so output log message and continue to next record
					plugin.getLogger().warning("Saved deathchest world '" + worldName + "' does not exist.");

					// delete all expired records in database that have this invalid world
					deleteExpiredRecords(worldName);
					continue;
				}

				// create Location object from database fields
				Location location = new Location(plugin.getServer().getWorld(worldName),
						rs.getInt("x"),
						rs.getInt("y"),
						rs.getInt("z"));

				// set other fields in deathChestBlock from database fields
				deathChestBlock.setLocation(location);
				deathChestBlock.setExpiration(rs.getLong("expiration"));

				// add DeathChestObject to results ArrayList
				results.add(deathChestBlock);
			}
		}
		catch (SQLException e) {

			// output simple error message
			plugin.getLogger().warning("An error occurred while trying to fetch all records from the SQLite database.");
			plugin.getLogger().warning(e.getMessage());

			// if debugging is enabled, output stack trace
			if (plugin.debug) {
				e.printStackTrace();
			}
		}
		if (plugin.debug) {
			plugin.getLogger().info(results.size() + " records fetched from SQLite datastore.");
		}
		return results;
	}

	@Override
	void putRecord(final DeathChestBlock deathChestBlock) {

		// sql statement to insert or replace record
		final String sqlInsertDeathChestBlock = "INSERT OR REPLACE INTO blocks "
				+ "(ownerid,killerid,worldname,x,y,z,expiration) "
				+ "values(?,?,?,?,?,?,?)";

		// catch invalid uuid exceptions, and set to null
		String ownerid = null;
		String killerid = null;
		try {
			ownerid = deathChestBlock.getOwnerUUID().toString();
		}
		catch (Exception e) {
			plugin.getLogger().warning("DeathChestBlock owner UUID is invalid.");
			return;
		}

		try {
			killerid = deathChestBlock.getKillerUUID().toString();
		}
		catch (Exception e) {
			killerid = null;
		}

		try {
			// create prepared statement
			PreparedStatement preparedStatement = connection.prepareStatement(sqlInsertDeathChestBlock);

			preparedStatement.setString(1, ownerid);
			preparedStatement.setString(2, killerid);
			preparedStatement.setString(3, deathChestBlock.getLocation().getWorld().getName());
			preparedStatement.setInt(4, deathChestBlock.getLocation().getBlockX());
			preparedStatement.setInt(5, deathChestBlock.getLocation().getBlockY());
			preparedStatement.setInt(6, deathChestBlock.getLocation().getBlockZ());
			preparedStatement.setLong(7, deathChestBlock.getExpiration());

			// execute prepared statement
			int rowsAffected = preparedStatement.executeUpdate();

			// output debugging information
			if (plugin.debug) {
				plugin.getLogger().info(rowsAffected + " rows affected.");
			}
		}
		catch (SQLException e) {

			// output simple error message
			plugin.getLogger().warning("An error occured while inserting a deathchest block into the SQLite database.");
			plugin.getLogger().warning(e.getMessage());

			// if debugging is enabled, output stack trace
			if (plugin.debug) {
				e.printStackTrace();
			}
		}

	}

	@Override
	void deleteRecord(Location location) {

		final String sqlDeleteDeathChestBlock = "DELETE FROM blocks "
				+ "WHERE worldname = ? AND x = ? AND y = ? and z =?";

		try {
			// create prepared statement
			PreparedStatement preparedStatement = connection.prepareStatement(sqlDeleteDeathChestBlock);

			preparedStatement.setString(1, location.getWorld().getName());
			preparedStatement.setInt(2, location.getBlockX());
			preparedStatement.setInt(3, location.getBlockY());
			preparedStatement.setInt(4, location.getBlockZ());

			// execute prepared statement
			int rowsAffected = preparedStatement.executeUpdate();

			// output debugging information
			if (plugin.debug) {
				plugin.getLogger().info(rowsAffected + " rows deleted.");
			}
		}
		catch (SQLException e) {

			// output simple error message
			plugin.getLogger().warning("An error occurred while attempting to delete a record from the SQLite database.");
			plugin.getLogger().warning(e.getMessage());

			// if debugging is enabled, output stack trace
			if (plugin.debug) {
				e.printStackTrace();
			}
		}
	}


	/**
	 * Close database connection
	 */
	@Override
	void close() {

		if (isInitialized()) {
			try {
				connection.close();
				plugin.getLogger().info(this.getName() + " datastore connection closed.");		
			}
			catch (SQLException e) {

				// output simple error message
				plugin.getLogger().warning("An error occured while closing the SQLite database connection.");
				plugin.getLogger().warning(e.getMessage());

				// if debugging is enabled, output stack trace
				if (plugin.debug) {
					e.printStackTrace();
				}
			}
			setInitialized(false);
		}
	}


	@Override
	void sync() {
		// no action necessary for this storage type
	}


	@Override
	void delete() {
		File dataStoreFile = new File(plugin.getDataFolder() + File.separator + this.getFilename());
		if (dataStoreFile.exists()) {
			dataStoreFile.delete();
		}
	}


	@Override
	boolean exists() {		
		// get path name to old data store file
		File dataStoreFile = new File(plugin.getDataFolder() + File.separator + this.getFilename());
		return dataStoreFile.exists();
	}


	/**
	 * Delete expired records in world <i>worldName</i>
	 * @param worldName
	 */
	void deleteExpiredRecords(String worldName) {

		final String sqlDeleteDeathChestBlock = "DELETE FROM blocks "
				+ "WHERE worldname = ? AND expiration > ?";

		// current time in milliseconds
		final Long currentTime = System.currentTimeMillis();

		try {
			// create prepared statement
			PreparedStatement preparedStatement = connection.prepareStatement(sqlDeleteDeathChestBlock);

			preparedStatement.setString(1, worldName);
			preparedStatement.setLong(2, currentTime);

			// execute prepared statement
			int rowsAffected = preparedStatement.executeUpdate();

			// output debugging information
			if (plugin.debug) {
				plugin.getLogger().info(rowsAffected + " rows deleted.");
			}
		}
		catch (SQLException e) {

			// output simple error message
			plugin.getLogger().warning("An error occurred while attempting to delete expired records from the SQLite database.");
			plugin.getLogger().warning(e.getMessage());

			// if debugging is enabled, output stack trace
			if (plugin.debug) {
				e.printStackTrace();
			}
		}
	}

}
