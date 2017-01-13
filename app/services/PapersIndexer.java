package services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spell.LevensteinDistance;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.grobid.core.mock.MockContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import play.Configuration;
import services.parsers.CermineDocumentParser;
import services.parsers.GrobIDDocumentParser;
import services.search.PapersIndexSearcher;

/**
 * Main class for indexing documents (papers) 
 *  using Lucene API.
 * @author jose
 *
 */
@Singleton
public class PapersIndexer {

	private static final String NULL_DOI = "NULL";

	private static final float MAX_DISTANCE = 0.7f;

	private Logger logger = LoggerFactory.getLogger(PapersIndexer.class);

	/**
	 * Index directory
	 */
	private final String indexDir;

	/**
	 * Instance of {@link PapersIndexSearcher} for
	 * retrieve IndexSearcher from SearcherManager
	 * and refresh index reader.
	 */
	private final PapersIndexSearcher papersIndexSearcher;

	/**
	 * Document Parser: GROBID and Cermine
	 */
	private DocumentParser documentParsers[];



	/**
	 * Creates a document indexer to index documents from 
	 * a directory or specific file.
	 * @param indexDir the directory where index is located (lucene database)
	 * @throws Exception 
	 */
	@Inject
	public PapersIndexer(Configuration configuration, PapersIndexSearcher isearcher) throws Exception {
		this.indexDir = configuration.getString("luceneIndexDir", "db");
		this.papersIndexSearcher = isearcher;
		initializeParsers();
	}

	private void initializeParsers() throws Exception {
		documentParsers = new DocumentParser[2];
		documentParsers[0] = new GrobIDDocumentParser();
		documentParsers[1] = new CermineDocumentParser();
	}

	/**
	 * Creates a new {@link IndexWriter} based on
	 * default configuration and using {@link CitationSimilarity}
	 * to score documents.<p> Creates new IndexWriter is expensive,
	 * but how documents will be eventually added to Index maintain
	 * a IndexWriter during all application live is a complete waste
	 * of resources</p> 
	 * @return a {@link IndexWriter} to add/remove/update document 
	 * from index.
	 * @throws IOException if index directory can't be open.
	 */
	private IndexWriter newIndexWriter() throws IOException{
		Directory dir = FSDirectory.open(new File(indexDir).toPath());
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
		cfg.setSimilarity(new CitationSimilarity(IndexSearcher.getDefaultSimilarity()));
		IndexWriter writer = new IndexWriter(dir, cfg);
		return writer;
	}

	/**
	 * Import all document in given directory to the index and
	 * also create Neo4j nodes.
	 * Initially all imported documents have citation count 1 (one)
	 * then {@link #updateCitations(DatabaseHelper, Document)} is called
	 * to update citation count fields.
	 * @param docsDir directory contains PDF documents.
	 * @throws IOException a error occurs when indexing documents.
	 */
	public void addDocuments(String docsDir) throws IOException
	{

		try (final IndexWriter writer = newIndexWriter()) 
		{
			File dir = new File(docsDir);
			final List<Document> docs = new ArrayList<>();
			//Iterates over all documents in the directory
			Files.list(dir.toPath()).forEach( (path) -> 
			{
				try {
					// Parse document file
					Document doc = parseDocument(path.toFile());
					List<Bibliography> references = parseReferences(path.toFile(), doc);

					if (doc != null){
						// At this point documents have not be inserted into
						// neo4j database, so we cannot calculate citation count
						// unless all documents have been added.
						// All documents will have same citCount (1.0) for scoring.
						// CitationSimilarity use citCount as a multiplication factor
						// documents with no citation are set to have 1 citation, so
						// it will not affect the scoring process.
						NumericDocValuesField citCount = new NumericDocValuesField("citCount", 1L);
						doc.add(citCount);

						// Adds node to Neo4j database and get its internal id
						long nodeId = DatabaseHelper.addNode(doc.get("doi"), doc.get("title"), doc.get("authors"), 
								doc.get("year"), doc.get("file"));
						// Adds the Neo4j node's id to the index, so we can retrieve it from 
						// index when searching and easy recover it from Neo4j.
						doc.add(new StringField("id", ""+nodeId, Store.YES));

						// Add citation nodes
						for(Bibliography bib : references){
							try {
							long citedNodeId = addCitation(doc, bib);
							doc.add(new StringField("cite_id", ""+citedNodeId, Store.YES));
							}catch (Exception e) {
								logger.error("",e);
								continue;
							}
						}

						// Write document to the index
						writer.addDocument(doc);
						docs.add(doc);
					}

				} catch (Exception e) {
					logger.error("Error importing document: "+path.toAbsolutePath(), e);
				}
			});

			// Commit and delete unused files
			writer.commit();
			writer.deleteUnusedFiles();

			updateCitations(writer, docs);

		}catch(Exception e){
			throw e;	
		}

	}

