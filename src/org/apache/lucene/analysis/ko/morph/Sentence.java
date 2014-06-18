package org.apache.lucene.analysis.ko.morph;

import java.util.ArrayList;
import java.util.List;

public class Sentence implements Cloneable {
	
	List<WSOutput1> wsList = new ArrayList<WSOutput1>();
	int score = 0;
	int position = 0;
	
	public Sentence() {
		
	}

	public void addOutput(WSOutput1 o) {
		wsList.add(o);
		score += (o.getScore()/10)*o.getSnippet().length()-o.getSnippet().length()*2;
		position += o.getSnippet().length();
	}
	
	public int getScore() {
		return score;
	}
	
	public int getPosition() {
		return position;
	}
	
	public List<WSOutput1> getWordList() {
		return this.wsList;
	}
	
	public Sentence clone() {
	  try {
        return (Sentence)super.clone();
      } catch (CloneNotSupportedException cnse) {
        throw new AssertionError();
      }
	}
}
