package services.search;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class MicrosoftAcademicGraphSearcher {
	
	
	private static final String GRAPH_SEARCH_URL = "https://api.projectoxford.ai/academic/v1.0/graph/search";
	
	private final String ocpKey;

	public MicrosoftAcademicGraphSearcher() {
		Config config = ConfigFactory.load();
		this.ocpKey = config.getString("Ocp-Apim-Subscription-Key");
	}
	
	public Map<Long, List<Long>> searchCitations(List<Long> paperIDs) throws Exception{
		if (paperIDs == null || paperIDs.size() == 0 )
			return null;

		final HttpClientBuilder httpBuilder = HttpClientBuilder.create();

		try ( CloseableHttpClient httpclient = httpBuilder.build(); ) {

			URIBuilder builder = new URIBuilder(GRAPH_SEARCH_URL);

			builder.setParameter("mode","json");

			URI uri = builder.build();
			HttpPost request = new HttpPost(uri);
			request.setHeader("Content-Type", "application/json");
			request.setHeader("Ocp-Apim-Subscription-Key", ocpKey);

			
			// Request body
			Map<String, Object> body = new HashMap<>();
			body.put("path", "/source/CitationIDs/target");
			
			Map<String, Object> aux = new HashMap();
			aux.put("type", "Paper");
			aux.put("id", paperIDs);
			
			body.put("source", aux);
			body.put("target", aux);
			
			ObjectMapper mapper = new ObjectMapper();
			String jsonBody = mapper.writeValueAsString(body);
			
			StringEntity reqEntity = new StringEntity(jsonBody);
			request.setEntity(reqEntity);
			
			HttpResponse response = httpclient.execute(request);
			HttpEntity entity = response.getEntity();

			if (entity != null) 
			{
				return parseCitationsJSON( EntityUtils.toString(entity) );
			}
			return null;
		}
		catch (Exception e)
		{
			throw e;
		}
	}

	private Map<Long, List<Long>> parseCitationsJSON(String response) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		try {
			Map<Long, List<Long>> citations = new HashMap<>();
			Map<?,?> map = mapper.readValue(response, Map.class);
			List<List<?>> results = (List<List<?>>) map.get("Results");
			for(List<?> value : results){
				long targetId = ((Number) ((Map<?,?>) value.get(0)).get("CellID")).longValue();
				long sourceId = ((Number) ((Map<?,?>) value.get(1)).get("CellID")).longValue();
				List<Long> references = citations.get(sourceId);
				if ( references == null ){
					references = new ArrayList<>();
					citations.put(sourceId, references);
				}
				references.add(targetId);
			}
			
			return citations;
		}catch (Exception e) {
			throw e;
		}
	}
	
	public static void main(String[] args) {
		MicrosoftAcademicGraphSearcher searcher = new MicrosoftAcademicGraphSearcher();
		
		List<Long> ids = new ArrayList<>();
		ids.add(2122841972L);
		ids.add(2157025439L);
		ids.add(2061503185L);
		
		try {
			searcher.searchCitations(ids);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
