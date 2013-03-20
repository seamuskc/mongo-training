package com.tengen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class ValidateHW32 {

	private static final String DFLT_USER = "testUser";
	private static final String SALT_CHARS = "abcdefghijklmnopqrstuvwxyz1234567890";
	private static final int SALT_SIZE = SALT_CHARS.length();
	private final Random rnd = new Random();
	private static final String DFLT_SESSION_ID = "abc123";

	private final String host;
	private final int port;

	private DefaultHttpClient client = new DefaultHttpClient();

	
	public ValidateHW32() {
		this("localhost");
	}

	public ValidateHW32(String host) {
		this(host, 8082);
	}

	public ValidateHW32(String host, int port) {
		super();
		this.host = host;
		this.port = port;

		addDefaultSessionId();
	}

	private void addDefaultSessionId() {

		DBCollection sessions = MongoUtils.getCollection("blog", "sessions");
		sessions.save(new BasicDBObject("_id", DFLT_SESSION_ID).append(
				"username", DFLT_USER));
	}

	private String getHostAndPort() {
		return "http://" + host + ":" + port;
	}

	protected String makeSalt(int size) {

		String salt = "";
		for (int i = 0; i < size; i++) {
			salt += SALT_CHARS.charAt(rnd.nextInt(SALT_SIZE));
		}

		return salt;
	}

	protected boolean createUser(String userName, String password) {

		String signUpURl = getHostAndPort() + "/signup";
		HttpPost request = new HttpPost(signUpURl);

		List<NameValuePair> prms = new ArrayList<NameValuePair>();
		prms.add(new BasicNameValuePair("email", ""));
		prms.add(new BasicNameValuePair("username", userName));
		prms.add(new BasicNameValuePair("password", password));
		prms.add(new BasicNameValuePair("verify", password));
		request.setEntity(getUrlEncodedEntity(prms));
		System.out.println("Attempting to create user with following params:"
				+ prms.toString());
		HttpResponse response = makeRequest(request);
		return (response.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY && (getHostAndPort() + "/welcome")
				.equalsIgnoreCase(response.getHeaders("location")[0].getValue()));

	}

	private UrlEncodedFormEntity getUrlEncodedEntity(List<NameValuePair> params) {

		try {
			return new UrlEncodedFormEntity(params, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("unable to encode params.", e);
		}
	}

	protected HttpResponse makeRequest(HttpUriRequest request) {

		return makeRequest(request, true);

	}

	private HttpResponse makeRequest(HttpUriRequest request,
			boolean drainResponse) {

		try {
			request.addHeader(new BasicHeader("Cookie", "session=\""
					+ DFLT_SESSION_ID + "\""));
			HttpResponse response = this.client.execute(request);
			// System.out.println("Request:  " + request);
			// System.out.println("Response:  " + response);
			if (drainResponse) {
				drainResponse(response);
			}
			return response;
		} catch (Exception e) {
			throw new RuntimeException("Unable to make http request.", e);
		}
	}

	public boolean attemptLogin(String userName, String pw) {

		String loginUrl = getHostAndPort() + "/login";

		HttpPost request = new HttpPost(loginUrl);
		List<NameValuePair> prms = new ArrayList<NameValuePair>();
		prms.add(new BasicNameValuePair("username", userName));
		prms.add(new BasicNameValuePair("password", pw));
		request.setEntity(getUrlEncodedEntity(prms));
		System.out.println("Attempting to login using params: " + prms.toString());
		HttpResponse response = makeRequest(request);
		String expectedLocation = getHostAndPort() + "/welcome";
		return verifyRedirect(response, expectedLocation);
		

	}

	public boolean addBlogPost(String title, String post, String tags) {

		String newPostUrl = getHostAndPort() + "/newpost";

		HttpPost request = new HttpPost(newPostUrl);

		request.addHeader(new BasicHeader("Cookie", "session=\""
				+ DFLT_SESSION_ID + "\""));
		List<NameValuePair> prms = new ArrayList<NameValuePair>();
		prms.add(new BasicNameValuePair("body", post));
		prms.add(new BasicNameValuePair("subject", title));
		prms.add(new BasicNameValuePair("tags", tags));
		request.setEntity(getUrlEncodedEntity(prms));
		HttpResponse response = makeRequest(request);
		String expectedLocation = getHostAndPort() + "/post/" + title;
		return verifyRedirect(response, expectedLocation);
		

	}

	public boolean addBlogComment(String title, String author,
			String commentBody) {

		System.out.println("Attemtping to add comment to blog with title "
				+ title);
		DBCollection blogs = MongoUtils.getCollection("blog", "posts");
		DBObject blogPost = blogs.findOne(new BasicDBObject("title", title));
		if (blogPost == null) {
			System.out.println("No blog post found for title " + title);
			return false;
		}

		String permalink = (String) blogPost.get("permalink");

		List<NameValuePair> prms = new ArrayList<NameValuePair>();
		prms.add(new BasicNameValuePair("commentName", author));
		prms.add(new BasicNameValuePair("commentBody", commentBody));
		prms.add(new BasicNameValuePair("permalink", permalink));

		String newPostUrl = getHostAndPort() + "/newcomment";
		HttpPost request = new HttpPost(newPostUrl);
		request.setEntity(getUrlEncodedEntity(prms));

		HttpResponse response = makeRequest(request, false);
		drainResponse(response);
		String expectedLocation = getHostAndPort() + "/post/" + permalink;
		return verifyRedirect(response, expectedLocation);
		

	}

	private boolean verifyRedirect(HttpResponse response,
			String expectedLocation) {
		
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != HttpStatus.SC_MOVED_TEMPORARILY) {
			System.out.println("Expected redirect status code (302), recieved " + statusCode + " instead.");
			return false;
		}
		String actualLocation = response.getHeaders("location")[0].getValue();
		
		if (!expectedLocation.equals(actualLocation)) {
			System.out.println("Unexpected location returned. Expected " + expectedLocation + " Actual: " + actualLocation);
			return false;
		}
		
		return true;
	}

	public boolean checkHomePage(String title1, String title2) {

		String newPostUrl = getHostAndPort() + "/";
		HttpGet request = new HttpGet(newPostUrl);
		HttpResponse response = makeRequest(request, false);

		if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
			System.out.println("Unable to display Blog index.");
			return false;
		}

		String content = getResponseString(response);
		int title1Location = content.indexOf(title1);
		int title2Location = content.indexOf(title2);

		// Verify that both blogs are on the page and that the newest comes
		// before the oldest
		return (title1Location > -1 && title2Location > -1 && title1Location > title2Location);

	}

	private void drainResponse(HttpResponse response) {
		
		HttpEntity entity = response.getEntity();
		if (entity == null) {
			return;
		}

		try {

			String content = inputStreamAsString(entity.getContent());
//			InputStream inputStream = entity.getContent();
//			String content = IOUtils.toString(entity.getContent());
		
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private String getResponseString(HttpResponse response) {

		HttpEntity entity = response.getEntity();
		if (entity == null) {
			return null;
		}

		String content = null;
		try {

			content = inputStreamAsString(entity.getContent());
			return content;

		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return content;
	}
	
private String inputStreamAsString(InputStream content) {
		
		InputStreamReader is = new InputStreamReader(content);
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(is);
		
		try {
			String read = br.readLine();
			while (read != null) {
				sb.append(read);
				read = br.readLine();
			}
		} catch (Exception e) {}
		finally {
			try {
				content.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return sb.toString();
	}

}
