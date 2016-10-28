package services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.inject.name.Named;

import play.Configuration;
import services.parsers.GrobIDDocumentParser;

/**
 * Main class for indexing documents (papers) 
 *  using Lucene API.
 * @author jose
 *
 */
@Singleton
public class PapersIndexer {

	private static final String NULL_DOI = "NULL";

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
	 * Document Parser: GROBID or Cermine
	 */
	@Named("documentParser")
	private DocumentParser documentParser;



	/**
	 * Creates a document indexer to index documents from 
	 * a directory or specific file.
	 * @param indexDir the directory where index is located (lucene database)
	 * @throws IOException if an error occurs while initializing index
	 */
	@Inject
	public PapersIndexer(Configuration configuration, PapersIndexSearcher isearcher, 
			DocumentParser documentParser) throws IOException {
		this.indexDir = configuration.getString("luceneIndexDir", "db");
		this.papersIndexSearcher = isearcher;
		this.documentParser = documentParser;
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

			//Iterates over all documents in the directory
			Files.list(dir.toPath()).forEach( (path) -> 
			{
				try {
					// Parse document file
					Document doc = parseDocument(path.toFile());

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

						// Write document to the index
						writer.addDocument(doc);
					}

				} catch (Exception e) {
					logger.error("Error importing document: "+path.toAbsolutePath(), e);
				}
			});

			// Commit and delete unused files
			writer.commit();
			writer.deleteUnusedFiles();
			// Refresh index reader to changes be searchable
			papersIndexSearcher.getSearcherManager().maybeRefresh();

			//Now it's time to update citations

			//Acquire a IndexSearch from manager
			final IndexSearcher isearch = papersIndexSearcher.getSearcherManager().acquire();
			final IndexReader ireader = isearch.getIndexReader();
			// Get a bits representing documents in the index 
			// to trace changes in documents as we iterate over them.
			Bits liveDoc = MultiFields.getLiveDocs(ireader);
			// maxDoc() is a large number to ensure iterate 
			// over all indexed documents.
			for(int i = 0; i < ireader.maxDoc(); i++){
				// If document is not live (ready for read) skip it
				if (liveDoc != null && !liveDoc.get(i))
					continue;

				// Get document and update its citations
				Document doc = ireader.document(i);
				updateCitations(writer, isearch, doc);
			}
			// Release IndexSearcher to be used by another thread
			papersIndexSearcher.getSearcherManager().release(isearch);