	/**
	 * Adds a new document to index
	 * @param docPath the full path to the document
	 * to be inserted
	 * @throws Exception error adding to index.
	 */
	public void addDocument(String docPath) throws Exception
	{
		try (final IndexWriter writer = newIndexWriter()) 
		{
			File file = new File(docPath);
			Document doc = parseDocument(file);
			List<Bibliography> references = parseReferences(file, doc);
			if (doc != null){

				// Just initialize citCount as 1
				NumericDocValuesField citCount = new NumericDocValuesField("citCount", 1L);
				doc.add(citCount);

				// Adds node to Neo4j graph
				long nodeId = DatabaseHelper.addNode(doc.get("doi"), doc.get("title"), doc.get("authors"),
						doc.get("year"), doc.get("file"));
				// And the Neo4j id as a document's field
				doc.add(new StringField("id", ""+nodeId, Store.YES));

				// Add citation nodes
				if (references != null){
					for(Bibliography bib : references){
						long citedNodeId = addCitation(doc, bib);
						doc.add(new StringField("cite_id", ""+citedNodeId, Store.YES));
					}
				}

				// Write document to the Index
				writer.addDocument(doc);

				writer.commit();
				writer.deleteUnusedFiles();

				updateCitations(writer, Arrays.asList(doc));
			}
		}catch(Exception e){
			throw e;
		}
	}

	/**
	 * Removes a document from the Index.
	 * @param id the id of the document to remove (Neo4j node id).
	 * @throws Exception if any error occurs when removing the document.
	 */
	public void removeDocument(String id) throws Exception{
		// First we need to search for a document with given id.
		// It is a exact match query
		Term idTerm = new Term("id", id);
		TermQuery query = new TermQuery(idTerm);

		try (final IndexWriter writer = newIndexWriter()) {

			final IndexSearcher isearch = papersIndexSearcher.getSearcherManager().acquire();

			// Search for the document
			ScoreDoc[] hits = isearch.search(query, 1).scoreDocs;
			// If found, at least one and just one (?)
			if (hits.length > 0){

				// Remove from Neo4j first
				DatabaseHelper.deleteNode(id);

				updateCitations(writer, Arrays.asList(isearch.doc(hits[0].doc)));

				// Remove from Index
				writer.deleteDocuments(idTerm);

				// Commit changes
				writer.commit();
				writer.deleteUnusedFiles();
			}

			// Release and refresh IndexSeacher to make changes searchable
			papersIndexSearcher.getSearcherManager().release(isearch);
			papersIndexSearcher.getSearcherManager().maybeRefresh();

		}catch(Exception e){
			throw e;
		}
	}

	/**
	 * Update documents citations by adding nodes and
	 * edges to Neo4j graph and updating citCount field
	 * @param doc referenced document to process
	 * @throws Exception 
	 */
	private long addCitation(Document doc, Bibliography bib) throws Exception {
		try {
			long citedNodeId = DatabaseHelper.createCitaton(doc, bib.getDOI(), 
					bib.getTitle(), bib.getAuthors(), bib.getPublicationDate());
			return citedNodeId;
		} catch (Exception e) {
			logger.error("Error adding citation for document: " + doc.get("file"), e);
			throw e;
		}
	}

