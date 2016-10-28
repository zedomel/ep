package services.parsers;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import pl.edu.icm.cermine.ContentExtractor;
import pl.edu.icm.cermine.bibref.model.BibEntry;
import pl.edu.icm.cermine.metadata.model.DocumentAffiliation;
import pl.edu.icm.cermine.metadata.model.DocumentAuthor;
import pl.edu.icm.cermine.metadata.model.DocumentDate;
import pl.edu.icm.cermine.metadata.model.DocumentMetadata;
import services.Bibliography;
import services.DocumentParser;
import services.Utils;

public class CermineDocumentParser implements DocumentParser{


	private DocumentMetadata metadata;

	private List<BibEntry> references;

	public CermineDocumentParser() {

	}

	public void parse(String documentFile) throws Exception{
		ContentExtractor extractor = new ContentExtractor();
		InputStream input = new FileInputStream(documentFile);
		extractor.setPDF(input);		
		metadata = extractor.getMetadata();
		references = extractor.getReferences();

	}

	@Override
	public String getAuthors() {
		StringBuilder sb = new StringBuilder();
		if (metadata.getAuthors() != null){
			for (DocumentAuthor author : metadata.getAuthors()){
				sb.append(author.getName());
				sb.append(";");
			}
			sb.replace(sb.length()-1, sb.length(), "");
			return sb.toString();
		}
		return null;
	}

	@Override
	public String getTitle() {
		return metadata.getTitle();
	}

	@Override
	public String getAffiliation() {
		StringBuilder sb = new StringBuilder();
		for (DocumentAffiliation aff : metadata.getAffiliations()){
			sb.append(aff.getOrganization()+"-"+aff.getCountry());
			sb.append(";");
		}
		sb.replace(sb.length()-1, sb.length(), "");
		return sb.toString();
	}

	@Override
	public String getDOI() {
		return metadata.getId(DocumentMetadata.ID_DOI);
	}

	@Override
	public String getPublicationDate() {
		DocumentDate date = metadata.getDate(DocumentDate.DATE_PUBLISHED);
		return date.getDay() + "/"+date.getMonth()+ "/"+date.getYear();
	}

	@Override
	public String getAbstract() {
		return metadata.getAbstrakt();
	}

	public List<Bibliography> getReferences(){
		List<Bibliography> refs = new ArrayList<>(references.size());
		for(BibEntry entry : references){
			Bibliography bib = new Bibliography();
			bib.setTitle(entry.getFirstFieldValue(BibEntry.FIELD_TITLE));
			bib.setDOI(entry.getFirstFieldValue(BibEntry.FIELD_DOI));
			bib.setAuthors(Utils.normalizeAuthors(entry.getAllFieldValues(BibEntry.FIELD_AUTHOR)));
			bib.setPublicationDate(entry.getFirstFieldValue(BibEntry.FIELD_YEAR));
			refs.add(bib);
		}
		return refs;
	}
}
