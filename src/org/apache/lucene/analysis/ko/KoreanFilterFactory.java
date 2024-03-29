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

import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 * Factory for {@link org.apache.lucene.analysis.ko.KoreanFilter}.
 * <pre class="prettyprint">
 * &lt;fieldType name="text_kr" class="solr.TextField"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.KoreanTokenizerFilterFactory"/&gt;
 *     &lt;filter class="solr.KoreanFilter"
 *       bigrammable="true"
 *       hasOrigin="true"
 *       hasCNoun="true"
 *       exactMatch="false"
 *     /&gt;
 *   &lt;/filter&gt;
 * &lt;/fieldType&gt;
 * </pre>
 */

public class KoreanFilterFactory extends TokenFilterFactory {

  private static final String BIGRAMMABLE_PARAM = "bigrammable";

  private static final String HAS_ORIGIN_PARAM = "hasOrigin";

  private static final String HAS_COMPOUND_NOUN_PARAM = "hasCNoun";

  // Decides whether the original compound noun is returned or not if analyzed morphologically
  private static final String EXACT_MATCH_PARAM = "exactMatch";
  
  private static final String IS_POSITION_INC = "incPosition";
  
  private static final String IS_QUERY_MODE = "queryMode";

  private static final String IS_DO_DECOMPOUND = "doDecompound";
  
  private boolean bigrammable;

  private boolean hasOrigin;

  private boolean hasCNoun;

  private boolean exactMatch;
  
  private boolean isPositionInc;
  
  private boolean queryMode;
  
  private boolean doDecompound;

  /**
   * Initialize this factory via a set of key-value pairs.
   */
  public KoreanFilterFactory(Map<String, String> args) {
    super(args);
    init(args);
  }

  public void init(Map<String, String> args) {
    bigrammable = getBoolean(args, BIGRAMMABLE_PARAM, true);
    hasOrigin = getBoolean(args, HAS_ORIGIN_PARAM, true);
    exactMatch = getBoolean(args, EXACT_MATCH_PARAM, false);
    hasCNoun = getBoolean(args, HAS_COMPOUND_NOUN_PARAM, true);
    isPositionInc = getBoolean(args, IS_POSITION_INC, true);
    queryMode = getBoolean(args, IS_QUERY_MODE, false);
    doDecompound = getBoolean(args, IS_DO_DECOMPOUND, true);
  }

  public TokenStream create(TokenStream tokenstream) {
    return new KoreanFilter(tokenstream, bigrammable, 
        hasOrigin, exactMatch, hasCNoun, isPositionInc, 
        queryMode, doDecompound);
  }
  
}
