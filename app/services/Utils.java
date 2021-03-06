package services;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.grobid.core.data.Person;

public final class Utils {


	public static final String AUTHOR_SEPARATOR = ";";
	
	private static final Pattern YEAR_PATTERN = Pattern.compile("\\w*(\\d{4})\\w*");


	public static String normalizeAuthors(List<String> authors){
		if (authors.isEmpty())
			return null;
		StringBuilder sb = new StringBuilder();
		for(String author : authors){
			sb.append(sanitize(author));
			sb.append(AUTHOR_SEPARATOR);
		}
		sb.replace(sb.length()-1, sb.length(), "");
		return sb.toString();
	}

	public static String sanitize(String text){
		if (text != null)
			return text.replaceAll("[,|;|\\.|-]", "");
		return "";
	}

	public static String sanitizeYear(String year) {
		if (year == null)
			return null;
		Matcher m = YEAR_PATTERN.matcher(year);
		if (m.matches())
			return m.group(1);
		return year;
	}

	public static String normalizePerson(List<Person> authors) {
		
		if (authors == null)
			return null;
		
		StringBuilder sb = new StringBuilder();
		for(Person p : authors){
			sb.append(Utils.sanitize(p.getLastName()));
			sb.append(" ");
			sb.append(Utils.sanitize(p.getFirstName()));
			sb.append(" ");
			sb.append(Utils.sanitize(p.getMiddleName()));
			sb.append(Utils.AUTHOR_SEPARATOR);
		}
		sb.replace(sb.length()-1, sb.length(), "");
		return sb.toString().replaceAll("\\s+;", ";").replaceAll("\\s{2,}", "");
	}

}
