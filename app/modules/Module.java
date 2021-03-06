package modules;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import play.Configuration;
import play.Environment;
import services.search.DocumentSearcher;

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
		// documentSearcher = services.search.MicrosoftAcademicSearcher
		// documentSearcher = services.search.PapersIndexSearcher
		String bindingClassName = configuration.getString("documentSearcher");
		try {
			Class<? extends DocumentSearcher> bindingClass =
					environment.classLoader().loadClass(bindingClassName)
					.asSubclass(DocumentSearcher.class);
			
			bind(DocumentSearcher.class)
			.annotatedWith(Names.named("docSearcher"))
			.to(bindingClass);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}