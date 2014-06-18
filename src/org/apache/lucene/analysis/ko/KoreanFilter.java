package org.apache.lucene.analysis.ko;

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

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.morph.AnalysisOutput;
import org.apache.lucene.analysis.ko.morph.CompoundEntry;
import org.apache.lucene.analysis.ko.morph.CompoundNounAnalyzer;
import org.apache.lucene.analysis.ko.morph.MorphAnalyzer;
import org.apache.lucene.analysis.ko.morph.MorphException;
import org.apache.lucene.analysis.ko.morph.PatternConstants;
import org.apache.lucene.analysis.ko.morph.WordEntry;
import org.apache.lucene.analysis.ko.morph.WordSpaceAnalyzer;
import org.apache.lucene.analysis.ko.utils.DictionaryUtil;
import org.apache.lucene.analysis.ko.utils.HanjaUtils;
import org.apache.lucene.analysis.ko.utils.Utilities;
import org.apache.lucene.analysis.ko.IndexWord;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.standard.ClassicTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

public class KoreanFilter extends TokenFilter {

  private LinkedList<IndexWord> morphQueue;
  
  private MorphAnalyzer morph;
  
  private WordSpaceAnalyzer wsAnal;
  
  private boolean bigrammable = true;
  
  private boolean hasOrigin = false;
  
  private boolean originCNoun = true;
  
  private boolean exactMatch = false;
  
  private boolean isPositionInc = true;
  
  private boolean queryMode = false;
  
  private boolean doDecompound = true;
  
  private char[] curTermBuffer;
    
  private int curTermLength;
    
  private String curType;
    
  private String curSource;
    
  private int tokStart;
    
  private int hanStart = 0; // 한글의 시작 위치, 복합명사일경우
    
  private int chStart = 0;
    
  private CompoundNounAnalyzer cnAnalyzer = new CompoundNounAnalyzer();
  
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
  private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    
  private static final String APOSTROPHE_TYPE = ClassicTokenizer.TOKEN_TYPES[ClassicTokenizer.APOSTROPHE];
  private static final String ACRONYM_TYPE = ClassicTokenizer.TOKEN_TYPES[ClassicTokenizer.ACRONYM];
    
  public KoreanFilter(TokenStream input) {
    super(input);
    morphQueue =  new LinkedList<IndexWord>();
    morph = new MorphAnalyzer();
    wsAnal = new WordSpaceAnalyzer();
    cnAnalyzer.setExactMach(false);
  }

  /**
   * 
   * @param input  input token stream
   * @param bigram  Whether the bigram index term return or not.
   */
  public KoreanFilter(TokenStream input, boolean bigram) {
    this(input);  
    bigrammable = bigram;
  }
  
  public KoreanFilter(TokenStream input, boolean bigram, boolean has) {
    this(input, bigram);
    hasOrigin = has;
  }
  
  public KoreanFilter(TokenStream input, boolean bigram, boolean has, boolean match) {
    this(input, bigram,has);
    this.exactMatch = match;
  }
  
  public KoreanFilter(TokenStream input, boolean bigram, boolean has, boolean match, boolean cnoun) {
    this(input, bigram,has, match);
    this.originCNoun = cnoun;
  }

  public KoreanFilter(TokenStream input, boolean bigram, boolean has, boolean match, boolean cnoun, boolean isPositionInc) {
    this(input, bigram,has, match, cnoun);
    this.isPositionInc = isPositionInc;
  }
  
  public KoreanFilter(TokenStream input, boolean bigram, boolean has, boolean match, 
      boolean cnoun, boolean isPositionInc, boolean queryMode) 
  {
    this(input, bigram,has, match, cnoun,isPositionInc);
    this.queryMode = queryMode;
  }
  
