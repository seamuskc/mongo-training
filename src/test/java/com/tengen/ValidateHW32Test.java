package com.tengen;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.JUnitCore;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class ValidateHW32Test {

	ValidateHW32 validator;
	private static String host = "localhost";
	private static int port = 8082;

	public static void main(String [] args) {
		
		for (int i = 0; i < args.length; i++) {
			if ("-h".equalsIgnoreCase(args[i])) {
				host = args[i+1];
			}
			if ("-p".equalsIgnoreCase(args[i])) {
				port = Integer.parseInt(args[i+1]);
			}
			if ("-help".equalsIgnoreCase(args[i])) {
				System.out.println("Validate Module supports the following command line args:");
				System.out.println("   -h [hostname]  Specify alternate hostname where blog app is running (defaults to localhost).");
				System.out.println("   -p [port]      Specify an alternate port where blog app is running (defaults to 8082).");
				System.out.println("");
				System.out.println("TODO: Parameterize connection string to allow mongod running on non-standard port/machine.");
				return;
			}
			
		}
		
		System.out.println("startup host:" + host + ":" + port);
		
		JUnitCore.main("com.tengen.ValidateHW32Test");
	}
	
	@Before
	public void setUp() throws Exception {
		validator = new ValidateHW32(host, port);
	}

	@BeforeClass
	public static void startUp() {

		DBCollection sessions = MongoUtils.getCollection("blog", "sessions");
		sessions.getDB().dropDatabase();

	}

	@Ignore
	@Test
	public void testMakeSalt() {
		String salt = validator.makeSalt(10);
		assertNotNull(salt);
		assertEquals(salt.length(), 10);
		System.out.println("Generated Salt: " + salt);

	}

	@Test
	public void testBlogApp() {

		
		System.out.println("Welcome to the HW 3.2 and HW 3.3 validation tester");
		
		// Test Create User
		String userName = validator.makeSalt(7);
		String password = validator.makeSalt(10);
		
		testCreateUser(userName, password);
		System.out.println("User creation successful.");
		testAttemptLogin(userName, password);
		System.out.println("User login successful.");
		List<String> titles 
			= Arrays.asList(validator.makeSalt(10), validator.makeSalt(10));
		List<String> blogs 
			= Arrays.asList(validator.makeSalt(30), validator.makeSalt(30));
		String tags = "t1,t2,t3";
		
		testAddBlogPost(titles.get(0), blogs.get(0), tags);
		System.out.println("Submission of single post successful");
		testAddBlogPost(titles.get(1), blogs.get(1), tags);
		System.out.println("Submission of second post successful");
		
		// Test Adding a comment
		testAddComment(titles.get(0));
		System.out.println("Added comment to first blog");
		

		// Check Home Page
		// Assert Preconditions - No existing comments
		// Need to ensure a blog is in place in order to comment
		assertTrue("Blog index does not have the posts present or they are ordered incorrectly.", validator.checkHomePage(titles.get(0), titles.get(1)));
		System.out.println("Blog index looks good");
		System.out.println("Tests Passed for HW 3.3. Your HW 3.3 validation code is ihfr48nf89jk09309kj0d2d");
		
		
	}

	private void testAddBlogPost(String title, String post, String tags) {
		// Test Create New Blog
		System.out.println("Attempting to create blog with title: " + title);
		assertTrue("Unable to add blog post.", validator.addBlogPost(title, post, tags));
		DBCollection posts = MongoUtils.getCollection("blog", "posts");
		assertNotNull("Newly added blog post with title '" + title + "' not found in mongo.", 
				posts.findOne(new BasicDBObject("title", title).append("body", post)));

	}

	private void testAddComment(String title) {

		DBCollection posts = MongoUtils.getCollection("blog", "posts");
		DBObject post = posts.findOne(new BasicDBObject("title", title));
		assertNotNull(post);
		List<DBObject> comments = (List<DBObject>) post.get("comments");
		assertTrue(comments != null && comments.size() == 0);

		String author = validator.makeSalt(7);
		String commentBody = validator.makeSalt(30);
		assertTrue("Add Comments Failed.", validator.addBlogComment(title, author, commentBody));
		post = posts.findOne(new BasicDBObject("title", title));
		assertNotNull(post);
		comments = (List<DBObject>) post.get("comments");
		assertTrue("Newly added comment not found in MongoDB for title " + title, comments != null && comments.size() == 1);
		assertEquals("Comment author attribute doesn't match", author, comments.get(0).get("author"));
		assertEquals("Comment body doesn't match", commentBody, comments.get(0).get("body"));
	}

	private void testAttemptLogin(String userName, String password) {
		assertTrue("Unable to Login using creds : " + userName + "/" + password, 
				validator.attemptLogin(userName, password));
	}

	private void testCreateUser(String userName, String password) {
		
		assertTrue("Create User Failed.", validator.createUser(userName, password));
		DBCollection usersCollection = MongoUtils.getCollection("blog", "users");
		assertNotNull("Unable to find user ('" + userName + "') in MongoDB.", 
				usersCollection.findOne(new BasicDBObject("_id", userName)));
	}

}
