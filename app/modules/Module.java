package modules;

import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import play.Configuration;
import play.Environment;
import services.DocumentParser;
import services.parsers.GrobIDDocumentParser;

public class Module extends AbstractModule {

	private final Environment environment;
	private final Configuration configuration;

	public Module(Environment environment,
			Configuration configuration) {
		this.environment = environment;
		this.configuration = configuration;
	}


	protected void configure() {        
		// Expect configuration like:
		// documentParser = "services.parsers.GROBIDDocumentParser"
		// documentParser = "services.parsers.CermineDocumentParser"
		String bindingClassName = configuration.getString("documentParser");
		try {
			Class<? extends DocumentParser> bindingClass =
					environment.classLoader().loadClass(bindingClassName)
					.asSubclass(DocumentParser.class);
			
			bind(DocumentParser.class)
			.annotatedWith(Names.named("documentParser"))
			.to(bindingClass);
			System.out.println(bindingClass.getName());
			
			if (bindingClass.equals(GrobIDDocumentParser.class))
				initializeGROBID();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void initializeGROBID() throws Exception {
		Configuration configuration = Configuration.reference();
		String grobidHome = configuration.getString("grobid.home", "grobid-home");
		String grobidProperties = configuration.getString("grobid.properties", "grobid-home/config/grobid.properties");
		
		try {
			MockContext.destroyInitialContext();
			MockContext.setInitialContext(grobidHome, grobidProperties);
		} catch (Exception e) {
			throw e;
		}
		GrobidProperties.getInstance();	
	}
}