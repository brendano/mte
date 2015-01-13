package tests;
import static org.junit.Assert.*;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;

import com.google.common.collect.Lists;

import te.data.Document;
import te.data.NLP;
import te.ui.textview.MyTextArea;
import util.U;

public class MyTextAreaTest {

	@Test
	public void testPossible() {
		Function<String,Document> go = (String text) -> {
			Document doc = new Document();
			doc.text = text;
			doc.tokens = NLP.stanfordTokenize(doc.text);
			return doc;
		};
		Document doc;
		doc = go.apply("abc efgh jklmnopqrs");		assertEquals(Lists.newArrayList(3), MyTextArea.possibleBreakpoints(doc,3, 4));

		doc = go.apply("hel");
		assertEquals(Lists.newArrayList(0), MyTextArea.possibleBreakpoints(doc));
		BiFunction<Document, Integer, List<Integer>> calcbreaks = (Document d, Integer width) -> 
				MyTextArea.calculateBreaks(d, 0, d.text.length(), width, String::length);
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,55));

		doc  = go.apply("hel ");
		assertEquals(Lists.newArrayList(0,3), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,55));
		
		doc = go.apply("hel o");
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc));
		
		doc = go.apply("abc abcd");
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(3,4), calcbreaks.apply(doc,0));
		assertEquals(Lists.newArrayList(4),  calcbreaks.apply(doc,5));
		assertEquals(Lists.newArrayList(4), calcbreaks.apply(doc,6));
		assertEquals(Lists.newArrayList(4), calcbreaks.apply(doc,7));
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,8));
		assertEquals(Lists.newArrayList(), calcbreaks.apply(doc,100));

		doc = go.apply("abc efgh jklmnopqrs");
		assertEquals(Lists.newArrayList(0,3,4,8,9), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(9), calcbreaks.apply(doc,15));
		assertEquals(Lists.newArrayList(0,3,4,8,9), MyTextArea.possibleBreakpoints(doc,0, 99999));
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc,0, 6));
		assertEquals(Lists.newArrayList(3,4), MyTextArea.possibleBreakpoints(doc,3, 6));
		assertEquals(Lists.newArrayList(3), MyTextArea.possibleBreakpoints(doc,3, 4));
		assertEquals(Lists.newArrayList(), MyTextArea.possibleBreakpoints(doc,3, 3));
		assertEquals(Lists.newArrayList(), MyTextArea.possibleBreakpoints(doc,3, 1));
		assertEquals(Lists.newArrayList(), MyTextArea.possibleBreakpoints(doc,3, -100));

		doc = go.apply("hel oooooooooooooooooooooooooooooooooooooooooooooooo");
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc));
		
		doc = go.apply("hel oo ");
		assertEquals(Lists.newArrayList(0,3,4,6), MyTextArea.possibleBreakpoints(doc));
	}

}
