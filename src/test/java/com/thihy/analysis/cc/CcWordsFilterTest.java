/*
 * Copyright 2015 thihy
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.thihy.analysis.cc;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKWidthFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.junit.Test;

public class CcWordsFilterTest {

	@Test
	public void test() throws IOException {
		final String[] words = { "U", "U盘", "AU" };
		final String[] texts = { "U盘", "u盘是个好东西", "A U 盘" };

		CcWordSet wordSet = createWordSet(words);
		CcArgs args = new CcArgs(wordSet, false);
		Analyzer ccAnalyzer = createCcAnalyzer(args);
		for (String text : texts) {
			try (TokenStream ts = ccAnalyzer.tokenStream("", text)) {
				System.out.println("=================================================================================");
				System.out.println(">>>>>>>>" + text);
				AnalysisTestHelper.printResultOfTokenStream(System.out, ts);
				System.out.println();
			}
		}
	}

	private Analyzer createCcAnalyzer(final CcArgs args) {
		return new Analyzer() {

			@Override
			protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
				StandardTokenizer tokenizer = new StandardTokenizer(reader);
				TokenStream tokenStream = tokenizer;
				tokenStream = new LowerCaseFilter(tokenStream);
				tokenStream = new CJKWidthFilter(tokenStream);
				tokenStream = new CcWordsFilter(tokenStream, args);
				return new TokenStreamComponents(tokenizer, tokenStream);
			}
		};
	}

	private CcWordSet createWordSet(String[] words) throws IOException {
		Analyzer analyzer = createWordSetAnalyzer();
		CharsRefBuilder textCharsRefBuilder = new CharsRefBuilder();
		CcWordSet.Builder ccWordSetBuilder = new CcWordSet.Builder();
		for (String word : words) {
			CharsRef charsRef = analyze(analyzer, word);
			textCharsRefBuilder.clear();
			textCharsRefBuilder.append(word);
			ccWordSetBuilder.add(charsRef, textCharsRefBuilder.get());
		}
		return ccWordSetBuilder.build();
	}

	private Analyzer createWordSetAnalyzer() {
		return new Analyzer() {

			@Override
			protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
				StandardTokenizer tokenizer = new StandardTokenizer(reader);
				TokenStream tokenStream = tokenizer;
				tokenStream = new LowerCaseFilter(tokenStream);
				tokenStream = new CJKWidthFilter(tokenStream);
				return new TokenStreamComponents(tokenizer, tokenStream);
			}
		};
	}

	private CharsRef analyze(Analyzer analyzer, String text) throws IOException {
		CharsRefBuilder charsRefBuilder = new CharsRefBuilder();
		try (TokenStream ts = analyzer.tokenStream("", text)) {
			CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
			PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);
			ts.reset();
			while (ts.incrementToken()) {
				int length = termAtt.length();
				if (length == 0) {
					throw new IllegalArgumentException("term: " + text + " analyzed to a zero-length token");
				}
				charsRefBuilder.grow(charsRefBuilder.length() + length + 1); /* current + word + separator */
				if (charsRefBuilder.length() > 0) {
					charsRefBuilder.append(CcWordSet.WORD_SEPARATOR);
				}
				charsRefBuilder.append(termAtt);
			}
			ts.end();
		}
		if (charsRefBuilder.length() == 0) {
			return null;
		}
		charsRefBuilder.append(CcWordSet.WORD_END);
		return charsRefBuilder.get();
	}
}