  /**
   * 
   * @param input input text
   * @param bigram  whether if should be bigrammed
   * @param has   whether if the input text should be extracted as indexing keyword
   * @param match whether if should be decompounded only when all unit words exist in dictionary
   * @param cnoun whether if the compound noun should be extracted as indexing keyword
   * @param isPositionInc whether if position should be increased
   * @param queryMode   whether if this is query mode
   * @param decompound  where if the decompound nouns shoud be extracted as indexing keyword, If you want to use phrase query and proximate query, this value should be set to false.
   */
  public KoreanFilter(TokenStream input, boolean bigram, boolean has, boolean match, 
      boolean cnoun, boolean isPositionInc, boolean queryMode, boolean decompound) 
  {
    this(input, bigram,has, match, cnoun,isPositionInc,queryMode);
    if(!decompound) {
      this.originCNoun=true;
      this.bigrammable=false;
    }
    this.doDecompound = decompound;
  }
  
  public final boolean incrementToken() throws IOException {

    if(curTermBuffer!=null&&morphQueue.size()>0) {
      setTermBufferByQueue(false);
      return true;
    }

    if(!input.incrementToken()) return false;
    
    curTermBuffer = termAtt.buffer().clone();
    curTermLength = termAtt.length();
    tokStart = offsetAtt.startOffset();    
    curType = typeAtt.type();

    try {      
      if(KoreanTokenizer.TOKEN_TYPES[KoreanTokenizer.KOREAN].equals(curType)) {            
        analysisKorean(new String(curTermBuffer,0,termAtt.length()));
      } else if(KoreanTokenizer.TOKEN_TYPES[KoreanTokenizer.CHINESE].equals(curType)) {
        analysisChinese(new String(curTermBuffer,0,termAtt.length()));
      } else {
        analysisETC(new String(curTermBuffer,0,termAtt.length()));
      }        
    }catch(MorphException e) {
    	e.printStackTrace();
      throw new IOException("Korean Filter MorphException\n"+e.getMessage());
    }

    if(morphQueue!=null&&morphQueue.size()>0) {
      setTermBufferByQueue(true);  
    } else {
      return incrementToken();
    }

    return true;

  }
  
  /**
   * queue에 저장된 값으로 buffer의 값을 복사한다.
   */
  private void setTermBufferByQueue(boolean isFirst) {
    
    clearAttributes();
        
    IndexWord iw = morphQueue.removeFirst();

    termAtt.copyBuffer(iw.getWord().toCharArray(), 0, iw.getWord().length());
    offsetAtt.setOffset(iw.getOffset(), iw.getOffset() + iw.getWord().length());
    
    int inc = isPositionInc ?  iw.getIncrement() : 0;
    
    posIncrAtt.setPositionIncrement(inc);      
    
  }
  
  /**
   * 한글을 분석한다.
   * @throws MorphException exception
   */
  private void analysisKorean(String input) throws MorphException {

    List<AnalysisOutput> outputs = morph.analyze(input);
    if(outputs.size()==0) return;
    
    Map<String,IndexWord> map = new LinkedHashMap<String,IndexWord>();
    if(hasOrigin) map.put("0:"+input, new IndexWord(input,offsetAtt.startOffset()));

    boolean ignoreAutoSpace = outputs.get(0).getScore()>=AnalysisOutput.SCORE_COMPOUNDS;
    if(queryMode) ignoreAutoSpace = outputs.get(0).getScore()>=AnalysisOutput.SCORE_CORRECT;
    
    ignoreAutoSpace = true;
    if(ignoreAutoSpace) 
    {
      extractKeyword(outputs,offsetAtt.startOffset(), map, 0, false);      
    } 
    else 
    {      
      try
      {
        List<AnalysisOutput> list = wsAnal.analyze(input);
        
            
        if(list.size()>1 && wsAnal.getOutputScore(list)>AnalysisOutput.SCORE_ANALYSIS) {
          int offset = 0;
          for(int ii=0; ii<list.size(); ii++) {
            AnalysisOutput o = list.get(ii);
            
            int inc = ii==0&&map.size()>0 ? 0 : 1;
            if(hasOrigin) map.put((offsetAtt.startOffset()+offset)+":"+o.getSource(), new IndexWord(o.getSource(),offsetAtt.startOffset()+offset,inc));
            List<AnalysisOutput> results = new ArrayList<AnalysisOutput>();
            results.add(o);
            extractKeyword(results,offsetAtt.startOffset()+offset, map, ii, (ii!=0));
            offset += o.getSource().length();
          }       
        } else {
          List<AnalysisOutput> results = new ArrayList<AnalysisOutput>();	
          results.addAll(outputs);
          extractKeyword(results, offsetAtt.startOffset(), map, 0, false);
        }
        
      }catch(Exception e) {
        extractKeyword(outputs.subList(0, 1), offsetAtt.startOffset(), map, 0, false);
      }
      
    }
        
    Iterator<String> iter = map.keySet().iterator();
    
    while(iter.hasNext()) {      
      String text = iter.next();      
      morphQueue.add(map.get(text));
    }
  
  }
  
