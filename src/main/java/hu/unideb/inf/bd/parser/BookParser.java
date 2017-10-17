package hu.unideb.inf.bd.parser;

import java.io.File;
import java.io.IOException;

import java.math.BigDecimal;

import java.text.DecimalFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.unideb.inf.jaxb.JAXBUtil;
import hu.unideb.inf.bd.model.Book;
import hu.unideb.inf.bd.model.Price;

public class BookParser {

	private static Logger logger = LoggerFactory.getLogger(BookParser.class);

	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy").withLocale(Locale.ENGLISH);

	public BookParser() {
	}

	public Book parse(String url) throws IOException {
		Document doc = Jsoup.connect(url).userAgent("Mozilla").get();
		Book book = parse(doc);
		book.setUri(url);
		return book;
	}

	public Book parse(File file) throws IOException {
		Document doc = Jsoup.parse(file, null);
		Book book = parse(doc);
		book.setUri(file.toURI().toString());
		return book;
	}

	public Book parse(Document doc) throws IOException {
		Book book = new Book();
		ArrayList<Book.Author> authors = new ArrayList<Book.Author>();
		try {
			for (Element e : doc.select("div.item-info > div.author-info > a[itemprop=author]")) {
				Book.Author author = new Book.Author();
				author.setName(e.text().trim());
				String role = "By (author)";
				if (e.previousSibling() instanceof TextNode) {
					role = ((TextNode) e.previousSibling()).text().trim();
					role = role.replaceFirst("^,\\s*", "");
				}
				author.setRole(role);
				logger.info("Author: {}", author);
				authors.add(author);
			}
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setAuthors(authors);

		String title = null;
		try {
			title = doc.select("div.item-info > h1[itemprop=name]").first().text().trim();
			logger.info("Title: {}", title);
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setTitle(title);

		String publisher = null;
		try {
			publisher = doc.select("ul.biblio-info a[itemprop=publisher]").first().text().trim();
			logger.info("Publisher: {}", publisher);
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setPublisher(publisher);

		LocalDate date = null;
		try {
			String s = doc.select("ul.biblio-info span[itemprop=datePublished]").first().text().trim();
			date = LocalDate.parse(s, formatter);
			logger.info("Date: {}", date);
		} catch(Exception e) {
		}
		book.setDate(date);

		String description = null;
		try {
			description = doc.select("div.item-excerpt").first().ownText().trim();
			logger.info("Description: {}", description);
		} catch(Exception e) {
		}
		book.setDescription(description);

		String format = null;
		Integer	pages = null;
		try {
			String s = doc.select("ul.biblio-info > li:has(label:containsOwn(Format)) > span").first().text().trim();
			Pattern pattern = Pattern.compile("([^|]+)( \\| (\\d+) pages)?");
			Matcher matcher = pattern.matcher(s);
			if (! matcher.matches()) throw new IOException("Malformed document");
			format = matcher.group(1).trim();
			if (matcher.group(3) != null) {
				pages = Integer.parseInt(matcher.group(3));
			}
			logger.info("Format: {}", format);
			logger.info("Pages: {}", pages);
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setFormat(format);
		book.setPages(pages);

		if (! doc.select("ul.biblio-info > li:has(label:containsOwn(Dimensions)) > span").isEmpty()) {
			try {
				String s = doc.select("ul.biblio-info > li:has(label:containsOwn(Dimensions)) > span").first().text().trim();
				logger.info("Dimensions: {}", s);
				Book.Dimensions dimensions = new Book.Dimensions(s);
				book.setDimensions(dimensions);
			} catch(Exception e) {
				throw new IOException("Malformed document");
			}
		}

		String language = null;
		try {
			language = doc.select("ul.biblio-info > li:has(label:containsOwn(Language)) > span").first().text().trim();
			logger.info("Language: {}", language);
		} catch(Exception e) {
		}
		book.setLanguage(language);

		String edition = null;
		try {
			edition = doc.select("ul.biblio-info > li:has(label:containsOwn(Edition statement)) > span").first().text().trim();
			logger.info("Edition: {}", edition);
		} catch(Exception e) {
		}
		book.setEdition(edition);

		String isbn10 = null;
		try {
			isbn10 = doc.select("ul.biblio-info > li:has(label:containsOwn(ISBN10)) > span").first().text().trim();
			logger.info("ISBN10: {}", isbn10);
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setIsbn10(isbn10);

		String isbn13 = null;
		try {
			isbn13 = doc.select("ul.biblio-info > li:has(label:containsOwn(ISBN13)) > span").first().text().trim();
			logger.info("ISBN13: {}", isbn13);
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setIsbn13(isbn13);

		String currency = null;
		try {
			currency = doc.select("#selectCurrency > option[selected=selected]").first().attr("value");
		} catch(Exception e){
			throw new IOException("Malformed document");
		}
		logger.info("Currency: [{}]", currency);
		DecimalFormat df = MonetaryUtil.createDecimalFormat(currency);
		df.setParseBigDecimal(true);

		Price salePrice = null;
		Price listPrice = null;
		try {
			if (! doc.select("div.price-info-wrap > div.item-info-wrap > div.price").isEmpty()) {
				Element p = doc.select("div.price-info-wrap > div.item-info-wrap > div.price").first();
				System.out.println(p);
				salePrice = new Price((BigDecimal) df.parse(p.select("span.sale-price").first().text().trim()), currency);
				logger.info("Sale price: {}", salePrice);
				if (! p.select("span.list-price").isEmpty()) {
					listPrice = new Price((BigDecimal) df.parse(p.select("span.list-price").first().text().trim()), currency);
					logger.info("List price: {}", listPrice);
				}
			} else {
				logger.warn("Currently unavailable");
				String s = doc.select("div.price-info-wrap > div.item-info-wrap > p.list-price").first().text().trim();
				s = s.replaceFirst("^List price:\\s+", "");
				listPrice = new Price((BigDecimal) df.parse(s), currency);
				logger.info("List price: {}", listPrice);
			}
		} catch(Exception e) {
			throw new IOException("Malformed document");
		}
		book.setSalePrice(salePrice);
		book.setListPrice(listPrice);
		return book;
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.printf("Usage: java %s <url>\n", BookParser.class.getName());
			System.exit(1);
		}
		try {
			Book book = new BookParser().parse(args[0]);
			System.out.println(book);
			JAXBUtil.toXML(book, System.out);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

}
