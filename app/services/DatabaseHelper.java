package services;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import org.apache.lucene.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import play.Configuration;

/**
 * Database helper methods to retrieve and store data.
 * 
 * @author jose
 *
 */
public final class DatabaseHelper {

	private static final String CONFIG_FILE = "conf/database.properties";
	
	private Logger logger = LoggerFactory.getLogger(DatabaseHelper.class);

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
	public static long addNode(String title, String authors, String file) throws Exception {

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
				queryString = "MERGE (n:DOCUMENT {title: {1}, authors: {2}, file: {3}}) RETURN n";
			else
				queryString = "MERGE (n:DOCUMENT {title: {1}, authors: {2}}) RETURN n";

			try (PreparedStatement stmt = con.prepareStatement(queryString)){
				stmt.setString(1, title);
				stmt.setString(2, authors);
				if (file != null)
					stmt.setString(3, file);
				
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
	public static long createCitaton(Document doc, String title, String authors) throws Exception {
		
		// Creates a new node for cited document if needed
		addNode(title, authors, null);
		
		try (Connection con = getConnection()){
			con.setAutoCommit(false);
			//Directional edge from n->m return m (cited node)
			String queryString = "MATCH (n:DOCUMENT {title: {1}}), "
					+ "(m:DOCUMENT {title: {2}}) MERGE (n)-[r:CITES]->(m) RETURN m";

			try (PreparedStatement stmt = con.prepareStatement(queryString)){
				stmt.setString(1, doc.get("title"));
				stmt.setString(2, title);
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
			String queryString = "MATCH (n:DOCUMENT {title: {1}})<-[r:CITES]-() RETURN count(r) as total";

			try (PreparedStatement stmt = con.prepareStatement(queryString)){
				stmt.setString(1, doc.get("title"));
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
		}catch (Exception e) {
			throw e;
		}
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
