package tests;
import static org.junit.Assert.*;

import java.util.*;
import java.util.function.BiFunction;

import org.junit.Test;

import com.google.common.collect.Lists;

import te.data.Document;
import te.data.NLP;
import te.ui.textview.MyTextArea;
import utility.util.U;

public class MyTextAreaTest {

	Document tokenize(String text) {
		Document doc = new Document();
		doc.text = text;
		doc.tokens = NLP.stanfordTokenize(doc.text);
		return doc;
	}
	
	public static List<Integer> possibleBreakpoints(Document doc) {
		return MyTextArea.possibleBreakpoints(doc, 0, doc.text.length());
	}

	@Test
	public void testpunct() {
		Document doc;
		doc = tokenize("Hello world?");
		List<Integer> breaks = possibleBreakpoints(doc);
		assertEquals(Lists.newArrayList(6), calcbreaks(doc, 10));
		
		// from SOTU 2010.txt
		doc = tokenize("Madam Speaker, Vice President Biden, Members of Congress, distinguished guests, and fellow Americans: Our Constitution declares that from time to time, the President shall give to Congress information about the state of our Union. For 220 years, our leaders have fulfilled this duty. They've done so during periods of prosperity and tranquility, and they've done so in the midst of war and depression, at moments of great strife and great struggle.\n");
		breaks = possibleBreakpoints(doc);
		breaks = calcbreaks(doc, 100);
		U.p(breaks);
		
		
	}
	List<Integer> calcbreaks(Document d, int w) {
		return MyTextArea.calculateBreaks(d, 0, d.text.length(), w, String::length);
	}
	@Test
	public void teststuff() {
		Document doc;
		doc = tokenize("abc efgh jklmnopqrs");		assertEquals(Lists.newArrayList(3), MyTextArea.possibleBreakpoints(doc,3, 4));

		doc = tokenize("hel");
		assertEquals(Lists.newArrayList(0), possibleBreakpoints(doc));
		BiFunction<Document, Integer, List<Integer>> calcbreaks = (Document d, Integer width) -> 
				MyTextArea.calculateBreaks(d, 0, d.text.length(), width, String::length);
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,55));

		doc  = tokenize("hel ");
		assertEquals(Lists.newArrayList(0,3), possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,55));
		
		doc = tokenize("hel o");
		assertEquals(Lists.newArrayList(0,3,4), possibleBreakpoints(doc));
		
		doc = tokenize("abc abcd");
		assertEquals(Lists.newArrayList(0,3,4), possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(3,4), calcbreaks.apply(doc,0));
		assertEquals(Lists.newArrayList(4),  calcbreaks.apply(doc,5));
		assertEquals(Lists.newArrayList(4), calcbreaks.apply(doc,6));
		assertEquals(Lists.newArrayList(4), calcbreaks.apply(doc,7));
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,8));
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,100));

		doc = tokenize("abc efgh jklmnopqrs");
		assertEquals(Lists.newArrayList(0,3,4,8,9), possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(9), calcbreaks.apply(doc,15));
		assertEquals(Lists.newArrayList(0,3,4,8,9), MyTextArea.possibleBreakpoints(doc,0, 99999));
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc,0, 6));
		assertEquals(Lists.newArrayList(3,4), MyTextArea.possibleBreakpoints(doc,3, 6));
		assertEquals(Lists.newArrayList(3), MyTextArea.possibleBreakpoints(doc,3, 4));
		assertEquals(Lists.newArrayList(), MyTextArea.possibleBreakpoints(doc,3, 3));
		assertEquals(Lists.newArrayList(), MyTextArea.possibleBreakpoints(doc,3, 1));
		assertEquals(Lists.newArrayList(), MyTextArea.possibleBreakpoints(doc,3, -100));

		doc = tokenize("hel oooooooooooooooooooooooooooooooooooooooooooooooo");
		assertEquals(Lists.newArrayList(0,3,4), possibleBreakpoints(doc));
		
		doc = tokenize("hel oo ");
		assertEquals(Lists.newArrayList(0,3,4,6), possibleBreakpoints(doc));
	}

}