  private void extractKeyword(List<AnalysisOutput> outputs, int startoffset, 
		  Map<String,IndexWord> map, int position, boolean SpaceAdded) 
      throws MorphException 
  {

    int maxDecompounds = 0;
    int maxStem = 0;
    
    // extracting keyword from compound noun that is not decompound.
    for(AnalysisOutput output : outputs) 
    {
      if(output.getStem().length()>maxStem) maxStem = output.getStem().length();
        
      if(output.getPos()==PatternConstants.POS_VERB) continue; // extract keywords from only noun

      if(output.getCNounList().size()>maxDecompounds) maxDecompounds = output.getCNounList().size();
      
      if(!originCNoun&&output.getCNounList().size()>0) continue; // except compound nound
      int inc = !SpaceAdded&&map.size()>0 ? 0 : 1;

      String key = startoffset+":"+output.getStem();
      if(!map.containsKey(key)) 
    	  map.put(key, new IndexWord(output.getStem(),startoffset,inc));
      
      // query time이고 띄어쓰기된 경우가 아니라면 첫번째 후보로만 검색어를 추출한다.
      if(queryMode && !SpaceAdded) break;
    }

    // if the inputed text can be decompound
    if(doDecompound && maxDecompounds>1) 
    {      
      for(int i=0; i<maxDecompounds; i++) 
      {
        position += i;
        
        int cPosition = position;
        for(AnalysisOutput output : outputs) 
        {
          if(output.getPos()==PatternConstants.POS_VERB ||
              output.getCNounList().size()<=i) continue;  
          
          // query mode 에서는 1글자가 분해된 경우, 한글자는 색인어로 추출하지 않고,
          // 앞 또는 뒤의 문자열과 결합하여 색인어를 추출한다.
          if((!hasOrigin && !originCNoun && queryMode)  
        		  && doNotOnQueryMode(output.getCNounList(), i)) {
        	  continue;
          }
          
          CompoundEntry cEntry = output.getCNounList().get(i);
          
          int cStartoffset = getStartOffset(output, i) + startoffset;
          int inc = (i==0) && map.size()>0 ? 0 : 1;
                 
          IndexWord indexWord = normalizeWord(output.getCNounList(), i, cStartoffset, inc);
          if(cEntry.getWord().length()==1 && indexWord.getIncrement()==0) cPosition -= 1;
          
          String key = indexWord.getOffset()+":"+indexWord.getWord();
          if(i>0&&output.getCNounList().get(i-1).getWord().length()==1) {
        	  String word = output.getCNounList().get(i-1).getWord()+output.getCNounList().get(i).getWord();
        	  WordEntry wEntry = DictionaryUtil.getCompoundNoun(word);
        	  if(wEntry!=null) indexWord.setIncrement(0);// 공사가계약--> "계약"은 "가계약"과 같은 위치
          }
          
          if(!map.containsKey(key)) map.put(key, indexWord);
                   
          if(bigrammable&&!cEntry.isExist()) 
            cPosition = addBiagramToMap(cEntry.getWord(), cStartoffset, map, cPosition);
          
          // query time이고 띄어쓰기된 경우가 아니라면 첫번째 후보로만 검색어를 추출한다.
          if(queryMode && !SpaceAdded) break;
        }                
      }      
    } 
    else 
    {
      for(AnalysisOutput output : outputs) 
      {
        if(output.getPos()==PatternConstants.POS_VERB) continue;
        
        if(bigrammable&&output.getScore()<AnalysisOutput.SCORE_COMPOUNDS) 
          addBiagramToMap(output.getStem(), startoffset, map, position); 
        
        // query time이고 띄어쓰기된 경우가 아니라면 첫번째 후보로만 검색어를 추출한다.
        if(queryMode && !SpaceAdded) break;
      }  
    }    
  }
  
