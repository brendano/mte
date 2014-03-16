package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collection;
import java.util.List;

import javax.swing.*;

import util.U;

import d.Analysis;
import d.Corpus;
import d.DocSet;
import d.WeightedTerm;

interface QueryReceiver {
	public void receiveQuery(Collection<String> docids);
}
public class Main implements QueryReceiver {
	public Corpus corpus;

	void initData() {
		corpus = Corpus.load("/d/sotu/sotu.xy");
	}
	
	@Override
	public void receiveQuery(Collection<String> docids) {
		DocSet ds = corpus.getDocSet(docids);
		
		List<WeightedTerm> tops = Analysis.topEPMI(30, 1e-3, ds.terms, corpus.globalTerms);
		U.pf("QUERY DOCS: %s docs, %s wordtoks\n", ds.docs.size(), ds.terms.totalCount);
		U.p("=== TOP WORDS ===");
		for (WeightedTerm t : tops) {
			U.pf("%-15s || %d vs %d || %.4g\n", 
					t.term, 
					(int) ds.terms.value(t.term), (int) corpus.globalTerms.value(t.term),
					t.weight);
		}
				
	}
	
	void go() {
		
        JFrame frame = new JFrame("HelloWorldSwing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        frame.setLayout(new FlowLayout());
        frame.setSize(900,800);

        BrushPanel p = new BrushPanel(this, corpus.allDocs());
        p.setMySize(600,300);
        p.setBorder(BorderFactory.createLineBorder(Color.black));
        frame.getContentPane().add(p);
        
        frame.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == 'q') {
					System.exit(0);
				}
			}
			@Override
			public void keyPressed(KeyEvent e) {
			}
			@Override
			public void keyReleased(KeyEvent e) {
			}
        	
        });

        //Display the window.
        frame.pack();
        frame.setVisible(true);

	}
	
	public static void main(String[] args) {
		final Main main = new Main();
		main.initData();
		SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                main.go();
            }
        });
	}

}
