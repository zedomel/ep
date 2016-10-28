package services.parsers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.Person;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;

import services.Bibliography;
import services.DocumentParser;
import services.Utils;

/**
 * Helper class to access GROBID functionality.
 * It provides methods to parse documents header and
 * references.
 * @author jose
 *
 */
public final class GrobIDDocumentParser implements DocumentParser{

	private final Engine engine;

	private BiblioItem metadata;

	private List<BibDataSet> references;
	
	/**
	 * Creates a {@link GrobIDDocumentParser} (singleton)
	 * @throws Exception if a exception occurs when try to load
	 * GROBID.
	 */
	public GrobIDDocumentParser() {
		engine = GrobidFactory.getInstance().createEngine();
	}
	
	/**
	 * Get GROBID engine.
	 * @return grobid engine
	 */
	public Engine getEngine() {
		return engine;
	}

	/**
	 * Parse document header
	 * @param filename document filename
	 * @return a {@link BiblioItem} with 
	 * extracted header information.
	 */
	public BiblioItem parseHeader(String filename) {
		BiblioItem bibText = new BiblioItem();
		engine.processHeader(filename, false, bibText);
		return bibText;
	}

	/**
	 * Parse document references.
	 * @param filename document filename
	 * @return a {@link List} with {@link BibDataSet} of
	 * references.
	 */
	public List<BibDataSet> parseReferences(String filename) {
		return engine.processReferences(new File(filename), false);				
	}

	@Override
	public void parse(String filename) throws Exception {
		metadata = new BiblioItem();
		engine.processHeader(filename, false, metadata);
		references = engine.processReferences(new File(filename), false);
	}

	@Override
	public String getAuthors() {
		if (metadata.getFullAuthors() != null){
			StringBuilder sb = new StringBuilder();
			for(Person p : metadata.getFullAuthors()){
				sb.append(p.getFirstName());
				sb.append(" ");
				sb.append(p.getMiddleName());
				sb.append(" ");
				sb.append(p.getLastName());
				sb.append(Utils.AUTHOR_SEPARATOR);
			}
			sb.replace(sb.length()-1, sb.length(), "");
			return sb.toString();
		}
		return metadata.getAuthors();
	}

	@Override
	public String getTitle() {
		return metadata.getTitle();
	}

	@Override
	public String getAffiliation() {
		return metadata.getAffiliation();
	}

	@Override
	public String getDOI() {
		return metadata.getDOI();
	}

	@Override
	public String getPublicationDate() {
		return metadata.getPublicationDate() == null ? metadata.getYear() : 
			metadata.getPublicationDate();
	}

	@Override
	public String getAbstract() {
		return metadata.getAbstract();
	}

	@Override
	public List<Bibliography> getReferences() {
		List<Bibliography> refs = new ArrayList<>(references.size());
		for(BibDataSet bds : references){
			BiblioItem item = bds.getResBib();
			Bibliography bib = new Bibliography();
			bib.setAuthors(item.getAuthors());
			bib.setTitle(item.getTitle());
			bib.setDOI(item.getDOI());
			bib.setPublicationDate( item.getPublicationDate() == null ? item.getYear() : item.getPublicationDate());
			refs.add(bib);
		}
		return refs;
	}

}
