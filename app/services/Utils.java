package services;

import java.util.List;

public final class Utils {
	
	
	public static final String AUTHOR_SEPARATOR = ";";
	

	public static String normalizeAuthors(List<String> authors){
		if (authors.isEmpty())
			return null;
		StringBuilder sb = new StringBuilder();
		for(String author : authors){
			sb.append(author);
			sb.append(AUTHOR_SEPARATOR);
		}
		sb.replace(sb.length()-1, sb.length(), "");
		return sb.toString();
	}

}