	private void updateCitations(IndexWriter writer, List<Document> docs) throws IOException{
		for(Document doc : docs){
			String[] citeIds = doc.getValues("cite_id");
			for(String id : citeIds){
				Term idTerm = new Term("id", ""+id);
				// If the document is in the Index update its citCount field
				// Get document
				// Add 1 to avoid zero values
				long citationsCount;
				try {
					citationsCount = DatabaseHelper.getNumberOfCitations(Long.parseLong(id)) + 1L;
				} catch (Exception e) {
					logger.error("Can't update citations of node id: "+id, e);
					continue;
				}
				writer.updateDocValues(idTerm, 
						new NumericDocValuesField("citCount", citationsCount));
			}
		}

		writer.commit();
		writer.deleteUnusedFiles();
	}

	/**
	 * Parses a document and creates a {@link Document} object
	 * to be inserted into Lucene index.
	 * @param filename the full filename of the document to be parsed
	 * @return a new Document to be added to the index
	 * @throws IOException if can't parse the document
	 */
	private Document parseDocument(File filename) throws IOException 
	{
		// Apache Tika for parse document
		Metadata metadata = new Metadata();
		ContentHandler handler = new BodyContentHandler(); 
		ParseContext ctx = new ParseContext();
		Parser parser = new AutoDetectParser();
		InputStream is = new FileInputStream(filename);

		try {
			parser.parse(is, handler, metadata, ctx);
		}catch (TikaException | SAXException e){
			//TODO: handle exp
			e.printStackTrace();
		}finally {
			is.close();
		}


		// Add document filename to the Index
		Document doc = new Document();
		doc.add(new StringField("file", filename.getAbsolutePath(), Store.YES));

		try {
			// Process document using GROBID: 
			// extracts header and references information
			parseDocument(filename.getAbsolutePath(), doc);
		} catch (Exception e) {
			logger.error("Error extracting document's information with GROBID", e);
			return null;
		}

		// Extracts and add the body of document to the index 
		String body = handler.toString();
		doc.add(new TextField("body", body, Store.NO));
		return doc;
	}

	private List<Bibliography> parseReferences(File docFile, Document doc) {

		List<Bibliography> references = null;
		try {
			documentParsers[0].parseReferences(docFile.getAbsolutePath());
		} catch (Exception e1) {}

		references = documentParsers[0].getReferences(); 

		// Builds a string if all references to be stored into Index
		boolean completed = true;
		if (references != null){
			for(Bibliography bib : references){
				// Is it complete?
				if (bib.getTitle() == null || bib.getAuthors() == null || bib.getPublicationDate() == null || 
						bib.getDOI() == null){
					completed = false;
					break;
				}
			}
		}

		if(!completed || references == null){
			if (references == null) 
				references = new ArrayList<>();
			for(int i = 1; i < documentParsers.length; i++){
				final DocumentParser parser = documentParsers[i];
				try {
					parser.parseReferences(docFile.getAbsolutePath());
				} catch (Exception e) {
					continue;
				}
				for(Bibliography bib : parser.getReferences()){
					updateBib(references, bib);
				}
			}
		}

		return references.isEmpty() ? null : references;

	}

