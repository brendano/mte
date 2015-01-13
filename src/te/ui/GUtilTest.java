package te.ui;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.Test;

import com.google.common.collect.*;

import te.data.Span;
import util.U;

public class GUtilTest {

	@Test
	public void spantests() {
		/* 0, {4,8}, 10 ===> [0,4), [4,8), [8,10) */
		List<Span> spans = GUtil.breakpointsToSpans(0, Lists.newArrayList(4,8), 10);
		assertEquals(Lists.newArrayList(new Span(0,4), new Span(4,8), new Span(8,10)), spans);

		spans = GUtil.breakpointsToSpans(0, Lists.newArrayList(), 10);
		assertEquals(Lists.newArrayList(new Span(0,10)), spans);

	}
	
	@Test
	public void regextests() {
		List<Span> spans;
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
	}

}
