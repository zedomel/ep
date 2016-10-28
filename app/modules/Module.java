package modules;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import play.Configuration;
import play.Environment;
import services.DocumentParser;

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
			
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}