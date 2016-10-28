package services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.apache.lucene.document.Document;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Database helper methods to retrieve and store data.
 * 
 * @author jose
 *
 */
public final class DatabaseHelper {

	/**
	 * Neo4j connection URL
	 */
	private final String neo4jUrl;

	/**
	 * Neo4j username
	 */
	private final String neo4jUser;

	/**
	 * Neo4j password
	 */
	private final String neo4jPassword;

	private static final DatabaseHelper INSTANCE = new DatabaseHelper();

	/**
	 * Creates a new {@link DatabaseHelper} and loads
	 * configuration from conf/database.properties.
	 */
	private DatabaseHelper() {
		super();

		Config config = ConfigFactory.load();
		neo4jUrl = config.getString("neo4j.url");
		neo4jUser = config.getString("neo4j.username");
		neo4jPassword = config.getString("neo4j.password");
	}


	public static DatabaseHelper getInstance(){
		return INSTANCE;
	}

	/**
	 * Returns a connection to the database.
	 * The caller is responsible to close connection
	 * after used it.
	 * @return connection.
	 * @throws SQLException if an error occurs when 
	 * open a new connection.
	 */
	public static Connection getConnection() throws SQLException{
		return DriverManager.getConnection(INSTANCE.neo4jUrl, INSTANCE.neo4jUser, INSTANCE.neo4jPassword);
	}

