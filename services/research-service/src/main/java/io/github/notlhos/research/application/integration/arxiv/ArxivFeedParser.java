package io.github.notlhos.research.application.integration.arxiv;

import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
class ArxivFeedParser {

    private static final String ATOM = "http://www.w3.org/2005/Atom";

    private static final String ABSTRACT_PATH = "/abs/";

    private static final String ABSTRACT_URL = "https://arxiv.org/abs/";

    private static final Pattern VERSION_SUFFIX = Pattern.compile("v\\d+$");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    List<ArxivPaper> parse(String xml) {
        NodeList entries = document(xml).getElementsByTagNameNS(ATOM, "entry");
        List<ArxivPaper> papers = new ArrayList<>(entries.getLength());
        for (int i = 0; i < entries.getLength(); i++) {
            papers.add(toPaper((Element) entries.item(i)));
        }
        return papers;
    }

    /* ***** Private Methods ***** */

    private Element document(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("Unreadable arXiv feed", e);
        }
    }

    private ArxivPaper toPaper(Element entry) {
        String absUrl = text(entry, "id");
        String versionedId = absUrl.substring(absUrl.lastIndexOf(ABSTRACT_PATH) + ABSTRACT_PATH.length());
        String arxivId = VERSION_SUFFIX.matcher(versionedId).replaceAll("");
        return new ArxivPaper(
                arxivId,
                text(entry, "title"),
                text(entry, "summary"),
                authors(entry),
                text(entry, "published").substring(0, 10),
                ABSTRACT_URL + arxivId);
    }

    private List<String> authors(Element entry) {
        NodeList nodes = entry.getElementsByTagNameNS(ATOM, "author");
        List<String> names = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            names.add(text((Element) nodes.item(i), "name"));
        }
        return names;
    }

    private String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagNameNS(ATOM, tag);
        String raw = nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
        return WHITESPACE.matcher(raw.strip()).replaceAll(" ");
    }

}
