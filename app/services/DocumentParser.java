package services;

import java.util.List;

public interface DocumentParser {
	
	
	public void parse(String filename) throws Exception;

	public String getAuthors();
	
	public String getTitle();
	
	public String getAffiliation();
	
	public String getDOI();
	
	public String getPublicationDate();
	
	public String getAbstract();
	
	public List<Bibliography> getReferences();

}
