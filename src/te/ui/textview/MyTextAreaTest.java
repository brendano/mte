package te.ui.textview;
import static org.junit.Assert.*;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.*;
import java.util.function.Function;

import org.junit.Test;

import com.google.common.collect.Lists;

import te.data.Document;
import te.data.NLP;
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
		doc = go.apply("hel");
		assertEquals(Lists.newArrayList(0), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(), MyTextArea.calculateBreaks(doc, 55, String::length));

		doc  = go.apply("hel ");
		assertEquals(Lists.newArrayList(0,3), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(), MyTextArea.calculateBreaks(doc, 55, String::length));
		
		doc = go.apply("hel o");
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc));
		
		doc = go.apply("abc abcd");
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(3,4), MyTextArea.calculateBreaks(doc, 0, String::length));
		assertEquals(Lists.newArrayList(4), MyTextArea.calculateBreaks(doc, 5, String::length));
		assertEquals(Lists.newArrayList(4), MyTextArea.calculateBreaks(doc, 6, String::length));
		assertEquals(Lists.newArrayList(4), MyTextArea.calculateBreaks(doc, 7, String::length));
		assertEquals(Lists.newArrayList(), MyTextArea.calculateBreaks(doc, 8, String::length));
		assertEquals(Lists.newArrayList(), MyTextArea.calculateBreaks(doc, 100, String::length));

		doc = go.apply("abc efgh jklmnopqrs");
		assertEquals(Lists.newArrayList(0,3,4,8,9), MyTextArea.possibleBreakpoints(doc));
		assertEquals(Lists.newArrayList(9), MyTextArea.calculateBreaks(doc, 15, String::length));

		doc = go.apply("hel oooooooooooooooooooooooooooooooooooooooooooooooo");
		assertEquals(Lists.newArrayList(0,3,4), MyTextArea.possibleBreakpoints(doc));
		
		doc = go.apply("hel oo ");
		assertEquals(Lists.newArrayList(0,3,4,6), MyTextArea.possibleBreakpoints(doc));
	}

}
