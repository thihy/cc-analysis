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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefHash;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FST.Arc;
import org.apache.lucene.util.fst.Util;

public final class CcWordsFilter extends TokenFilter {
	public static final String TOKEN_TYPE = "CC";

	private static final String ALPHANUM = StandardTokenizer.TOKEN_TYPES[StandardTokenizer.ALPHANUM];

	private final CcArgs args;

	private final IntsRefBuilder scratchInfs = new IntsRefBuilder();

	private final FST<Long> fst;
	private final FST.BytesReader fstReader;
	private final BytesRefHash fstWords;
	private final BytesRef scratchWordBytesRef;
	private final FST.Arc<Long> fstFirstArc;
	private final FST.Arc<Long> scratchArc, scratchArcOfSep, scatchArcOfEnd;

	private final Queue<PendingOutput> pendingOutputs;
	private int lastEndOffset = -1;
	private String lastInputType = null;

	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
	private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
	private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
	private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
	private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

	protected CcWordsFilter(TokenStream input, CcArgs args) {
		super(input);
		this.args = args;
		//
		this.fst = args.wordSet.fst;
		this.fstReader = args.wordSet.fst.getBytesReader();
		this.fstWords = args.wordSet.words;
		this.fstFirstArc = new FST.Arc<>();
		this.fst.getFirstArc(fstFirstArc);
		this.scratchWordBytesRef = new BytesRef();
		this.scratchArc = new FST.Arc<>();
		this.scratchArcOfSep = new FST.Arc<>();
		this.scatchArcOfEnd = new FST.Arc<>();
		//
		this.pendingOutputs = new LinkedList<>();
	}

	@Override
	public boolean incrementToken() throws IOException {
		while (true) {
			if (hasPendingTokens()) {
				flushPendingToken();
				return true;
			}

			// ASSERT: no pending tokens

			if (!input.incrementToken()) {
				return false;
			}

			PendingInputToken input = createPendingToken();
			boolean skipMatch = false;
			boolean stopMatch = false;

			// 如果之前存在Token，则需要判断是否是连续的，如果产生交集，则不进行词典检测，如果断层，则结束词典检测
			if (lastEndOffset >= 0) {
				int currentStartOffset = input.startOffset;
				if (currentStartOffset < lastEndOffset) {
					skipMatch = true;
				} else if (currentStartOffset > lastEndOffset) {
					// 当存在英文单词，则允许结束1个偏移量。
					if (currentStartOffset - lastEndOffset == 1 && (lastInputType == ALPHANUM || input.type == ALPHANUM)) {
						// OK
					} else {
						stopMatch = true;
					}
				}
			}
			lastEndOffset = input.endOffset;
			lastInputType = input.type;

			//
			if (!skipMatch) {
				for (Iterator<PendingOutput> pendingOutputsIter = pendingOutputs.iterator(); pendingOutputsIter.hasNext();) {
					PendingOutput pendingOutput = pendingOutputsIter.next();
					if (stopMatch) {
						pendingOutput.arc = null;
					} else {
						processPendingOutput(input, pendingOutput, true);
					}
				}
			}
			//
			PendingOutput pendingOutput = new PendingOutput(input.startOffset, input.positionIncrement, input.positionLength);
			pendingOutput.arc.copyFrom(fstFirstArc);
			processPendingOutput(input, pendingOutput, false);
			pendingOutput.tokens.add(new PendingOutputToken(input));
			this.pendingOutputs.add(pendingOutput);
		}
	}

	private void processPendingOutput(PendingInputToken input, PendingOutput pendingOutput, boolean matchEnd) throws IOException {
		if (pendingOutput.arc == null) {
			return;
		}

		scratchArc.copyFrom(pendingOutput.arc);
		FST.Arc<Long> arcOfSep = null;
		FST.Arc<Long> arcOfEnd = null;

		Arc<Long> arcOfToken = matchToken(scratchArc, input.text);
		if (arcOfToken == null) {
			pendingOutput.arc = null;
			return;
		}

		scratchArcOfSep.copyFrom(arcOfToken);
		arcOfSep = matchSeparator(scratchArcOfSep);
		if (arcOfSep == null) {
			pendingOutput.arc = null;
		} else {
			pendingOutput.arc.copyFrom(arcOfSep);
			pendingOutput.positionLength += input.positionLength;
		}

		if (matchEnd) {
			scatchArcOfEnd.copyFrom(arcOfToken);
			arcOfEnd = matchEnd(scatchArcOfEnd);

			if (arcOfEnd != null) {
				int wordsOrd = arcOfEnd.output.intValue();
				this.fstWords.get(wordsOrd, scratchWordBytesRef);
				CharsRefBuilder charsRef = new CharsRefBuilder();
				charsRef.copyUTF8Bytes(scratchWordBytesRef);
				CharsRef text = charsRef.get();
				String type = TOKEN_TYPE;
				int endOffset = input.endOffset;
				int positionIncrement = pendingOutput.positionIncrement;
				int positionLength = pendingOutput.positionLength;
				PendingOutputToken token = new PendingOutputToken(text, type, endOffset, positionIncrement, positionLength);
				pendingOutput.tokens.add(token);
			}
		}
	}

