package services.search;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.carrot2.core.Controller;
import org.carrot2.core.ControllerFactory;
import org.carrot2.core.Document;
import org.carrot2.core.ProcessingResult;
import org.carrot2.core.attribute.AttributeNames;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import services.clustering.DistanceMeasure;
import services.clustering.EuclideanDistance;
import services.clustering.KMedoidClusteringAlgorithm;
import services.clustering.SearchProcessing;

public class MicrosoftAcademicSearcher implements DocumentSearcher {


	private static final String EVALUATE_URL = "https://api.projectoxford.ai/academic/v1.0/evaluate";

	private final Controller controller;

	private int numClusters = 10;
	
	private final String ocpKey;

	private DistanceMeasure distanceMeasure = new EuclideanDistance();

	public MicrosoftAcademicSearcher() {
		this.controller = ControllerFactory.createPooling();
		Config config = ConfigFactory.load();
		this.ocpKey = config.getString("Ocp-Apim-Subscription-Key");
	}
	
	public String getExpression(String text){
		StringBuilder sb = new StringBuilder("Or(");
		for(String str : text.split("\\s+"))
			sb.append(String.format("W='%s',", str));
		sb.append(String.format("Composite(AA.AuN='%s'))", text));
		return sb.toString();
	}

	public String search(String query) throws Exception {
		return search(query, 100);
	}
	
	public String search(String query, boolean fetchNumberOfCitations) throws Exception {
		return search(query);
	}

	public String search(String query, boolean fetchNumberOfCitations, int count) throws Exception {
		return search(query, count);
	}
	public String search(String query, int count) throws Exception {
		if (query == null || query.isEmpty() )
			return null;

		final HttpClientBuilder httpBuilder = HttpClientBuilder.create();

		try ( CloseableHttpClient httpclient = httpBuilder.build(); ) {

			URIBuilder builder = new URIBuilder(EVALUATE_URL);

			final String expr = getExpression(query);
			builder.setParameter("expr",expr);
			builder.setParameter("count", ""+count);
			builder.setParameter("attributes", "Id,Ti,CC,AA.AuN,W,Y,F.FN,J.JN,C.CN,E");

			URI uri = builder.build();
			HttpGet request = new HttpGet(uri);
			request.setHeader("Ocp-Apim-Subscription-Key", ocpKey);

			HttpResponse response = httpclient.execute(request);
			HttpEntity entity = response.getEntity();

			if (entity != null) 
			{
				List<Long> paperIDs = new ArrayList<>();
				List<Document> documents = parseJSON( EntityUtils.toString(entity), paperIDs );
				
				MicrosoftAcademicGraphSearcher graphSearcher = new MicrosoftAcademicGraphSearcher();
				Map<Long, List<Long>> citations = graphSearcher.searchCitations(paperIDs);
				if ( !citations.isEmpty() ){
					for( Document doc : documents ){
						long id = doc.getField("id");
						List<Long> references = citations.get(id);
						if ( references != null ){
							doc.setField("references", references);
						}
					}
				}
				
				return clustering(documents);
				
			}
			return null;
		}
		catch (Exception e)
		{
			throw e;
		}
	}

	public List<Document> parseJSON(String content, List<Long> paperIDs) throws IOException{
		ObjectMapper mapper = new ObjectMapper();
		try {
			Map<?,?> map = mapper.readValue(content, Map.class);
			Object obj = map.get("entities");
			if ( obj != null ){
				List<?> values = (List<?>) obj;
				List<Document> documents = new ArrayList<>(values.size());
				for(Object value : values){
					Map<?,?> valueMap = (Map<?,?>) value;
					String title = (String) valueMap.get("Ti");
					String summary = "";
					obj = valueMap.get("W");
					if (obj != null){
						List<String> words = (List<String>) obj;
						for(String word : words)
							summary += word + ",";
					}
					
					Document doc = new Document( title, summary );
					Double score = (Double) valueMap.get("logprob");
					doc.setScore(score);
					doc.setField("relevance", score);
					doc.setField("numCitations", valueMap.get("CC"));
					Number id = (Number) valueMap.get("Id");
					doc.setField("id", id.longValue());
					paperIDs.add(id.longValue());
					
					if ( valueMap.containsKey("AA")){
						List<Map<?,?>> authors = (List<Map<?,?>>) valueMap.get("AA");
						String str = (String) authors.get(0).get("AuN");
						for(int i = 1; i < authors.size(); i++){
							str += ", " + (String) authors.get(i).get("AuN");
						}
						doc.setField("authors", str);
					}
					if ( valueMap.containsKey("Y") )
						doc.setField("year", valueMap.get("Y"));
					if ( valueMap.containsKey("C") )
						doc.setField("conference", ((Map<?,?>)valueMap.get("C")).get("CN"));
					if ( valueMap.containsKey("J") )
						doc.setField("journal", ((Map<?,?>)valueMap.get("J")).get("JN"));
					if ( valueMap.containsKey("F") ){
						List<Map<?,?>> fields = (List<Map<?,?>>) valueMap.get("F");
						String str = (String) fields.get(0).get("FN");
						for(int i = 1; i < fields.size(); i++)
							str += ", " + (String) fields.get(i).get("FN");
						doc.setField("fields", str);
					}
					if ( valueMap.containsKey("E") ){
						Map<?,?> metadata = mapper.readValue((String)valueMap.get("E"), Map.class);
						doc.setField("full_title", metadata.get("DN"));
						doc.setField("abstract", metadata.get("D"));
						if ( metadata.containsKey("S") ){
							String url = (String) ((List<Map<?,?>>)metadata.get("S")).get(0).get("U");
							doc.setField("url", url);
						}
						if( metadata.containsKey("DOI") )
							doc.setField("doi", metadata.get("DOI"));
					}
					
					
					documents.add(doc);
				}
				return documents;
			}
		} catch (IOException e) {
			throw e;
		}
		return null;
	}

	public String clustering(List<Document> documents) throws IOException{
		
		Map<String,Object> attributes = new HashMap<>();
		attributes.put(AttributeNames.DOCUMENTS, documents);
		attributes.put(KMedoidClusteringAlgorithm.NUM_CLUSTERS, numClusters);
		attributes.put(KMedoidClusteringAlgorithm.DISTANCE_MEASURE, distanceMeasure );
		attributes.put(KMedoidClusteringAlgorithm.MAX_ITERATIONS, 50);
		attributes.put(SearchProcessing.NUM_NEIGHBORS, 10);

		ProcessingResult results = controller.process(attributes, SearchProcessing.class);

		// Serialize result as JSON
		StringWriter writer = new StringWriter();
		results.serializeJson(writer);

		return writer.toString();
	}

	public static void main(String[] args) {

		MicrosoftAcademicSearcher search = new MicrosoftAcademicSearcher();

		try {
			String json = search.search("jaime teevan");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