  /**
   * 형태소분석하기 전의 문자열(hasOrigin), 복합명사 분해하기 전의 문자열(originCNoun)을 반환하는 경우가 아니라면..
   * @param decompounds
   * @param index
   * @return
   * @throws MorphException
   */
  private boolean doNotOnQueryMode(List<CompoundEntry> decompounds, int index) throws MorphException {
	 
	  // 첫번째 글자(index=0)가 1글자로 분해된 경우라면 두번째 글자(index=1)과 결합하여 색인어 추출, 예)가+건물
	  // 첫번째 글자(index=0)에서 색인어로 추출되었기 때문에 index=1에서는 색인어를 추출하지 않는다.
	  if(index==1 && decompounds.get(index-1).getWord().length()==1) {
		  return true;
	  }
		 	  
	  // 앞의 글자가 1글자로 분해되었고, 현재의 글자와 결합하여 사전에 있는 경우라면 앞의 글자와 결합하여 색인어로 추출한다. 예)공장+가+건물
	  if(index>1 && decompounds.get(index-1).getWord().length()==1) {
		  String word = decompounds.get(index-1).getWord()+decompounds.get(index).getWord();
		  if(DictionaryUtil.getCompoundNoun(word)!=null) { // 복합명사 사전에 존재한다.
			  return true;
		  }
	  }

	  // [공장+가+건물]의 경우, [가+건물]이 사전에 존재한다면 현재의 단어는 색인어로 추출하여야 한다.
	  if(index+2<decompounds.size() && decompounds.get(index+1).getWord().length()==1) {
		  String word = decompounds.get(index+1).getWord()+decompounds.get(index+2).getWord();
		  if(DictionaryUtil.getCompoundNoun(word)!=null) { // 복합명사 사전에 존재한다.
			  return false;
		  }
	  }
	  
	  // [가게+문+가+건물]의 경우가 아니고, 다음 글자가 1글자로 분해된 경우라면  
	  // 공장+가+건물인 경우, 공장을 색인어로 추출하지 못한다.
	  if(index<=decompounds.size()-2 && decompounds.get(index).getWord().length()!=1 &&
			  decompounds.get(index+1).getWord().length()==1) {
		  return true;
	  }
	  
	  return false;
  }
  
  /**
   * 1글자 분해된 경우 색인어를 추출하는 로직
   * @param decompounds
   * @param index
   * @param cStartoffset
   * @param inc
   * @return
   * @throws MorphException
   */
  private IndexWord normalizeWord(List<CompoundEntry> decompounds, int index,
		  int cStartoffset, int inc) throws MorphException {
	  
	  String word = null;
	  
	  // 1글자가 아니므로 현재의 단어를 색인어로 추출한다. 
	  //queryMode 와  관계는?
	  if(decompounds.get(index).getWord().length()!=1) {
		  word = decompounds.get(index).getWord();
		  return new IndexWord(word,cStartoffset,inc);
	  }
	    
	  if(index==0 && decompounds.get(index).getWord().length()==1) {
		  word = decompounds.get(index).getWord()+decompounds.get(index+1).getWord();
		  return new IndexWord(word,cStartoffset,inc);
	  }
	  
	  // 마지막이 한글자라면 무조건 앞의 글자와 결합한다.
	  // index가 1이라면.. 
	  // 
	  if(index==decompounds.size()-1 && decompounds.get(index).getWord().length()==1) {
		  word = decompounds.get(index-1).getWord()+decompounds.get(index).getWord();
		  if(hasOrigin || originCNoun) inc=0;
		  cStartoffset -= decompounds.get(index-1).getWord().length();
		  return new IndexWord(word,cStartoffset,inc);
	  }

	  if(index<decompounds.size()-1 && decompounds.get(index).getWord().length()==1) {
		  word = decompounds.get(index).getWord()+decompounds.get(index+1).getWord();
		  if(DictionaryUtil.getCompoundNoun(word)!=null) {
			  return new IndexWord(word,cStartoffset,inc);
		  }		  
	  }

	  // 한글자들은 앞의 글자와 결합한다. 따라서 원래 복합명사를 출력한다면.. inc는 0이 되고 출력하지 않는다면 1이 된다
	  // 그러나  전체 입력테스트를 출력할 때도
	  word = decompounds.get(index-1).getWord()+decompounds.get(index).getWord();
	  if(!queryMode) inc=0; // position은 무조건 증가하지 않는다.
	  cStartoffset -= decompounds.get(index-1).getWord().length();
	  return new IndexWord(word,cStartoffset,inc);
	  
  }
  