			// Now commits updates
			writer.commit();
			writer.deleteUnusedFiles();
			// Refresh index reader to changes be searchable
			papersIndexSearcher.getSearcherManager().maybeRefresh();
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
			IndexSearcher isearch = papersIndexSearcher.getSearcherManager().acquire();
			Document doc = parseDocument(new File(docPath));
			if (doc != null){

				// Just initialize citCount as 1
				NumericDocValuesField citCount = new NumericDocValuesField("citCount", 1L);
				doc.add(citCount);

				// Adds node to Neo4j graph
				long nodeId = DatabaseHelper.addNode(doc.get("doi"), doc.get("title"), doc.get("authors"),
						doc.get("year"), doc.get("file"));
				// And the Neo4j id as a document's field
				doc.add(new StringField("id", ""+nodeId, Store.YES));

				// Write document to the Index
				writer.addDocument(doc);		

				// Update references citations
				updateCitations(writer, isearch, doc);

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

				// Update citations
				updateCitations(writer, isearch, isearch.doc(hits[0].doc));

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
	 */
	private void updateCitations(IndexWriter writer, IndexSearcher isearch, Document doc) {

		// First, update document citation count
		Term idTerm = new Term("id", doc.get("id"));
		try {
			long citCount = DatabaseHelper.getNumberOfCitations(doc) + 1L;
			writer.updateNumericDocValue(idTerm, "citCount", citCount);
		}catch(Exception e){
			logger.error("Error updating citation for document: "+doc.get("file"), e);
		}

		// Get citation string extracted from document
		String[] citStrings = doc.getValues("citString");
		String[] citDOIs= doc.getValues("citDOI");
		
		if (citStrings == null || citStrings.length == 0)
			return;

		// Iterate over all citation text 
		for(int i = 0; i < citStrings.length; i++){
			String[] fields = citStrings[i].split("\t");
			String doi = citDOIs[i];
			try {

				// Create citation (edge on Neo4j graph) if not exist
				if (NULL_DOI.equals(doi))
					doi = null;
				String year = null;
				if (fields.length == 3)
					year = fields[2];
				long citedNodeId = DatabaseHelper.createCitaton(doc, doi, fields[0], fields[1], year);

				idTerm = new Term("id", ""+citedNodeId);
				TermQuery query = new TermQuery(idTerm);

				// Searches for cited document in the Index
				ScoreDoc[] hits = isearch.search(query, 1).scoreDocs;

				// If the document is in the Index update its citCount field
				if (hits.length > 0){
					// Get document
					Document citedDoc = isearch.doc(hits[0].doc);
					// Add 1 to avoid zero values
					long citationsCount = DatabaseHelper.getNumberOfCitations(citedDoc) + 1L;
					// Get document current citation count (lucene)
					Number docCitCount = doc.getField("citCount").numericValue();
					// Only updates with citations count have changed or is null
					if (docCitCount == null || citationsCount > docCitCount.longValue())
						writer.updateDocValues(idTerm, 
								new NumericDocValuesField("citCount", citationsCount));
				}
			} catch (Exception e) {
				logger.error("Error updating citation for document: "+doc.get("file") + " citString: "+citStrings[i], e);
			}
		}
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

	/**
	 * Process document using GROBID: extracts header and 
	 * references data.
	 * @param filename path to the document's file
	 * @param doc the {@link Document} object to add extracted terms
	 * @throws Exception if any error occurs extracting data.
	 */
	private void parseDocument(String filename, Document doc) throws Exception {

		// Parse document
		documentParser.parse(filename);

		// If document has not title or authors it can't be add to 
		// the index: is it really a scientific publication?
		// TODO: remove authors from mandatory fields
		if (documentParser.getTitle() == null || documentParser.getAuthors() == null )
			throw new Exception("Document has no title or authors");

		// Mandatory fields: title and authors
		doc.add(new StringField("title", documentParser.getTitle().toLowerCase(), Store.YES));

		//TODO: check for existence
		doc.add(new StringField("authors", normalizeAuthors(documentParser.getAuthors()), Store.YES));

		// Some other useful informations
		if ( documentParser.getAffiliation() != null )
			doc.add(new StringField("affiliation", documentParser.getAffiliation().toLowerCase(), Store.YES));
		if ( documentParser.getDOI() != null )
			doc.add(new StringField("doi", documentParser.getDOI(), Store.YES));
		if ( documentParser.getPublicationDate() != null )
			doc.add(new StringField("year", ""+documentParser.getPublicationDate().toLowerCase(), Store.YES));
		if ( documentParser.getAbstract() != null)
			doc.add(new TextField("abstract", documentParser.getAbstract().toLowerCase(), Store.NO));

		// Parse document references
		List<Bibliography> refs = documentParser.getReferences();

		// Builds a string if all references to be stored into Index
		
		for (Bibliography bib : refs){
			StringBuilder citString = new StringBuilder();
			// Is is a complete references?
			if (bib.getTitle() != null && bib.getAuthors() != null){
				// Append fields separated by tab (\t)
				citString.append(bib.getTitle().toLowerCase()).append("\t")
				.append(normalizeAuthors(bib.getAuthors()));

				// Has publication data? If so, get just the year.
				if (bib.getPublicationDate() != null)
					citString.append("\t").append(bib.getPublicationDate().toLowerCase());

				// Adds citation string to the document
				if (citString.length() > 0)
					doc.add(new TextField("citString", citString.toString(), Store.YES));

				if (bib.getDOI() != null )
					doc.add(new StringField("citDOI", bib.getDOI(), Store.YES));
				else
					doc.add(new StringField("citDOI", NULL_DOI, Store.YES));
			}
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
			String grobidHome = configuration.getString("grobid.home", "grobid-home");
			String grobidProperties = configuration.getString("grobid.properties", "grobid-home/config/grobid.properties");

			try {
				MockContext.setInitialContext(grobidHome, grobidProperties);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
			GrobidProperties.getInstance();	

			PapersIndexer indexer = new PapersIndexer(configuration, 
					new PapersIndexSearcher(configuration), new GrobIDDocumentParser());
			indexer.addDocuments(args[0]);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
