package hu.unideb.inf.bd.search;

import java.io.IOException;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;

import hu.unideb.inf.bd.model.Book;
import hu.unideb.inf.bd.model.SearchResults;

import hu.unideb.inf.bd.parser.BookParser;
import hu.unideb.inf.bd.parser.SearchResultsParser;

import hu.unideb.inf.jaxb.JAXBUtil;

public class IsbnSearch extends SearchResultsParser {

	private static final String SEARCH_URI = "http://www.bookdepository.com/search";

	public IsbnSearch() {
		super(1);
	}

	public Book doSearch(String isbn) throws IOException {
		Document doc = Jsoup.connect(SEARCH_URI).userAgent("Mozilla").data("searchIsbn", isbn).data("advanced", "true").get();
		if (! doc.select("div.content:containsOwn(Your search did not return any results.)").isEmpty())
			return null;
		Book book = new BookParser().parse(doc);
		book.setUri(doc.location());
		return book;
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.printf("Usage: java %s <isbn>\n", IsbnSearch.class.getName());
			System.exit(1);
		}
		try {
			Book book = new IsbnSearch().doSearch(args[0]);
			if (book != null)
				JAXBUtil.toXML(book, System.out);
			else
				System.out.println("NOT FOUND");
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

}