	/**
	 * Adds a node representing a document into Neo4j database.
	 * @param title document's title
	 * @param authors document's authors
	 * @param file document's file
	 * @return the new node Neo4j internal id.
	 * @throws Exception if any error occurs when creating
	 * the new node.
	 */
	public static long addNode(String doi, String title, String authors, String year, String file) throws Exception {

		if (doi == null && (title == null && authors == null && year == null)){
			throw new Exception("Can't add empty node");
		}
		
		//Clean values
		doi = doi == null ? "" : doi;
		title = title == null ? "" : title;
		authors = authors == null ? "" :  authors;
		year = year == null ?  "" : year;
		
		try (Connection con = getConnection()){
			con.setAutoCommit(false);

			// If the new node to create is already in the index
			// it has a associated file.
			// If the new node is just a node from a reference of a
			// node in the index, it does not has a file property.
			// So, we can differentiate nodes (documents) in the index
			// from that only in the graph (not already inserted).
			String queryString;
			if (file != null)
				queryString = "MERGE (n:DOCUMENT {doi: {1}, title: {2}, authors: {3}, year: {4}, file: {5}}) RETURN n";
			else
				queryString = "MERGE (n:DOCUMENT {doi: {1}, title: {2}, authors: {3}, year: {4}}) RETURN n";

			try (PreparedStatement stmt = con.prepareStatement(queryString)){
				stmt.setString(1, doi);
				stmt.setString(2, title);
				stmt.setString(3, authors);
				stmt.setString(4, year);
				if (file != null)
					stmt.setString(5, file);

				// Get the new node object and return its internal id
				ResultSet rs = stmt.executeQuery();
				if (rs.next()){
					Map<String, Object> node = (Map<String, Object>) rs.getObject(1);
					Object id = node.get("_id");
					if (id != null){
						// close and commit transaction
						rs.close();
						con.commit();
						return ((Long) id).longValue();
					}
				}

				//Should never happen!
				throw new Exception("Should neve happen! Query does not return expected value for node");
			}catch (Exception e) {
				con.rollback();
				throw e;
			}
		}catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Creates a citation in the graph. A citation is a directional edge from
	 * node representing a document a its referenced document (title and authors).
	 * <p>This method will also create a new node in the graph for cited document if it is not
	 * in database yet.</p>
	 * @param doc the main document that makes the citation.
	 * @param title the title of cited document.
	 * @param authors the authors of cited document.
	 * @return the id of cited node.
	 * @throws Exception if any error occurs when creating the new edge.
	 */
	public static long createCitaton(Document doc, String doi, String title, String authors, String year) throws Exception {

		// Creates a new node for cited document if needed
		addNode(doi, title, authors, year, null);

		try (Connection con = getConnection()){
			con.setAutoCommit(false);
			//Directional edge from n->m return m (cited node)
			String queryString = null;
			
			String param1 = doc.get("doi");
			String param2 = null;
			if (param1 != null && !param1.isEmpty()){
				if (doi != null && !doi.isEmpty()){
					queryString = "MATCH (n:DOCUMENT {doi: {1}}), "
							+ "(m:DOCUMENT {doi: {2}}) MERGE (n)-[r:CITES]->(m) RETURN m";
					param2 = doi;
				}
				else if (title != null && !title.isEmpty()){
					queryString = "MATCH (n:DOCUMENT {doi: {1}}), "
							+ "(m:DOCUMENT {title: {2}}) MERGE (n)-[r:CITES]->(m) RETURN m";
					param2 = title;
				}
			}
			else{
				param1 = doc.get("title");
				if (param1 != null && !param1.isEmpty()){
					if (doi != null && !doi.isEmpty()){
						queryString = "MATCH (n:DOCUMENT {title: {1}}), "
								+ "(m:DOCUMENT {doi: {2}}) MERGE (n)-[r:CITES]->(m) RETURN m";
						param2 = doi;
					}
					else if (title != null && !title.isEmpty()){
						queryString = "MATCH (n:DOCUMENT {title: {1}}), "
								+ "(m:DOCUMENT {title: {2}}) MERGE (n)-[r:CITES]->(m) RETURN m";
						param2 = title;
					}
				}
			}

			if (queryString == null)
				throw new Exception("Document has no DOI or title. Can't create citation!");
			
			try (PreparedStatement stmt = con.prepareStatement(queryString)){
				stmt.setString(1, param1);
				stmt.setString(2, param2);
				ResultSet rs = stmt.executeQuery();

				if (rs.next()){
					Map<String, Object> node = (Map<String, Object>) rs.getObject(1);
					Object id = node.get("_id");
					if (id != null){
						//close and commit transaction
						rs.close();
						con.commit();
						return ((Long) id).longValue();
					}
				}

				//Should never happen!
				throw new Exception("Should neve happen! Query does not return expected value for cited node"); 
			}catch (Exception e) {
				con.rollback();
				throw e;
			}
		}catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Get the number of citations of a document.
	 * The number of citations are calculated based on the graph
	 * nodes and edges, it is not a stored property of nodes. So,
	 * this only reflect the citations actually in database, and not 
	 * the citations count in literature.
	 * <p>Since the database grows this number approximates to the real
	 * number of citations or provide a good approximation for scoring
	 * more relevant documents.
	 * @param doc the document to retrieve number of citations.
	 * @return a long with number of citations.<b> Note:</b>
	 * the number of citations can be zero.
	 * @throws Exception if any error occurs when querying database.
	 */
	public static long getNumberOfCitations(Document doc) throws Exception 
	{

		try (Connection con = getConnection()){
			con.setAutoCommit(false);
			String value = doc.get("citDOI");
			String queryString = null;
			if (value != null && !value.isEmpty())
				queryString = "MATCH (n:DOCUMENT {doi: {1}})<-[r:CITES]-() RETURN count(r) as total";
			else{
				value = doc.get("title");
				if (value != null && !value.isEmpty())
					queryString = "MATCH (n:DOCUMENT {title: {1}})<-[r:CITES]-() RETURN count(r) as total";
			}
			if (queryString != null){
				try (PreparedStatement stmt = con.prepareStatement(queryString)){
					stmt.setString(1, value);
					ResultSet rs = stmt.executeQuery();
					if (rs.next()){
						con.commit();
						return rs.getLong("total");
					}
					con.rollback();
					return -1;
				}catch (Exception e) {
					con.rollback();
					throw e;
				}
			}
		}catch (Exception e) {
			throw e;
		}
		return 0;
	}

	/**
	 * Delete a node from database with given internal id.
	 * @param id the id of the node to delete.
	 * @throws Exception if an error occurs deleting the node.
	 */
	public static void deleteNode(String id) throws Exception {
		try (Connection con = getConnection()){
			con.setAutoCommit(false);
			String queryString = "MATCH (p:DOCUMENT) where ID(p)={1} OPTIONAL MATCH (p)-[r]-() DELETE r,p";

			try (PreparedStatement stmt = con.prepareStatement(queryString)){
				stmt.setLong(1, Long.parseLong(id));
				stmt.executeUpdate();
				con.commit();
			}catch (Exception e) {
				con.rollback();
				throw e;
			}
		}catch (Exception e) {
			throw e;
		}
	}
}
