package hu.unideb.inf.bd.parser;

import java.io.IOException;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import java.time.LocalDate;

import java.time.format.DateTimeFormatter;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.unideb.inf.bd.model.SearchResultItem;
import hu.unideb.inf.bd.model.SearchResults;

public class SearchResultsParser {

	private static Logger logger = LoggerFactory.getLogger(SearchResultsParser.class);

	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy").withLocale(Locale.ENGLISH);

	public static final int MAX_ITEMS = 60;

	private int maxItems = MAX_ITEMS;

	public SearchResultsParser() {
	}

	public SearchResultsParser(int maxItems) {
		setMaxItems(maxItems);
	}

	public int getMaxItems() {
		return maxItems;
	}

	public void setMaxItems(int maxItems) {
		this.maxItems = maxItems;
	}

	private int getTotalItems(Document doc) throws IOException {
		if (doc.select("#search-info > span.search-count").isEmpty()) {
			return doc.select("div.book-item").size();
		}
		int totalItems = 0;
		try {
			totalItems = Integer.parseInt(doc.select("#search-info > span.search-count").get(0).text().trim());
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		return totalItems;
	}

	private List<SearchResultItem> extractItems(Document doc) throws IOException {
		List<SearchResultItem> items = new LinkedList<SearchResultItem>();
		for (Element element : doc.select("div.book-item")) {
			String uri = null;
			try {
				uri = element.select("div.item-info > h3.title > a").get(0).attr("abs:href").trim().split("\\?")[0];
			} catch(Exception e) {
				throw new IOException("Malformed document");
			}
			logger.debug("Uri: {}", uri);

			String title = null;
			try {
				title = element.select("div.item-info > h3.title > a").get(0).text().trim();
			} catch(Exception e) {
				throw new IOException("Malformed document");
			}
			logger.debug("Title: {}", title);

			String author = null;
			try {
				author = element.select("div.item-info > p.author").get(0).text().trim();
			} catch(Exception e) {
				logger.warn("No author provided");
			}
			logger.debug("Author: {}", author);

			LocalDate date = null;
			try {
				date = LocalDate.parse(
					element.select("div.item-info > p.published").get(0).text().trim(),
					formatter
				);
			} catch(Exception e) {
				logger.warn("No publication date provided");
			}
			logger.debug("Date: {}", date);

			String format = null;
			try {
				format = element.select("div.item-info > p.format").get(0).text().trim();
			} catch(Exception e) {
				throw new IOException("Malformed document");
			}
			logger.debug("Format: {}", format);

			SearchResultItem item = new SearchResultItem(uri, author, title, date, format);
			items.add(item);
		}
		return items;
	}

	private Document getNextPage(Document doc) throws IOException {
		String nextPage = null;
		try {
			nextPage = doc.select("#next-bottom > a").get(0).attr("abs:href");
			logger.info("Next page: {}", nextPage);
		} catch(Exception e) {
			// no more pages
		}
		return nextPage != null ? Jsoup.connect(nextPage).userAgent("Mozilla").get() : null;
	}

	public SearchResults parse(Document doc) throws IOException {
		List<SearchResultItem> items = new LinkedList<SearchResultItem>();
		int totalItems = getTotalItems(doc);
		logger.info("Total number of items: {}", totalItems);
loop:		while (totalItems != 0 && doc != null) {
			for (SearchResultItem item : extractItems(doc)) {
				if (0 <= maxItems && maxItems <= items.size()) {
					break loop;
				}
				items.add(item);
			}
			if (0 <= maxItems && maxItems <= items.size()) break;
			doc = getNextPage(doc);
		}
		return new SearchResults(totalItems, 1, items.size(), items);
	}

}
