package services;

import java.util.List;

public interface DocumentParser {
	
	
	public void parseHeader(String filename) throws Exception;
	
	public void parseReferences(String filename) throws Exception;

	public String getAuthors();
	
	public String getTitle();
	
	public String getAffiliation();
	
	public String getDOI();
	
	public String getPublicationDate();
	
	public String getAbstract();
	
	public String getJournal();
	
	public List<Bibliography> getReferences();
}
