package com.cadrlife.jhaml.internal;

import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;

public class JHamlParser {
	private final JHamlReader reader;
	private Helper helper;
	public JHamlParser(JHamlReader reader) {
		// TODO the Helper methods called from this class do not require a config.
		// Perhaps Helper should become 2 classes: one that takes a config and one that does not.
		helper = new Helper(null);
		
		this.reader = reader;
	}
	public JHamlParser(Reader in) {
		this(new JHamlReader(new LineNumberReader(in)));
	}
	public List<Line> jHamlSource() {
		List<Line> lines = new ArrayList<Line>();
		while (line(lines));
		return lines;
	}
	private boolean line(List<Line> lines) {
		if (reader.eof()) {
			return false;
		}
		Line line = new Line();
		line.lineNumber = reader.getLineNumber()+1;
		lines.add(line);
		reader.setObserver(line.getTextWriter());
		line.leadingWhitespace = reader.consumeMatching(CharMatchers.INDENTATION_CHAR);
		if (elementDeclaration(line)) {
			line.isElement = true;
			if (reader.isNextChar('/')) {
				reader.skip(1);
				line.isSelfClosing = true;
			}
			ignoreWhitespace();
			line.inlineContent = reader.readLine();
			reader.setObserver(null);
			return true;
		}
		line.inlineContent = reader.readLine();
		reader.setObserver(null);
		return true;
	}
	private boolean elementDeclaration(Line line) {
		boolean hasElementTypeSpecifier = elementTypeSpecifier(line);
		if (hasElementTypeSpecifier || classSpecifier(line) || idSpecifier(line)) {
			while (classSpecifier(line) || idSpecifier(line));
			if (!hasElementTypeSpecifier) {
				line.tag = "div";
			}
			attributeHashes(line);
			return true;
		}
		return false;
	}
	
	private void attributeHashes(Line line) {
		String textBeforeAttrHashes = line.text;
		try {
			boolean foundAttributeHash = attributeHash(line.attrMap);
			htmlStyleAttributeHash(line.attrMap);
			if (!foundAttributeHash) {
				attributeHash(line.attrMap);
			}
		} catch (JHamlInternalParseException e) {
			helper.getErrorChecker().setCurrentLineNumber(reader.getLineNumber());
			helper.getErrorChecker().invalidAttributeList(line.text.substring(textBeforeAttrHashes.length()));
		}
		
	}
	
	private boolean htmlStyleAttributeHash(Map<String, AttributeValue> attrMap) throws JHamlInternalParseException {
		CharMatcher CLOSE_PAREN = CharMatcher.is(')');
		if (reader.isNextChar('(')) {
			reader.skip(1);
			ignoreWhitespaceIncludingNewline();
			boolean attrFound = htmlStyleAttributeMapping(attrMap, CharMatcher.WHITESPACE, CLOSE_PAREN);
			ignoreWhitespaceIncludingNewline();
			while (attrFound && !reader.isNextChar(')') && !reader.eof()) {
				attrFound = htmlStyleAttributeMapping(attrMap, CharMatcher.WHITESPACE, CLOSE_PAREN);
				ignoreWhitespaceIncludingNewline();
			}
			if (reader.isNextChar(')')) {
				reader.skip(1);
				return true;
			}
			fail();
		}
	 	return false;
	}
	private boolean htmlStyleAttributeMapping(Map<String, AttributeValue> attrMap, CharMatcher separator, CharMatcher endOfAttributes) throws JHamlInternalParseException {
		String attr = ""; 
		if (reader.nextCharMatches(CharMatchers.XML_NAME_START_CHAR)) {
			attr = reader.consumeMatching(CharMatchers.XML_NAME_CHAR);
		}
		if (StringUtils.isBlank(attr)) {
			return false;
		}
		ignoreWhitespaceIncludingNewline();
		if (reader.isNextChar('=')) {
			reader.skip(1);
			ignoreWhitespaceIncludingNewline();
			attrMap.put(attr, attributeValue(separator.or(endOfAttributes)));
			return true;
		} else {
			attrMap.put(attr, AttributeValue.literal("true"));
			return true;
		}
//		fail();
//		return false;
		
	}
	private boolean attributeHash(Map<String, AttributeValue> attrMap) throws JHamlInternalParseException {
		CharMatcher COMMA = CharMatcher.is(',');
		CharMatcher CLOSE_BRACE = CharMatcher.is('}');
		if (reader.isNextChar('{')) {
			reader.skip(1);
			ignoreWhitespaceIncludingNewline();
			boolean attrFound = attributeMapping(attrMap, COMMA, CLOSE_BRACE);
			ignoreWhitespaceIncludingNewline();
			while (attrFound && reader.nextCharMatches(COMMA)) {
				reader.skip(1);
				ignoreWhitespaceIncludingNewline();
				attributeMapping(attrMap, COMMA, CLOSE_BRACE);
				ignoreWhitespaceIncludingNewline();
			}
			if (reader.isNextChar('}')) {
				reader.skip(1);
				return true;
			}
			fail();
		}
	 	return false;
	}
	
	private void fail() throws JHamlInternalParseException {
		throw new JHamlInternalParseException();	
	}
	
