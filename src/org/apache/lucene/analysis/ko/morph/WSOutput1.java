package org.apache.lucene.analysis.ko.morph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.ko.utils.DictionaryUtil;

public class WSOutput1 {

	public static int DEC_COMPOUND = 50;
	public static int DEC_PART_WORD = 30;
	
	private String snippet;

	private boolean isPrefix = false;
	
	private boolean existWord = false;

	private List<AnalysisOutput> morphemes;
	
	private int score = 0;
	
	public WSOutput1(String s) {
		this.snippet = s;
		morphemes = new ArrayList<AnalysisOutput>();
		lookup(s);
	}

	private void lookup(String s) {
		try {
			Iterator<WordEntry> iter = DictionaryUtil.findWithPrefix(s);
			if(iter.hasNext()) {
				isPrefix = true;
				existWord = DictionaryUtil.getAllNoun(s)!=null;
				if(existWord) score = AnalysisOutput.SCORE_CORRECT;
			}
		} catch (MorphException e) {
			
		}
	}

	public String getSnippet() {
		return snippet;
	}

	public void setSnippet(String snippet) {
		this.snippet = snippet;
	}
	
	public void setMorphemes(List<AnalysisOutput> os) {
		morphemes = os;
		
		if(morphemes.size()>0)
			score = morphemes.get(0).getScore();
		
		if(score==AnalysisOutput.SCORE_CORRECT) 
			existWord = true;
	}

	public void decreaseScore(int s) {
		this.score -= s;
	}
	
	public void addMorpheme(AnalysisOutput o) {
		morphemes.add(o);
		score = o.getScore();
		if(score==AnalysisOutput.SCORE_CORRECT) 
			existWord = true;
	}
	
	public List<AnalysisOutput> getMorphemes() {
		return this.morphemes;
	}
	
	public boolean isPrefix() {
		return isPrefix;
	}

	public void setPrefix(boolean isPrefix) {
		this.isPrefix = isPrefix;
	}

	public boolean isExistWord() {
		return existWord;
	}

	public void setExistWord(boolean existWord) {
		this.existWord = existWord;
	}	
	
	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}
}
