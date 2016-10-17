package controllers;

import java.io.IOException;

import javax.inject.Inject;

import model.ResultDocument;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.routing.JavaScriptReverseRouter;
import services.PapersIndexSearcher;
import services.PapersIndexer;
import views.html.index;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
	
	/**
	 * IndexSearcher for documents in database
	 */
	private final PapersIndexSearcher isearch;
	
	private final PapersIndexer indexer;
	
	/**
	 * FormFactory injected
	 */
	private final FormFactory formFactory;
	
	@Inject
	public HomeController(PapersIndexSearcher isearch, FormFactory formFactory, PapersIndexer indexer) {
		this.isearch = isearch;
		this.formFactory = formFactory;
		this.indexer = indexer;
	}

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok(index.render());
    }
   
    /**
     * Searches for user request term. 
     * @param term the search term
     * @return Play result as Json
     */
    public Result search(String term){
    	ResultDocument[] docs;
		try {
			docs = isearch.search(term, false);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return internalServerError("Can't search for documents");
		}
    	return ok(Json.toJson(docs)).as("application/json");
    }
    
    
    public Result addDocument(String directory){
    	try {
			indexer.addDocuments(directory);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return ok(index.render());
    }

    /**
     * Create javascript routes for this controller
     * @return javascrit route
     */
    public Result javascriptRoutes(){
    	return ok(
    			JavaScriptReverseRouter.create("jsRoutes",
    					routes.javascript.HomeController.search()
    				)).as("text/javascript");
    }
}