  private int addBiagramToMap(String input, int startoffset, Map map, int position) {
    int offset = 0;
    int strlen = input.length();
    if(strlen<2) return position;
    
    while(offset<strlen-1) {
      
      int inc = offset==0 ? 0 : 1;
      
      if(isAlphaNumChar(input.charAt(offset))) {
        String text = findAlphaNumeric(input.substring(offset));
        String key = startoffset+":"+text;
        if(!map.containsKey(key))
        	map.put(key,  new IndexWord(text,startoffset+offset,inc));
        offset += text.length();
      } else {
        String text = input.substring(offset,
            offset+2>strlen?strlen:offset+2);
        String key = startoffset+":"+text;
        if(!map.containsKey(key))
        	map.put(key,  new IndexWord(text,startoffset+offset,inc));
        offset++;
      }
      
      position += 1;
    }
    
    return position-1;
  }
  
  /**
   * return the start offset of current decompounds entry.
   * @param output  morphlogical analysis output
   * @param index     the index of current decompounds entry
   * @return        the start offset of current decoumpounds entry
   */
  private int getStartOffset(AnalysisOutput output, int index) {    
    int sOffset = 0;
    for(int i=0; i<index;i++) {
      sOffset += output.getCNounList().get(i).getWord().length();
    }
    return sOffset;
  }
  
  private String findAlphaNumeric(String text) {
    int pos = 0;
    for(int i=0;i<text.length();i++) {
      if(!isAlphaNumChar(text.charAt(i))) break;
      pos++;
    }    
    if(pos<text.length()) pos += 1;
    
    return text.substring(0,pos);
  }
  
