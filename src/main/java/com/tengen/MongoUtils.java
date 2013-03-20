package com.tengen;

import java.net.UnknownHostException;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

public class MongoUtils {

	public static DBCollection getCollection(String dbName, String collName) {
	
		// Connect to Mongo and get reference to Users collection
		MongoClient client = null;
		try {
			client = new MongoClient();
		} catch (UnknownHostException e) {
			// translate checked to runtime exception
			throw new IllegalArgumentException("Unable to connect to MongoDB on default host/port", e);
		}
	
		DB db = client.getDB(dbName);
		return db.getCollection(collName);
	
	}

}
