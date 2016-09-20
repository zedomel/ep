package services;

import java.io.File;
import java.util.List;

import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;

import play.Configuration;
import play.Logger;

/**
 * Helper class to access GROBID functionality.
 * It provides methods to parse documents header and
 * references.
 * @author jose
 *
 */
public final class GrobIDParser {

	private final Engine engine;
	
	private static final GrobIDParser INSTANCE = new GrobIDParser();
	
	/**
	 * Creates a {@link GrobIDParser} (singleton)
	 * @throws Exception if a exception occurs when try to load
	 * GROBID.
	 */
	private GrobIDParser() {
		Configuration configuration = Configuration.reference();
		String grobidHome = configuration.getString("grobid.home", "grobid-home");
		String grobidProperties = configuration.getString("grobid.properties", "grobid-home/config/grobid.properties");
		
		try {
			MockContext.setInitialContext(grobidHome, grobidProperties);
		} catch (Exception e) {
			Logger.error("Can't create GrobIDParser.",e);
			System.exit(-1);
		}
		GrobidProperties.getInstance();
		engine = GrobidFactory.getInstance().createEngine();
	}
	
	
	public static GrobIDParser getInstance(){
		return INSTANCE;
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

}
