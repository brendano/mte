package te.ui;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.Test;

import com.google.common.collect.*;

import te.data.Span;
import util.U;

public class GUtilTest {

	@Test
	public void spantests2() {
		assertTrue(GUtil.spanContainedIn( 5,5, 3,6));
		assertTrue(GUtil.spanContainedIn( 5,6, 4,7));
		assertTrue(GUtil.spanContainedIn( 5,6, 4,6));
		assertTrue(GUtil.spanContainedIn( 5,6, 5,6));
		assertFalse(GUtil.spanContainedIn( 5,6, 5,5));
		assertFalse(GUtil.spanContainedIn( 5,6, 6,8));
		assertFalse(GUtil.spanContainedIn( 5,10, 7,12));
		assertFalse(GUtil.spanContainedIn( 4,5, 20,25 ));
		assertFalse(GUtil.spanContainedIn( 0,10, 5,6 ));
		
		
		assertTrue(GUtil.spansIntersect( 5,5, 3,6));
		assertTrue(GUtil.spansIntersect( 5,6, 4,7));
		assertTrue(GUtil.spansIntersect( 5,6, 4,6));
		assertTrue(GUtil.spansIntersect( 5,6, 5,6));
		assertTrue(GUtil.spansIntersect( 5,6, 5,5));
		assertFalse(GUtil.spansIntersect( 5,6, 6,8));
		assertTrue(GUtil.spansIntersect( 5,10, 7,12));
		assertTrue(GUtil.spansIntersect( 7,12, 5,10));
		assertFalse(GUtil.spansIntersect( 4,5, 20,25 ));
		assertTrue(GUtil.spansIntersect( 0,10, 5,6 ));
	}
	@Test
	public void spantests() {
		/* 0, {4,8}, 10 ===> [0,4), [4,8), [8,10) */
		List<Span> spans = GUtil.breakpointsToSpans(0, Lists.newArrayList(4,8), 10);
		assertEquals(Lists.newArrayList(new Span(0,4), new Span(4,8), new Span(8,10)), spans);

		spans = GUtil.breakpointsToSpans(0, Lists.newArrayList(), 10);
		assertEquals(Lists.newArrayList(new Span(0,10)), spans);
		
		spans = GUtil.breakpointsToSpans(-4, Lists.newArrayList(3), 10);
		assertEquals(Lists.newArrayList(new Span(-4,3), new Span(3,10)), spans);

		spans = GUtil.breakpointsToSpans(3, Lists.newArrayList(), 3);
		assertEquals(Lists.newArrayList(new Span(3,3)), spans);
		
		spans = GUtil.breakpointsToSpans(3, Lists.newArrayList(), 2);
		assertEquals(Lists.newArrayList(), spans);
		

	}
	
	@Test
	public void regextests() {
		assertEquals(Lists.newArrayList(new Span(0,1), new Span(2,3)), 
				GUtil.splitIntoSpans("X", "aXb"));
		assertEquals(Lists.newArrayList(new Span(0,1), new Span(2,5)), 
				GUtil.splitIntoSpans("X", "aXbbb"));
		assertEquals(Lists.newArrayList(new Span(0,1), new Span(2,2)), 
				GUtil.splitIntoSpans("X", "aX"));
		assertEquals(Lists.newArrayList(new Span(0,0), new Span(1,1)), 
				GUtil.splitIntoSpans("X", "X"));
		assertEquals(Lists.newArrayList(new Span(0,1), new Span(2,2), new Span(3,3)), 
				GUtil.splitIntoSpans("X", "aXX"));
		
		assertEquals(Lists.newArrayList(
					//[0,1), [2,4), [5,6), [7,7)
					new Span(0,1), new Span(2,4), new Span(5,6), new Span(7,7)),
				GUtil.splitIntoSpans("-", "a-bb-c-"));
	}

}