	private FST.Arc<Long> matchToken(FST.Arc<Long> asc, CharsRef text) throws IOException {
		IntsRef intsRef = Util.toUTF32(text, scratchInfs);
		Long pendingOutput = asc.output;
		for (int intsRefIndex = 0; intsRefIndex < intsRef.length; ++intsRefIndex) {
			if (fst.findTargetArc(intsRef.ints[intsRef.offset + intsRefIndex], asc, asc, fstReader) == null) {
				return null;
			}
			pendingOutput = fst.outputs.add(pendingOutput, asc.output);
		}
		asc.output = pendingOutput;
		return asc;
	}

	private FST.Arc<Long> matchSeparator(FST.Arc<Long> asc) throws IOException {
		Long pendingOutput = asc.output;
		if (fst.findTargetArc(CcWordSet.WORD_SEPARATOR, asc, asc, fstReader) == null) {
			return null;
		}
		asc.output = fst.outputs.add(pendingOutput, asc.output);
		return asc;
	}

	private FST.Arc<Long> matchEnd(FST.Arc<Long> asc) throws IOException {
		Long pendingOutput = asc.output;
		if (fst.findTargetArc(CcWordSet.WORD_END, asc, asc, fstReader) == null) {
			return null;
		}
		pendingOutput = fst.outputs.add(pendingOutput, asc.output);
		asc.output = fst.outputs.add(pendingOutput, asc.nextFinalOutput);
		return asc;
	}

	private PendingInputToken createPendingToken() {
		CharsRef text = new CharsRefBuilder().append(termAtt).get();
		String type = typeAtt.type();
		int startOffset = offsetAtt.startOffset();
		int endOffset = offsetAtt.endOffset();
		int positionIncrement = posIncrAtt.getPositionIncrement();
		int positionLength = posLenAtt.getPositionLength();
		return new PendingInputToken(text, type, startOffset, endOffset, positionIncrement, positionLength);
	}

	private boolean hasPendingTokens() {
		if (this.pendingOutputs.isEmpty()) {
			return false;
		}
		while (true) {
			PendingOutput firstPendingOutput = pendingOutputs.peek();
			if (firstPendingOutput == null) {
				return false;
			}
			if (firstPendingOutput.tokens.isEmpty()) {
				if (firstPendingOutput.arc == null) {
					pendingOutputs.poll();
				} else {
					return false;
				}
			} else {
				if (args.mostTokens) {
					return true;
				} else {
					return firstPendingOutput.arc == null;
				}
			}
		}
	}

	private void flushPendingToken() {
		clearAttributes();
		PendingOutput firstPendingOutput = pendingOutputs.peek();
		PendingOutputToken token;
		if (args.mostTokens) {
			token = firstPendingOutput.tokens.poll();
		} else {
			token = firstPendingOutput.tokens.pollLast();
			firstPendingOutput.tokens.clear();
		}
		termAtt.append(token.text);
		typeAtt.setType(token.type);
		posIncrAtt.setPositionIncrement(token.positionIncrement);
		posLenAtt.setPositionLength(token.positionLength);
		offsetAtt.setOffset(firstPendingOutput.startOffset, token.endOffset);
	}

	private static class PendingOutput {
		public final int startOffset;
		public final int positionIncrement;
		public int positionLength;
		public FST.Arc<Long> arc;
		public final Deque<PendingOutputToken> tokens;

		public PendingOutput(int startOffset, int positionIncrement, int positionLength) {
			this.startOffset = startOffset;
			this.positionIncrement = positionIncrement;
			this.positionLength = positionLength;
			this.arc = new FST.Arc<>();
			this.tokens = new LinkedList<>();
		}
	}

	private static class PendingOutputToken {
		public final CharsRef text;
		public final String type;
		public final int endOffset;
		public final int positionIncrement;
		public final int positionLength;

		private PendingOutputToken(CharsRef text, String type, int endOffset, int positionIncrement, int positionLength) {
			super();
			this.text = text;
			this.type = type;
			this.endOffset = endOffset;
			this.positionIncrement = positionIncrement;
			this.positionLength = positionLength;
		}

		private PendingOutputToken(PendingInputToken inputToken) {
			this(inputToken.text, inputToken.type, inputToken.endOffset, inputToken.positionIncrement, inputToken.positionLength);
		}

	}

	private static class PendingInputToken {
		public final CharsRef text;
		public final String type;
		public final int startOffset;
		public final int endOffset;
		public final int positionIncrement;
		public final int positionLength;

		private PendingInputToken(CharsRef text, String type, int startOffset, int endOffset, int positionIncrement, int positionLength) {
			super();
			this.text = text;
			this.type = type;
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.positionIncrement = positionIncrement;
			this.positionLength = positionLength;
		}

	}
}