	private boolean attributeMapping(Map<String, AttributeValue> attrMap, CharMatcher separator, CharMatcher endOfAttributes) throws JHamlInternalParseException {
		String attr = ""; 
		if (reader.isNextChar(':')) {
			reader.skip(1);
			attr = reader.consumeMatching(CharMatchers.XML_NAME_CHAR);
			if (StringUtils.isBlank(attr)) {
				return false;
			}
		} else if (reader.isNextCharOneOf("0123456789")) {
			attr = numberLiteral();
		} else if (reader.isNextCharOneOf("'\"")) {
			attr = helper.parseStringLiteral(stringLiteral());
		} else {
			return false;
		}
		ignoreWhitespaceIncludingNewline();
		if (reader.isNextInput("=>")) {
			reader.skip(2);
			ignoreWhitespaceIncludingNewline();
			if (!html5DataAttributeMap(attr, attrMap)) {
				attrMap.put(attr, attributeValue(separator.or(endOfAttributes)));
			}
			return true;
		}
		fail();
		return false;
	}
	
	private boolean html5DataAttributeMap(String attr, Map<String, AttributeValue> attrMap) throws JHamlInternalParseException {
		Map<String, AttributeValue> dataAttributes = new HashMap<String, AttributeValue>();
		if (attr.equals("data") && attributeHash(dataAttributes)) {
			for (Entry<String, AttributeValue> e : dataAttributes.entrySet()) {
				String key = attr + "-" + e.getKey();
				if (!attrMap.containsKey(key)) {
					attrMap.put(key, e.getValue());
				}
			}
			return true;
		}
		return false;
	}
	private AttributeValue attributeValue(CharMatcher separator) throws JHamlInternalParseException {
		String exp = expression(separator).trim();
		if (exp.startsWith("'") || exp.startsWith("\"")) {
			return AttributeValue.quoted(helper.parseStringLiteral(exp));
		} else {
//			String identifier = reader.consumeMatching(CharMatcher.JAVA_LETTER);
//			if (!identifier.isEmpty()) {
//				identifier += reader.consumeMatching(CharMatcher.JAVA_LETTER_OR_DIGIT);
//				return AttributeValue.literal(identifier);
//			}
			CharMatcher NUMBER_LITERAL_CHARS = CharMatcher.JAVA_LETTER_OR_DIGIT.or(CharMatcher.is('.'));
			if (CharMatcher.JAVA_DIGIT.indexIn(exp) == 0 && NUMBER_LITERAL_CHARS.matchesAllOf(exp)) {
				return AttributeValue.literal(helper.parseNumberLiteral(exp));
			}
			ImmutableSet<String> keywords = ImmutableSet.of("null","true","false");
			if (keywords.contains(exp)) {
				return AttributeValue.literal(exp);
			}			
			return AttributeValue.literal("<%= " + exp + " %>");
		}
//		fail();
//		return null;
	}
	
	private String expression(CharMatcher delim) throws JHamlInternalParseException {
		int parenDepth = 0;
		int braceDepth = 0;
		int bracketDepth = 0;
		String exp = "";
		while (!reader.eof()) {
			if (parenDepth < 1 && braceDepth <1 && braceDepth <1 && bracketDepth <1 && reader.nextCharMatches(delim)) {
				break;
			}
			if (reader.isNextCharOneOf("'\"")) {
				exp += stringLiteral();
				continue;
			}
			char c = (char)reader.read();
			exp += c;
			switch (c) {
			case '{': braceDepth++; break;
			case '}': braceDepth--; break;
			case '(': parenDepth++; break;
			case ')': parenDepth--; break;
			case '[': bracketDepth++; break;
			case ']': bracketDepth--; break;
			}
		}
		return exp;
	}
	private String numberLiteral() {
		String val = "";
			if (reader.isNextInput("0x")) {
				val = "0x";
				reader.skip(2);
			}
			val += reader.consumeMatching(CharMatcher.inRange('0', '9'));
			if (reader.isNextChar('.')) {
				reader.skip(1);
				val += "." + reader.consumeMatching(CharMatcher.inRange('0', '9'));
				if (reader.isNextCharOneOf("dDfF")) {
					reader.skip(1);
				}
				return helper.parseDoubleLiteral(val);
				
			}
			if (reader.isNextCharOneOf("lL")) {
				reader.skip(1);
			}
			return helper.parseIntegerLiteral(val);
	}
	private String stringLiteral() throws JHamlInternalParseException {
		char quote = (char) reader.read();
		String val = reader.consumeMatchingWithEscaping(CharMatcher.is(quote).negate(),'\\');
//		String ret = StringEscapeUtils.unescapeJava(val);
		if (reader.isNextChar(quote)) {
			reader.skip(1);
			return quote + val + quote;
		}
		fail();
		return null;
	}
	private void ignoreWhitespace() {
		reader.consumeMatching(CharMatchers.INDENTATION_CHAR);
	}
	private void ignoreWhitespaceIncludingNewline() {
		reader.consumeMatching(CharMatcher.WHITESPACE);
	}
	private boolean elementTypeSpecifier(Line line) {
		if (reader.isNextChar('%')) {
			reader.skip(1);
			line.tag = reader.consumeMatching(CharMatchers.XML_NAME_CHAR);
			return true;
		}
		return false;
	}
	private boolean classSpecifier(Line line) {
		if (reader.isNextChar('.')) {
			reader.skip(1);
			line.classes.add(reader.consumeMatching(CharMatchers.CLASS_CHAR));
			return true;
		}
		return false;
	}
	private boolean idSpecifier(Line line) {
		if (reader.isNextChar('#')) {
			reader.skip(1);
			line.ids.add(reader.consumeMatching(CharMatchers.ID_CHAR));
			return true;
		}
		return false;
	}
}
