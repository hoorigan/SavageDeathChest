package com.winterhaven_mc.deathchest;

import java.util.ArrayList;

import org.bukkit.Location;


public abstract class DataStore {

	protected boolean initialized;

	protected DataStoreType type;

	protected String filename;

	/**
	 * Initialize the datastore
	 * @throws Exception
	 */
	abstract void initialize() throws Exception;


	/**
	 * Retrieve a record from the datastore
	 * @param location
	 * @return DeathChestBlock
	 */
	abstract DeathChestBlock getRecord(Location location);


	/**
	 * Retrieve a list of all records from the datastore
	 * @return ArrayList of DeathChestBlock
	 */
	abstract ArrayList<DeathChestBlock> getAllRecords();


	/**
	 * Insert a record in the datastore
	 * @param deathChestBlock
	 */
	abstract void putRecord(DeathChestBlock deathChestBlock);


	/**
	 * Delete a record from the datastore
	 * @param location
	 */
	abstract void deleteRecord(Location location);


	/**
	 * Close the datastore
	 */
	abstract void close();

	
	/**
	 * Sync the datastore to disk
	 */
	abstract void sync();

	
	/**
	 * Delete the datastore file or equivalent
	 */
	abstract void delete();

	
	/**
	 * Check for existence of datastore file or equivalent
	 * @return
	 */
	abstract boolean exists();

	
	/**
	 * Check if the datastore is initialized
	 * @return
	 */
	boolean isInitialized() {
		return this.initialized;
	}

	
	/**
	 * Set datastore initialized value
	 * @param initialized
	 */
	void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	
	/**
	 * Get the datastore type
	 * @return
	 */
	DataStoreType getType() {
		return this.type;
	}

	
	/**
	 * Get the datastore name
	 * @return
	 */
	String getName() {
		return this.getType().getName();
	}

	
	/**
	 * Get the datastore filename or equivalent
	 * @return
	 */
	String getFilename() {
		return this.filename;
	}

}
