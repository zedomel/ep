package services;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CitationUpdater {

	private static Logger logger = LoggerFactory.getLogger(CitationUpdater.class);
	
	private String indexDir;
	
	public CitationUpdater(String indexDir) {
		super();
		this.indexDir = indexDir;
	}
	
	public void updateCitationsCount() throws IOException, SQLException{
		Directory directory = FSDirectory.open(new File(indexDir).toPath());
	
		IndexReader ireader = null;
		try {
			 ireader = DirectoryReader.open(directory);
			 Bits liveDoc = MultiFields.getLiveDocs(ireader);
			 
			 for(int i = 0; i < ireader.maxDoc(); i++){
				if (liveDoc != null && !liveDoc.get(i))
					continue;
				 
				 Document doc = ireader.document(i);
				 String citString = doc.get("citString");
				 String citDOI = doc.get("citDOI");
				 processCitation(doc, citDOI, citString);
			 }
		} catch (IOException e) {
			logger.error("Can't create index searcher object", e);
			throw e;
		}finally {
			if (ireader != null){
				ireader.close();
			}
		}
	}



	private void processCitation(Document doc, String citDOI, String citString) 
	{
		String[] citations = citString.split("\\$");	
		for(int i = 0; i < citations.length; i++){
			String[] fields = citations[i].split("\t");
			try {
				DatabaseHelper.createCitaton(doc, citDOI, fields[0], fields[1], fields[2]);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		CitationUpdater counter = new CitationUpdater("db");
		try {
			counter.updateCitationsCount();
		} catch (IOException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
