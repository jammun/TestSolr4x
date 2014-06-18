package org.apache.lucene.analysis.ko.morph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.ko.utils.ConstraintUtil;
import org.apache.lucene.analysis.ko.utils.DictionaryUtil;
import org.apache.lucene.analysis.ko.utils.EomiUtil;
import org.apache.lucene.analysis.ko.utils.IrregularUtil;
import org.apache.lucene.analysis.ko.utils.MorphUtil;
import org.apache.lucene.analysis.ko.utils.NounUtil;
import org.apache.lucene.analysis.ko.utils.SyllableUtil;
import org.apache.lucene.analysis.ko.utils.VerbUtil;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class WordSpaceAnalyze {
  
  private MorphAnalyzer morphAnalyzer = null;
  
  private CompoundNounAnalyzer compAnalyzer = null;
  
  public WordSpaceAnalyze() {
    morphAnalyzer = new MorphAnalyzer();
    compAnalyzer = new CompoundNounAnalyzer();
  }
/**
 * 알고리즘은 동적 프로그램이다.
 * 분석은 전방부터 분석을 실시한다.
 * 1음절인 경우 "간, 온"과 같이 용언+어미인 경우를 점검하고, 1음절 단어인지 점검한다.
 * 2음절이상인 경우 사전에 존재하는 단어인지 점검한다.
 * 현재의 음절이 어미나 조사의 1음절로 시작될 수 있는 경우 형태소분석을 시도한다.
 * 위의 경우는 다음 음절이 조사나 어미의 2음절 이상에서 사용할 수 없을 때 까지 계속한다.
 * @param input
 * @return
 * @throws MorphException
 */
  public List<AnalysisOutput> analyze(String input) throws MorphException {
	  
	  int len = input.length();
	  WSOutput1[][] repositories = new WSOutput1[len][len];
	  
	  for(int e=len-1;e>=0;e--) {
		  
		  List<AnalysisOutput> candidates = new ArrayList<AnalysisOutput>();
		  String snippet = input.substring(0,e+1);
		  
		  analyzeFromEnd(snippet, candidates);
		  
		  System.out.println("candidates.size()>>"+candidates.size()); 

		  // 어미와 조사로 분석되지 않았다. 사전에서 가장 긴 단어를 찾아서 후보로 넣었다.
		  if(candidates.size()==0) {
			  String text = findLongestWord(snippet, e);
			  AnalysisOutput ao = buildAnalysisOutput(text);
			  candidates.add(ao);
		  }
		  
		  for(AnalysisOutput ao : candidates) {
			  System.out.println(ao);
		  }
		  
		  String source = candidates.get(0).getSource();
		  e -= source.length()-1;
		  
		  if(e<0) break;
	  }
	  
	  return null;
  }
  
  private void analyzeFromEnd(String snippet, List<AnalysisOutput> candidates) throws MorphException {
	  
	  char valid = validWithJosaOrEomi(snippet);
	  if(valid=='n') return;
	  
	  int len = snippet.length();
	  int score = 0;
	  char[] chars = MorphUtil.decompose(snippet.charAt(len-1));
	  int start = len-2;
	  if(chars.length==3 && (chars[2]=='ㅁ'||chars[2]=='ㄹ'||chars[2]=='ㄴ')) start = len-1;
	  
	  List<AnalysisOutput> best = null;
	  String bestWord = null;
	  
	  for(int e=start;e>=0;e--) {
		  
		  String text = snippet.substring(e);
		  List<AnalysisOutput> list = new ArrayList<AnalysisOutput>();
		  
		  analysisByRule(text, list);
		  Collections.sort(list,new AnalysisOutputComparator<AnalysisOutput>());
		  
		  if(list.size()==0 || score>list.get(0).getScore()) continue;
		  if(start-e>5) break;
		  
		  score = list.get(0).getScore();
		  best = list;
		  bestWord = text;
	  }
	  
	  if(best!=null) candidates.addAll(best);
	  
	  for(AnalysisOutput o : candidates)
		  o.setSource(bestWord);
  }
  
  /**
   * 조사로 끝나는 것이 타당한지 조사한다.
   * @return
 * @throws MorphException 
   */
  private boolean validWithJosa(String stem, String end) throws MorphException {

	if(stem==null||stem.length()==0) return false;  
    
    char[] chrs = MorphUtil.decompose(stem.charAt(stem.length()-1));
    if(!DictionaryUtil.existJosa(end)||
        (chrs.length==3&&ConstraintUtil.isTwoJosa(end))||
        (chrs.length==2&&(ConstraintUtil.isThreeJosa(end))||"".equals(end))) return false; 
    
    return true;
  }
  
  /**
   * 어미로 끝나는 것이 타당한지 조사한다.
   * @return
 * @throws MorphException 
   */
  private boolean validWithEomi(String stem, String end) throws MorphException {
	  String[] morphs = EomiUtil.splitEomi(stem, end);
	  return morphs!=null;
  }
  /**
   * 사전에 존재하는 가장 긴 단어를 찾는다.
   * 
   * @param input
   * @param e
   * @return
   * @throws MorphException
   */
  private String findLongestWord(String input, int e) throws MorphException {
	  
	  String founded = null;
	  
	  for(int i=e-1;i>=0;i--) {
		  String text = input.substring(i);
		  if(DictionaryUtil.getAllNoun(text)!=null) {
			  founded = text;
		  } else {
			  break;
		  }
	  }
	  
	  if(founded!=null) return founded;
	  
	  // 조사나 어미일 가능성이 있는 위치를 구한다.
	  int posEnd = 0;
	  if(founded==null) {
		  // 어미와 조사 분리되지 않은 경우이므로 마지막 글자는 제외하고 앞에 문자부터 조사한다.
		  for(int i=e;i>0;i--) {
			  char result = validWithJosaOrEomi(input.substring(0,i));
			  if(result!='n') {
				  posEnd=i;
				  break;
			  }
		  }
	  }
	  
	  founded = input.substring(posEnd);
	  
	  return founded;
  }
  
  private char validWithJosaOrEomi(String input) throws MorphException {
	
	boolean validJosa = false;
	boolean validEomi = false;
	
    boolean josaFlag = true;
    boolean eomiFlag = true;
        
    int strlen = input.length();
    
//	    boolean isVerbOnly = MorphUtil.hasVerbOnly(input);
    boolean isVerbOnly = false;
    validEomi = validWithEomi(input,"");
    
    for(int i=strlen-1;i>0;i--) {
      
      String stem = input.substring(0,i);
      String eomi = input.substring(i);

      char[] feature =  SyllableUtil.getFeature(eomi.charAt(0));    
      if(!validJosa&&!isVerbOnly&&josaFlag&&feature[SyllableUtil.IDX_JOSA1]=='1') {        
    	  validJosa = validWithJosa(stem,eomi);
      }
      
      if(!validEomi&&eomiFlag) {      
    	  validEomi = validWithEomi(stem,eomi);
      }      
      
      if(josaFlag&&feature[SyllableUtil.IDX_JOSA2]=='0') josaFlag = false;
      if(eomiFlag&&feature[SyllableUtil.IDX_EOMI2]=='0') eomiFlag = false;
      
      if(!josaFlag&&!eomiFlag) break;
    }
    
    char result = 'n';
    if(validJosa && validEomi)
    	result = 'a';
    else if(validJosa)
    	result = 'j';
    else if(validEomi)
    	result ='e';
    
    return result;
  }
  
  private void analysisByRule(String input, List<AnalysisOutput> candidates) throws MorphException {
	  
    boolean josaFlag = true;
    boolean eomiFlag = true;
        
    int strlen = input.length();
    
//	    boolean isVerbOnly = MorphUtil.hasVerbOnly(input);
    boolean isVerbOnly = false;
    morphAnalyzer.analysisWithEomi(input,"",candidates);
    
    for(int i=strlen-1;i>0;i--) {
      
      String stem = input.substring(0,i);
      String eomi = input.substring(i);

      char[] feature =  SyllableUtil.getFeature(eomi.charAt(0));    
      if(!isVerbOnly&&josaFlag&&feature[SyllableUtil.IDX_JOSA1]=='1') {        
    	  morphAnalyzer.analysisWithJosa(stem,eomi,candidates);
      }
      
      if(eomiFlag) {      
    	  morphAnalyzer.analysisWithEomi(stem,eomi,candidates);
      }      
      
      if(josaFlag&&feature[SyllableUtil.IDX_JOSA2]=='0') josaFlag = false;
      if(eomiFlag&&feature[SyllableUtil.IDX_EOMI2]=='0') eomiFlag = false;
      
      if(!josaFlag&&!eomiFlag) break;
    }
  }
  
  /**
   * 체언 + 조사 (PTN_NJ)
   * 체언 + 용언화접미사 + '음/기' + 조사 (PTN_NSMJ
   * 용언 + '음/기' + 조사 (PTN_VMJ)
   * 용언 + '아/어' + 보조용언 + '음/기' + 조사(PTN_VMXMJ)
   * 
   * @param stem  stem
   * @param end end
   * @param candidates  candidates
   * @throws MorphException exception
   */
  public void analysisWithJosa(String stem, String end, List<AnalysisOutput> candidates) throws MorphException {
  
    if(stem==null||stem.length()==0) return;  
    
    char[] chrs = MorphUtil.decompose(stem.charAt(stem.length()-1));
    if(!DictionaryUtil.existJosa(end)||
        (chrs.length==3&&ConstraintUtil.isTwoJosa(end))||
        (chrs.length==2&&(ConstraintUtil.isThreeJosa(end))||"".equals(end))) return; // 연결이 가능한 조사가 아니면...

    AnalysisOutput output = new AnalysisOutput(stem, end, null, PatternConstants.PTN_NJ);
    output.setPos(PatternConstants.POS_NOUN);
    
//    boolean success = false;
//    try {
//      success = NounUtil.analysisMJ(output.clone(), candidates);
//    } catch (CloneNotSupportedException e) {
//      throw new MorphException(e.getMessage(),e);
//    }
//
//    WordEntry entry = DictionaryUtil.getWordExceptVerb(stem);
//    if(entry!=null) {
//      output.setScore(AnalysisOutput.SCORE_CORRECT);
//      if(entry.getFeature(WordEntry.IDX_NOUN)=='0'&&entry.getFeature(WordEntry.IDX_BUSA)=='1') {
//        output.setPos(PatternConstants.POS_ETC);
//        output.setPatn(PatternConstants.PTN_ADVJ);
//      }
//      if(entry.getCompounds().size()>1) output.addCNoun(entry.getCompounds());
//    }else {
//      if(MorphUtil.hasVerbOnly(stem)) return;
//    }
    
      candidates.add(output);

  }
  
  /**
   * 
   *  1. 사랑받다 : 체언 + 용언화접미사 + 어미 (PTN_NSM) <br>
   *  2. 사랑받아보다 : 체언 + 용언화접미사 + '아/어' + 보조용언 + 어미 (PTN_NSMXM) <br>
   *  3. 학교에서이다 : 체언 + '에서/부터/에서부터' + '이' + 어미 (PTN_NJCM) <br>
   *  4. 돕다 : 용언 + 어미 (PTN_VM) <br>
   *  5. 도움이다 : 용언 + '음/기' + '이' + 어미 (PTN_VMCM) <br>
   *  6. 도와주다 : 용언 + '아/어' + 보조용언 + 어미 (PTN_VMXM) <br>
   *  
   * @param stem  stem
   * @param end end
   * @param candidates  candidates
   * @throws MorphException exception 
   */
  public void analysisWithEomi(String stem, String end, 
		  List<AnalysisOutput> candidates) throws MorphException {
    
    String[] morphs = EomiUtil.splitEomi(stem, end);
    if(morphs[0]==null) return; // 어미가 사전에 등록되어 있지 않다면....

    String[] pomis = EomiUtil.splitPomi(morphs[0]);

    AnalysisOutput o = new AnalysisOutput(pomis[0],null,morphs[1],PatternConstants.PTN_VM);
    o.setPomi(pomis[1]);
  
    candidates.add(o);
    
  }    
  
//  public List<AnalysisOutput> analyze(String input) throws MorphException {
//    
//    int len = input.length();
//    WSOutput1[][] repositories = new WSOutput1[len][len];
//  
//    // 복합명사 분해와 조사로 끝나는 경우를 분석하여 배열에 저장한다.
//    for(int s=0; s < len ; s++) {
//      
//      boolean josaFlag = false; 
//      boolean eomiFlag = false;
//      boolean verbOnly = false;
//      
//      boolean changedJFlag = false;
//      boolean changedEFlag = false;
//      int jstart = -1;
//      
//      int minAnalLen = 0;
//      
//      for(int e=s; e<len; e++) {
//      
//    	if(endWithSucess(repositories, s, e)) continue;
//    	
//        String text = input.substring(s, e+1); 
//        
//        repositories[s][e] = new WSOutput1(text);
//        if(repositories[s][e].isExistWord()) {
//        	jstart = -1;
//        	josaFlag = false;
//         	continue;
//        }
//        
//        char[] features = SyllableUtil.getFeature(input.charAt(e));
//        if(features[SyllableUtil.IDX_WDSURF]=='1') verbOnly = true;
//        
//        if(!josaFlag && !verbOnly && features[SyllableUtil.IDX_JOSA1]=='1') {
//        	josaFlag = true;
//        	jstart = e;
//        } else if(josaFlag && features[SyllableUtil.IDX_JOSA2]=='0') {
//        	josaFlag = false;
//        	changedJFlag = true;
//        	jstart = -1;
//        }        
//
//        if(!eomiFlag && features[SyllableUtil.IDX_YNPEOMI]=='1') {
//        	eomiFlag = true;
//        } else if(eomiFlag 
//        		&& features[SyllableUtil.IDX_EOMI2]=='0' 
//        		&& features[SyllableUtil.IDX_EOMI1]=='0') {
//        	eomiFlag = false;
//        	changedEFlag = true;
//        }
//    	
//        int cEnd = -1;
//        if(e>s && !isPartOfJosa(input, jstart, e)
//        		&& repositories[s][e-1]!=null 
//        		&& repositories[s][e-1].isExistWord() 
//        		&& (cEnd=findEndOfCompound(input,s, e))!=-1) {
//        	repositories[s][e].addMorpheme(buildAnalysisOutput(input, s, e));
//        	s = e-1;
//        	break;
//        }else if(josaFlag || eomiFlag) {
//        	List<AnalysisOutput> candidates = morphAnalyzer.analyze(text);
//        	repositories[s][e].setMorphemes(candidates);
//
//        	if(canIncreaseLen(repositories, s, e, minAnalLen)) {
//        		minAnalLen = text.length();
//        	}
//        } 
//        
//        if(!verbOnly && minAnalLen==0 &&
//        		repositories[s][e].getScore()<AnalysisOutput.SCORE_COMPOUNDS){
//        	AnalysisOutput ao = new AnalysisOutput(text, null, null, PatternConstants.PTN_N);
//        	ao.setStem(text);
//        	morphAnalyzer.confirmCNoun(ao);
//        	if(ao.getScore()>=AnalysisOutput.SCORE_COMPOUNDS) {          
//            	repositories[s][e].addMorpheme(ao);
//            	repositories[s][e].decreaseScore(WSOutput1.DEC_COMPOUND);
//        	}
//        }
//        
//        if(minAnalLen>0 && (changedJFlag || changedEFlag)) {
//        	s += minAnalLen-1;
//        	break;
//        }
//      }
//      
//    }
//    
//    List<Sentence> sentences = new ArrayList<Sentence>();    
//    sentences.addAll(transver(repositories, 0, len));
//    
//    Collections.sort(sentences, new SentenceComparator());
//    
//    for(Sentence sent : sentences) {
//    	for(WSOutput1 o : sent.getWordList()) {
//    		if(o.getMorphemes().size()==0) {
//    			System.out.println(o.getSnippet());
//    			continue;
//    		}
//    		System.out.print(o.getMorphemes().get(0)+":");
//    		for(CompoundEntry cn : o.getMorphemes().get(0).getCNounList())
//    		  System.out.print(cn.getWord()+"/");
//    		System.out.println();
//    	}
//    	System.out.println("==000===="+sent.getScore());
//    	break;
//    }
//    return new ArrayList();
//  }
  
  private AnalysisOutput buildAnalysisOutput(String input) {
  	AnalysisOutput ao = new AnalysisOutput(input, null, null, PatternConstants.PTN_N);
  	ao.setSource(input);
  	return ao;
  }
  
  private AnalysisOutput buildAnalysisOutput(String input, int s, int e) {
	String text = input.substring(s,e);
  	AnalysisOutput ao = new AnalysisOutput(text, null, null, PatternConstants.PTN_N);
  	ao.setStem(text);
  	return ao;
  }
  
  private boolean isPartOfJosa(String input, int jstart, int e) throws MorphException {
	  if(jstart==-1 || jstart==e) return false;
	  String josa = input.substring(jstart,e);
	  return DictionaryUtil.existJosa(josa);
  }
  
  private int findEndOfCompound(String input, int is, int s) throws MorphException {
	
	int len = input.length();
	int end = -1;
	if(s<is+2) return end;
	
	for(int i=s+2;i<=len;i++) {
		String temp = input.substring(s,i);
		Iterator<WordEntry> iter = DictionaryUtil.findWithPrefix(temp);
		if(iter.hasNext() && DictionaryUtil.getAllNoun(temp)!=null) {
			end = i;
		} else if(!iter.hasNext()) {
			break;
		}
	}
	
	if(end==-1) return end;
	
	if(end<len) {
		int nEnd = findEndOfCompound(input, is, end);
		if(nEnd!=-1) end = nEnd;
	}
	
	return end;
  }
  
  private boolean isPartOfWord(String input, int cur, int len) throws MorphException {	  
	  if(cur+2>len) return false;
	  Iterator<WordEntry> iter = DictionaryUtil.findWithPrefix(input.substring(cur,cur+2));	  
	  return iter.hasNext();
  }
  
  private boolean canIncreaseLen(WSOutput1[][] repositories, int s, int e, int minAnalLen) {
	  
	  if(s==e || minAnalLen==0) return true;
	  if(repositories[s][e-1]==null) return false;
	  
	  List<AnalysisOutput> prevAOs = repositories[s][e-1].getMorphemes();
	  List<AnalysisOutput> curAOs = repositories[s][e].getMorphemes();
	  
	  for(AnalysisOutput cao : curAOs) {
		  for(AnalysisOutput pao : prevAOs) {
			  if((cao.getEomi()!=null && pao.getEomi()!=null) ||
					  (cao.getJosa()!=null && pao.getJosa()!=null)) 
				  return true;
		  }
	  }
	  
	  return false;
  }
  
  private boolean endWithSucess(WSOutput1[][] repositories, int s, int e) {
	  
	  for(int i=s-1;i>=0;i--) {
		  if(repositories[i][e]!=null && 
				  repositories[i][e].getScore()==AnalysisOutput.SCORE_CORRECT)
			  return true;
	  }
	  
	  return false;
  }
  
  private List<Sentence> transver(WSOutput1[][] repositories, int s, int len) {
	  
	  List<Sentence> sentList = new ArrayList<Sentence>();
	  
	  for(int i=0;i<len-s;i++) {
		  if(repositories[s][s+i]==null) continue;
		  if(i==len-s-1) {
			  Sentence sent = new Sentence();
			  sent.addOutput(repositories[s][s+i]);
			  sentList.add(sent);
		  } else {		  
			  List<Sentence> tmpList = transver(repositories, s+i+1, len);
			  for(Sentence sent : tmpList) 
				  sent.getWordList().add(0,repositories[s][s+i]);
			  sentList.addAll(tmpList);
		  }		  
	  }
	  
	  return sentList;
  }
  

  /**
   * 조사로 끝나는 어구를 분석한다.
   * @param snippet input
   * @param js  josa start position
   * @return  resulsts
   * @throws MorphException throw exception
   */
  private List<AnalysisOutput> anlysisWithJosa(String snippet, int js) throws MorphException {

    List<AnalysisOutput> candidates = new ArrayList<AnalysisOutput>();
    if(js<1) return candidates;
    
    int jend = findJosaEnd(snippet, js);

    if(jend==-1) return candidates; // 타당한 조사가 아니라면...
  
    String input = snippet.substring(0,jend);

    boolean josaFlag = true;
    
    for(int i=input.length()-1;i>0;i--) {
      
      String stem = input.substring(0,i);
      
      String josa = input.substring(i);

      char[] feature =  SyllableUtil.getFeature(josa.charAt(0));  
      
      if(josaFlag&&feature[SyllableUtil.IDX_JOSA1]=='1') {
    	  morphAnalyzer.analysisWithJosa(stem,josa,candidates);       
      }
        
      if(josaFlag&&feature[SyllableUtil.IDX_JOSA2]=='0') josaFlag = false;
      
      if(!josaFlag) break;
      
    }
    
    if(input.length()==1) {
      AnalysisOutput o =new AnalysisOutput(input,null,null,PatternConstants.POS_NOUN,
           PatternConstants.PTN_N,AnalysisOutput.SCORE_ANALYSIS);
      candidates.add(o);
    }
    
    return candidates;
  }
 
  /**
   * 조사의 첫음절부터 조사의 2음절이상에 사용될 수 있는 음절을 조사하여
   * 가장 큰 조사를 찾는다.
   * @param snippet snippet
   * @param jstart  josa start position
   * @return  position
   * @throws MorphException throw exception
   */
  private int findJosaEnd(String snippet, int jstart) throws MorphException {
    
    int jend = jstart;

    // [것을]이 명사를 이루는 경우는 없다.
    if(snippet.charAt(jstart-1)=='것'&&(snippet.charAt(jstart)=='을')) return jstart+1;
    
    if(snippet.length()>jstart+2&&snippet.charAt(jstart+1)=='스') { // 사랑스러운, 자랑스러운 같은 경우르 처리함.
      char[] chrs = MorphUtil.decompose(snippet.charAt(jstart+2));

      if(chrs.length>=2&&chrs[0]=='ㄹ'&&chrs[1]=='ㅓ') return -1;
    }
    
    // 조사의 2음절로 사용될 수 마지막 음절을 찾는다.
    for(int i=jstart+1;i<snippet.length();i++) {
      char[] f = SyllableUtil.getFeature(snippet.charAt(i));
      if(f[SyllableUtil.IDX_JOSA2]=='0') break;
      jend = i;       
    }
        
    int start = jend;
    boolean hasJosa = false;
    for(int i=start;i>=jstart;i--) {
      String str = snippet.substring(jstart,i+1);
      if(DictionaryUtil.existJosa(str) && !findNounWithinStr(snippet,i,i+2) &&
          !isNounPart(snippet,jstart)) {
        jend = i;
        hasJosa = true;
        break;
      }
    }

    if(!hasJosa) return -1;
    
    return jend+1;
    
  }
  
  /**
   * 
   * @param str 분석하고자 하는 전체 문자열
   * @param ws  문자열에서 명사를 찾는 시작위치
   * @param es  문자열에서 명사를 찾는 끝 위치
   * @return  if founded
   * @throws MorphException throw exception
   */
  private boolean findNounWithinStr(String str, int ws, int es) throws MorphException {

    if(str.length()<es) return false;
        
    for(int i=es;i<str.length();i++) {
      char[] f = SyllableUtil.getFeature(str.charAt(i));  
      if(i==str.length() || (f[SyllableUtil.IDX_JOSA1]=='1')) {       
        return (DictionaryUtil.getWord(str.substring(ws,i))!=null);
      }
    }
    return false;
  }
  
  private boolean isNounPart(String str, int jstart) throws MorphException  {
    
    if(true) return false;
    
    for(int i=jstart-1;i>=0;i--) {      
      if(DictionaryUtil.getWordExceptVerb(str.substring(i,jstart+1))!=null)
        return true;
      
    }
    return false;    
  }
  
  private List anlysisWithEomi(String snipt, int estart) throws MorphException {

	    List<AnalysisOutput> candidates = new ArrayList();
	    
	    int eend = findEomiEnd(snipt,estart);   

	    // 동사앞에 명사분리
	    int vstart = 0;
	    for(int i=estart-1;i>=0;i--) {  
	      Iterator iter = DictionaryUtil.findWithPrefix(snipt.substring(i,estart)); 
	      if(iter.hasNext()) vstart=i;
	      else break;
	    }
	      
	    if(snipt.length()>eend &&
	        DictionaryUtil.findWithPrefix(snipt.substring(vstart,eend+1)).hasNext()) 
	      return candidates;  // 다음음절까지 단어의 일부라면.. 분해를 안한다.
	    
	    String pvword = null;
	    if(vstart!=0) pvword = snipt.substring(0,vstart);
	      
	    while(true) { // ㄹ,ㅁ,ㄴ 이기때문에 어미위치를 뒤로 잡았는데, 용언+어미의 형태가 아니라면.. 어구 끝을 하나 줄인다.
	      String input = snipt.substring(vstart,eend);
	      anlysisWithEomiDetail(input, candidates);       
	      if(candidates.size()==0) break;   
	      if(("ㄹ".equals(candidates.get(0).getEomi()) ||
	          "ㅁ".equals(candidates.get(0).getEomi()) ||
	          "ㄴ".equals(candidates.get(0).getEomi())) &&
	          eend>estart+1 && candidates.get(0).getPatn()!=PatternConstants.PTN_VM &&
	          candidates.get(0).getPatn()!=PatternConstants.PTN_NSM
	          ) {
	        eend--;
	      }else if(pvword!=null&&candidates.get(0).getPatn()>=PatternConstants.PTN_VM&& // 명사 + 용언 어구 중에.. 용언어구로 단어를 이루는 경우는 없다.
	          candidates.get(0).getPatn()<=PatternConstants.PTN_VMXMJ && DictionaryUtil.getWord(input)!=null){
	        candidates.clear();
	        break;
	      }else if(pvword!=null&&VerbUtil.verbSuffix(candidates.get(0).getStem())
	          &&DictionaryUtil.getNoun(pvword)!=null){ // 명사 + 용언화 접미사 + 어미 처리
	        candidates.clear();
	        anlysisWithEomiDetail(snipt.substring(0,eend), candidates);
	        pvword=null;
	        break;        
	      } else {
	        break;
	      }
	    }
	            
	    if(candidates.size()>0&&pvword!=null) {
	      AnalysisOutput o =new AnalysisOutput(pvword,null,null,PatternConstants.POS_NOUN,
	          PatternConstants.PTN_N,AnalysisOutput.SCORE_ANALYSIS);  
	      morphAnalyzer.confirmCNoun(o,false);
	      
	      List<CompoundEntry> cnouns = o.getCNounList();
	      if(cnouns.size()==0) {
	        boolean is = DictionaryUtil.getWordExceptVerb(pvword)!=null;
	        cnouns.add(new CompoundEntry(pvword,0,is));
	      } 
	      
	      for(AnalysisOutput candidate : candidates) {
	        candidate.getCNounList().addAll(cnouns);
	        candidate.getCNounList().add(new CompoundEntry(candidate.getStem(),0,true));
	        candidate.setStem(pvword+candidate.getStem()); // 이렇게 해야 WSOutput 에 복합명사 처리할 때 정상처리됨
	      }
	      
	    }

	    return candidates;
	  }
  
  /**
   * 어미의 첫음절부터 어미의 1음절이상에 사용될 수 있는 음절을 조사하여
   * 가장 큰 조사를 찾는다.
   * @param snippet snippet
   * @return  start position
   * @throws MorphException throw exception
   */
  private int findEomiEnd(String snippet, int estart) throws MorphException {
    
    int jend = 0;
    
    String tail = null;
    char[] chr = MorphUtil.decompose(snippet.charAt(estart));
    if(chr.length==3 && (chr[2]=='ㄴ')) {
      tail = '은'+snippet.substring(estart+1);
    }else if(chr.length==3 && (chr[2]=='ㄹ')) {
      tail = '을'+snippet.substring(estart+1);     
    }else if(chr.length==3 && (chr[2]=='ㅂ')) {
      tail = '습'+snippet.substring(estart+1);
    }else {
      tail = snippet.substring(estart);
    }       

    // 조사의 2음절로 사용될 수 마지막 음절을 찾는다.
    int start = 0;
    for(int i=1;i<tail.length();i++) {
      char[] f = SyllableUtil.getFeature(tail.charAt(i)); 
      if(f[SyllableUtil.IDX_EOGAN]=='0') break;
      start = i;        
    }
          
    for(int i=start;i>0;i--) { // 찾을 수 없더라도 1음절은 반드시 반환해야 한다.
      String str = tail.substring(0,i+1); 
      char[] chrs = MorphUtil.decompose(tail.charAt(i));  
      if(DictionaryUtil.existEomi(str) || 
          (i<2&&chrs.length==3&&(chrs[2]=='ㄹ'||chrs[2]=='ㅁ'||chrs[2]=='ㄴ'))) { // ㅁ,ㄹ,ㄴ이 연속된 용언은 없다, 사전을 보고 확인을 해보자
        jend = i;
        break;
      }
    }
    
    return estart+jend+1;
    
  }

  private void anlysisWithEomiDetail(String input, List<AnalysisOutput> candidates ) 
  throws MorphException {

    boolean eomiFlag = true;
    
    int strlen = input.length();
    
    char ch = input.charAt(strlen-1);
    char[] feature =  SyllableUtil.getFeature(ch);
    
    if(feature[SyllableUtil.IDX_YNPNA]=='1'||feature[SyllableUtil.IDX_YNPLA]=='1'||
        feature[SyllableUtil.IDX_YNPMA]=='1')
    	morphAnalyzer.analysisWithEomi(input,"",candidates);
    
    for(int i=strlen-1;i>0;i--) {
      
      String stem = input.substring(0,i);
      String eomi = input.substring(i);

      feature =  SyllableUtil.getFeature(eomi.charAt(0));   
      
      if(eomiFlag) {      
    	  morphAnalyzer.analysisWithEomi(stem,eomi,candidates);
      }     
      
      if(eomiFlag&&feature[SyllableUtil.IDX_EOMI2]=='0') eomiFlag = false;
      
      if(!eomiFlag) break;
    }
    
  }
  

  
}
