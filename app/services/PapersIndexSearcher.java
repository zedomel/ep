package services;

import java.io.File;
import java.io.IOException;
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

import model.ResultDocument;
import play.Configuration;

@Singleton
public class PapersIndexSearcher {


	private SearcherManager  mgr;
	private final Directory directory;

	@Inject
	public PapersIndexSearcher(Configuration configuration) throws IOException {
		String indexDir = configuration.getString("luceneIndexDir", "db");
		directory = NIOFSDirectory.open(new File(indexDir).toPath());
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

	public ResultDocument[] search(String term, boolean fetchNumberOfCitations) throws IOException{
		if (term == null || term.isEmpty())
			return null;
		
		List<String> terms = getListOfTerms(term);
		ScoreDoc[] hits = null;
		
		if (terms.size() > 1){
			try {
				hits = searchForTerms(terms);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		else {
			Query query;
			String t = terms.get(0);
			if (t.matches("^\".*\"$")){
				PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
				for(String s : t.split("\\s+"))
					phraseBuilder.add(new Term("body", s));
				query = phraseBuilder.build();
			}
			else
			 query = new TermQuery(new Term("body", term));
		
			IndexSearcher isearch = getSearcherManager().acquire();
			try {
				hits = isearch.search(query,100).scoreDocs;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			getSearcherManager().release(isearch);
		}

		if (hits != null){
			IndexSearcher isearch = getSearcherManager().acquire();
			ResultDocument[] docs = new ResultDocument[hits.length];
			for( int i = 0; i < hits.length; i++){
				ResultDocument rdoc = new ResultDocument();
				Document doc = isearch.doc(hits[i].doc);
				rdoc.setScore(hits[i].score);
				rdoc.setTitle(doc.get("title"));
				rdoc.setAuthors(doc.get("authors"));
				rdoc.setKeywords(doc.get("keyword"));
				rdoc.setReferences(doc.get("citString"));
				rdoc.setFilename(doc.get("file"));
				if (fetchNumberOfCitations)
					try {
						rdoc.setNumberOfCitations(DatabaseHelper.getNumberOfCitations(doc));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				docs[i] = rdoc;
			}
			getSearcherManager().release(isearch);
			return docs;
		}
		return null;
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

	private ScoreDoc[] searchForTerms(List<String> terms) throws IOException {
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
		IndexSearcher isearcher = getSearcherManager().acquire();
		ScoreDoc[] hits = isearcher.search(builder.build(), 100).scoreDocs;
		getSearcherManager().release(isearcher);
		return hits;
		
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
