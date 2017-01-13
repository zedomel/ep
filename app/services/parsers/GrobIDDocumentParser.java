package services.parsers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.Person;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.GrobidPropertyKeys;

import play.Configuration;
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

	private static Engine ENGINE;
	
	private final Engine engine;

	private BiblioItem metadata;

	private List<BibDataSet> references;

	/**
	 * Creates a {@link GrobIDDocumentParser} (singleton)
	 * @throws Exception if a exception occurs when try to load
	 * GROBID.
	 */
	public GrobIDDocumentParser() throws Exception {
		initialize();
		engine = ENGINE;
	}

	private void initialize() throws Exception {
		InitialContext ic = new InitialContext();
		try{
			ic.lookup("java:comp/env/" + GrobidPropertyKeys.PROP_GROBID_HOME);
		}catch(NamingException e){
			Configuration configuration = Configuration.reference();
			String grobidHome = configuration.getString("grobid.home", "grobid-home");
			String grobidProperties = configuration.getString("grobid.properties", "grobid-home/config/grobid.properties");

			try {
				MockContext.setInitialContext(grobidHome, grobidProperties);
			} catch (Exception e1) {
				throw e1;
			}
			GrobidProperties.getInstance();	
			ENGINE = GrobidFactory.getInstance().createEngine();
		}
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
	 */
	@Override
	public void parseHeader(String filename) {
		metadata = new BiblioItem();
		engine.processHeader(filename, false, metadata);
	}

	/**
	 * Parse document references.
	 * @param filename document filename
	 */
	@Override
	public void parseReferences(String filename) {
		references = engine.processReferences(new File(filename), false);
	}

	@Override
	public String getAuthors() {
		if (metadata.getFullAuthors() != null){
			StringBuilder sb = new StringBuilder();
			for(Person p : metadata.getFullAuthors()){
				sb.append(Utils.sanitize(p.getFirstName()));
				sb.append(" ");
				sb.append(Utils.sanitize(p.getMiddleName()));
				sb.append(" ");
				sb.append(Utils.sanitize(p.getLastName()));
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
	public String getJournal() {
		return metadata.getJournal();
	}

	@Override
	public List<Bibliography> getReferences() {
		List<Bibliography> refs = new ArrayList<>(references.size());
		for(BibDataSet bds : references){
			BiblioItem item = bds.getResBib();
			Bibliography bib = new Bibliography();
			String str = Utils.normalizePerson(item.getFullAuthors());
			bib.setAuthors(str != null ? str.toLowerCase() : null);
			bib.setTitle(item.getTitle() != null ? item.getTitle().toLowerCase() : null);
			bib.setDOI(item.getDOI() != null ? item.getDOI().toLowerCase() : null);
			bib.setJournal(item.getJournal() != null ? item.getJournal().toLowerCase() : null);
			bib.setPublicationDate( Utils.sanitizeYear(item.getPublicationDate() == null ? 
					item.getYear() : item.getPublicationDate()));
			refs.add(bib);
		}
		return refs;
	}

}