  /**
   * 한자는 2개이상의 한글 음으로 읽혀질 수 있다.
   * 두음법칙이 아님.
   * @param term  term
   * @throws MorphException exception
   */
  private void analysisChinese(String term) throws MorphException {  
    
    morphQueue.add(new IndexWord(term,offsetAtt.startOffset()));
    if(term.length()<2) return; // 1글자 한자는 색인어로 한글을 추출하지 않는다.
    
    List<StringBuffer> candiList = new ArrayList<StringBuffer>();
    candiList.add(new StringBuffer());
    
    for(int i=0;i<term.length();i++) {

      char[] chs = HanjaUtils.convertToHangul(term.charAt(i));        
      if(chs==null) continue;
      
      List<StringBuffer> removeList = new ArrayList<StringBuffer>(); // 제거될 후보를 저장  
      
      int caniSize = candiList.size();
      
      for(int j=0;j<caniSize;j++) { 
        String origin = candiList.get(j).toString();

        for(int k=0;k<chs.length;k++) { // 추가로 생성된 음에 대해서 새로운 텍스트를 생성한다.
          
          if(k==4) break; // 4개 이상의 음을 가지고 있는 경우 첫번째 음으로만 처리를 한다.
          
          StringBuffer sb = candiList.get(j);
          if(k>0) sb = new StringBuffer(origin);
          
          sb.append(chs[k]);          
          if(k>0)  candiList.add(sb);
          
          Iterator<WordEntry> iter = DictionaryUtil.findWithPrefix(sb.toString());
          if(!iter.hasNext()) // 사전에 없으면 삭제 후보
            removeList.add(sb);    
        }        
      }            

      if(removeList.size()==candiList.size()) { // 사전에서 찾은 단어가 하나도 없다면.. 
        candiList = candiList.subList(0, 1); // 첫번째만 생성하고 나머지는 버림
      } 
      
      for(StringBuffer rsb : removeList) {
        if(candiList.size()>1) candiList.remove(rsb);
      }
    }

    int maxCandidate = 5;
    if(candiList.size()<maxCandidate) maxCandidate=candiList.size();
    
    for(int i=0;i<maxCandidate;i++) {
      morphQueue.add(new IndexWord(candiList.get(i).toString(),offsetAtt.startOffset(),0));
    }
    
    Map<String, String> cnounMap = new HashMap<String, String>();
    
    // 추출된 명사가 복합명사인 경우 분리한다.
    for(int i=0;i<maxCandidate;i++) {
    	
      if(!Utilities.hangulOnly(candiList.get(i).toString())) continue;
      
      List<CompoundEntry> results = confirmCNoun(candiList.get(i).toString());
      
      int pos = 0;
      int offset = 0;
      for(int ii=0;ii<results.size();ii++) {  
    	CompoundEntry entry = results.get(ii);
        pos += entry.getWord().length();
        if(pos>term.length()) pos = term.length();
        
        if(cnounMap.get(entry.getWord())!=null) continue;
         
        int posInc = ii==0? 0 : 1;
        
        try{
        // 한글과 매치되는 한자를 짤라서 큐에 저장한다.           
        morphQueue.add(new IndexWord(term.substring(offset,pos),offsetAtt.startOffset()+offset,posInc));
        }catch(Exception e) {
          System.out.println("term:"+term+" made exception");
          e.printStackTrace();
          throw new MorphException("term:"+term+" made exception");
        }
        cnounMap.put(entry.getWord(), entry.getWord());
         
//        if(entry.getWord().length()<2) continue; //  한글은 2글자 이상만 저장한다.
         
        // 분리된 한글을 큐에 저장한다.  
        morphQueue.add(new IndexWord(entry.getWord(),offsetAtt.startOffset()+offset,0));
         
        offset = pos;
      }       
    }    
  }
  
  private List<CompoundEntry> confirmCNoun(String input) throws MorphException {
    
    WordEntry cnoun = DictionaryUtil.getAllNoun(input);
    if(cnoun!=null && cnoun.getFeature(WordEntry.IDX_NOUN)=='2') {
      return cnoun.getCompounds();
    }
       
    return cnAnalyzer.analyze(input);
  }
  
  private void analysisETC(String term) throws MorphException {

    final char[] buffer = termAtt.buffer();
    final int bufferLength = termAtt.length();
    final String type = typeAtt.type();

    if (type == APOSTROPHE_TYPE &&      // remove 's
        bufferLength >= 2 &&
        buffer[bufferLength-2] == '\'' &&
        (buffer[bufferLength-1] == 's' || buffer[bufferLength-1] == 'S')) {
      // Strip last 2 characters off
      morphQueue.add(new IndexWord(term.substring(0,bufferLength - 2),offsetAtt.startOffset()));
    } else if (type == ACRONYM_TYPE) {      // remove dots
      int upto = 0;
      for(int i=0;i<bufferLength;i++) {
        char c = buffer[i];
        if (c != '.')
          buffer[upto++] = c;
      }
      morphQueue.add(new IndexWord(term.substring(0,upto),offsetAtt.startOffset()));
    } else {
      morphQueue.add(new IndexWord(term,offsetAtt.startOffset()));
    }
  }
  
  private boolean isAlphaNumChar(int c) {
    if((c>=48&&c<=57)||(c>=65&&c<=122)) return true;    
    return false;
  }
  
  public void setHasOrigin(boolean has) {
    hasOrigin = has;
  }

  public void setExactMatch(boolean match) {
    this.exactMatch = match;
  }
}
