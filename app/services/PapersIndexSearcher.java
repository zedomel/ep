package services;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.carrot2.clustering.lingo.LingoClusteringAlgorithm;
import org.carrot2.core.Controller;
import org.carrot2.core.ControllerFactory;
import org.carrot2.core.ProcessingResult;

import play.Configuration;

@Singleton
public class PapersIndexSearcher {


	private SearcherManager  mgr;

	private final Directory directory;

	private Controller controller;

	@Inject
	public PapersIndexSearcher(Configuration configuration) throws IOException {
		String indexDir = configuration.getString("luceneIndexDir", "db");
		directory = NIOFSDirectory.open(new File(indexDir).toPath());
		controller = ControllerFactory.createPooling();
	}

	public SearcherManager getSearcherManager(){
		if (mgr == null){
			try {
				this.mgr = new SearcherManager(directory, new SearcherFactory() {
					@Override
					public IndexSearcher newSearcher(IndexReader reader, IndexReader previousReader) throws IOException {
						IndexSearcher isearch = new IndexSearcher(reader);
						isearch.setSimilarity(new CitationSimilarity(IndexSearcher.getDefaultSimilarity()));
						return isearch;
					}
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return mgr;
	}

	public String search(String term, boolean fetchNumberOfCitations) throws IOException{
		if (term == null || term.isEmpty())
			return null;

		List<String> terms = getListOfTerms(term);
		ScoreDoc[] hits = null;
		Query query;

		if (terms.size() > 1){
			try {
				query = buildBooleanQuery(terms);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
		else {
			query = buildSimpleQuery(terms);
		}

		IndexSearcher isearch = getSearcherManager().acquire();
		try {
			hits = isearch.search(query,100).scoreDocs;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		if (hits != null){
			// Preparing for clustering
			List<org.carrot2.core.Document> docsForClustering = prepareDocumentsForClustering(isearch, hits, fetchNumberOfCitations);

			//Release Index Searcher
			getSearcherManager().release(isearch);

			//Now perform clustering
			ProcessingResult results = controller.process(docsForClustering, null, LingoClusteringAlgorithm.class);

			// Serialize reusult as JSON
			StringWriter writer = new StringWriter();
			results.serializeJson(writer);

			return writer.toString();
		}

		getSearcherManager().release(isearch);
		return null;
	}


	private List<org.carrot2.core.Document> prepareDocumentsForClustering(IndexSearcher isearch, ScoreDoc[] hits, 
			boolean fetchNumberOfCitations) {
		List<org.carrot2.core.Document> docsForClustering = new ArrayList<>();
		for( int i = 0; i < hits.length; i++){
			Document doc;
			try {
				doc = isearch.doc(hits[i].doc);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				continue;
			}
			org.carrot2.core.Document docForClustering = new org.carrot2.core.Document();
			docForClustering.setContentUrl(new File(doc.get("file")).toURI().toString());
			docForClustering.setTitle(doc.get("title"));
			docForClustering.setField("authors", doc.get("authors"));
			docForClustering.setField("keyword", doc.get("keyword"));
			docForClustering.setField("citString", doc.getValues("citString"));
			docForClustering.setField("relevance", hits[i].score );
			docForClustering.setScore((double) hits[i].score);

			if (fetchNumberOfCitations)
				try {
					docForClustering.setField("numCitations", DatabaseHelper.getNumberOfCitations(doc));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			docsForClustering.add(docForClustering);
		}
		return docsForClustering;
	}

	private List<String> getListOfTerms(String term) {
		List<String> matchList = new ArrayList<String>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(term);
		while (regexMatcher.find()) {
			if (regexMatcher.group(1) != null) {
				// Add double-quoted string without the quotes
				matchList.add(regexMatcher.group(1));
			} else if (regexMatcher.group(2) != null) {
				// Add single-quoted string without the quotes
				matchList.add(regexMatcher.group(2));
			} else {
				// Add unquoted word
				matchList.add(regexMatcher.group());
			}
		}
		return matchList;
	}


	private Query buildSimpleQuery(List<String> terms) {
		Query query;
		String t = terms.get(0);
		if (t.matches("^\".*\"$")){
			PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
			for(String s : t.split("\\s+"))
				phraseBuilder.add(new Term("body", s));
			query = phraseBuilder.build();
		}
		else
			query = new TermQuery(new Term("body", t));

		return query;
	}

	private Query buildBooleanQuery(List<String> terms) throws IOException {
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		for(int i = 0; i < terms.size(); i++){
			String t = terms.get(i);
			Query q;
			if (t.matches("^\".*\"$")){
				PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
				for(String s : t.split("\\s+"))
					phraseBuilder.add(new Term("body", s));
				builder.add(phraseBuilder.build(), Occur.MUST);
			}
			else {
				q = new TermQuery(new Term("body", t));
				builder.add(q, Occur.SHOULD);
			}
		}
		return builder.build();
	}

	public IndexReader openIndexReader() throws IOException{
		return DirectoryReader.open(directory);
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		getSearcherManager().close();
	}

	public IndexSearcher getIndexSearcher() throws IOException{
		return getSearcherManager().acquire();
	}
}