	private void updateBib(List<Bibliography> references, Bibliography bib) {
		// Calculate Levenshtein distance between references strings
		final LevensteinDistance distance = new LevensteinDistance();
		float maxDist = 0;
		Bibliography simBib = null;
		for(Bibliography b : references){
			float titleDist = 0, authorDist = 0, yearDist = 0;
			if (b.getTitle() != null && bib.getTitle() != null)
				titleDist = distance.getDistance(b.getTitle(), bib.getTitle());
			if (b.getAuthors() != null && bib.getAuthors() != null)
				authorDist = distance.getDistance(b.getAuthors(), bib.getAuthors());
			if (b.getPublicationDate() != null && bib.getPublicationDate() != null)
				yearDist = distance.getDistance(b.getPublicationDate(), bib.getPublicationDate());
			float dist = (titleDist + authorDist + yearDist) / 3;
			// If distance is greater than 90% than considers two strings as equal
			if (dist > maxDist){
				maxDist = dist;
				simBib = b;
			}	
		}

		if (maxDist > MAX_DISTANCE){
			if (simBib.getTitle() == null && bib.getTitle() != null)
				simBib.setTitle(bib.getTitle());
			if (simBib.getAuthors() == null && bib.getAuthors() != null)
				simBib.setAuthors(bib.getAuthors());
			if (simBib.getPublicationDate() == null && bib.getPublicationDate() != null)
				simBib.setPublicationDate(bib.getPublicationDate());
			if (simBib.getDOI() == null && bib.getDOI() != null)
				simBib.setDOI(bib.getDOI());
		}
		else{
			//references.add(bib);
		}
	}

	/**
	 * Process document using GROBID: extracts header and 
	 * references data.
	 * @param filename path to the document's file
	 * @param doc the {@link Document} object to add extracted terms
	 * @return 
	 * @throws Exception if any error occurs extracting data.
	 */
	private void parseDocument(String filename, Document doc) throws Exception {

		String title = null, authors = null, affiliation = null, 
				doi = null, year = null, docAbstract = null, journal = null;

		// Parse document
		for(int i = 0; i < documentParsers.length; i++){
			final DocumentParser parser = documentParsers[i];
			parser.parseHeader(filename);

			if (title == null)
				title = parser.getTitle();

			if (authors == null)
				authors = normalizeAuthors(parser.getAuthors());

			if (affiliation == null)
				affiliation = parser.getAffiliation();

			if (doi == null)
				doi = parser.getDOI();

			if (year == null)
				year = parser.getPublicationDate();

			if (docAbstract == null)
				docAbstract = parser.getAbstract();

			if (journal == null)
				journal = parser.getJournal();
		}

		if (title == null || authors == null)
			throw new Exception("Document has no title or authors");

		// Mandatory fields: title and authors
		doc.add(new StringField("title", title.toLowerCase(), Store.YES));
		doc.add(new StringField("authors", authors.toLowerCase(), Store.YES));

		// Some other useful informations
		if ( affiliation != null )
			doc.add(new StringField("affiliation", affiliation.toLowerCase(), Store.YES));
		if ( doi != null )
			doc.add(new StringField("doi", doi.toLowerCase(), Store.YES));
		if ( year != null )
			doc.add(new StringField("year", year.toLowerCase(), Store.YES));
		if ( journal != null )
			doc.add(new StringField("journal", journal.toLowerCase(), Store.YES));
		if ( docAbstract != null)
			doc.add(new TextField("abstract", docAbstract.toLowerCase(), Store.NO));
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		for(int i = 0; i < documentParsers.length; i++)
			if ( documentParsers[i] instanceof GrobIDDocumentParser){
				MockContext.destroyInitialContext();
			}
	}

	/**
	 * Normalize authors raw string: removes new lines, extra spaces and
	 * 'and' words.
	 * @param authors a string to be normalized
	 * @return a normalized string with new line, extra 
	 * white spaces and  'and' words removed.
	 */
	private String normalizeAuthors(String authors) {
		return authors.replaceAll("\n|;|\\s+and\\s+", Utils.AUTHOR_SEPARATOR).toLowerCase();
	}

	public static void main(String[] args) throws Exception {

		if ( args.length != 1){
			System.out.println("Provide the directory path where articles are located");
			return;
		}

		try {
			BufferedReader br = new BufferedReader( new FileReader("conf/application.conf"));
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();
			while (line != null){
				sb.append(line);
				line = br.readLine();
			}
			br.close();

			Configuration configuration = new Configuration(sb.toString());

			PapersIndexer indexer = new PapersIndexer(configuration, 
					new PapersIndexSearcher(configuration));
			indexer.addDocuments(args[0]);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
