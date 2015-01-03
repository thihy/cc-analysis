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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.BytesRefHash;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

public class CcWordSet {

	/** 当有多个Token时，使用此分隔符。此分隔符应该时用户不会输入的。 */
	public static final char WORD_SEPARATOR = 0;
	public static final char WORD_END = 1;
	public final FST<Long> fst;
	public final BytesRefHash words;

	public CcWordSet(FST<Long> fst, BytesRefHash words) {
		this.fst = fst;
		this.words = words;
	}

	@Override
	public String toString() {
		StringBuilder builder2 = new StringBuilder();
		builder2.append("CcWordSet [fst=").append(fst.toString()).append(", words=").append(words).append("]");
		return builder2.toString();
	}

	/**
	 * Builds an FSTSynonymMap.
	 * <p>
	 * Call add() until you have added all the mappings, then call build() to get an FSTSynonymMap
	 * @lucene.experimental
	 */
	public static class Builder {
		/** map&lt;input,output_ord&gt; */
		private final HashMap<IntsRef, Integer> workingSet = new HashMap<>();
		private final BytesRefHash words = new BytesRefHash();
		private final BytesRefBuilder utf8Scratch = new BytesRefBuilder();

		public Builder() {
		}

		/** only used for asserting! */
		private boolean hasHoles(CharsRef chars) {
			final int end = chars.offset + chars.length;
			for (int idx = chars.offset + 1; idx < end; idx++) {
				if (chars.chars[idx] == WORD_SEPARATOR && chars.chars[idx - 1] == WORD_SEPARATOR) {
					return true;
				}
			}
			if (chars.chars[chars.offset] == '\u0000') {
				return true;
			}
			if (chars.chars[chars.offset + chars.length - 1] == '\u0000') {
				return true;
			}

			return false;
		}

		public void add(CharsRef analyzedText, CharsRef origText) {
			if (analyzedText.length <= 0) {
				throw new IllegalArgumentException("input.length must be > 0 (got " + analyzedText.length + ")");
			}
			if (origText.length <= 0) {
				throw new IllegalArgumentException("output.length must be > 0 (got " + origText.length + ")");
			}

			assert !hasHoles(analyzedText) : "input has holes: " + analyzedText;
			assert !hasHoles(origText) : "output has holes: " + origText;

			utf8Scratch.copyChars(origText);
			// lookup in hash
			int ord = words.add(utf8Scratch.get());
			if (ord < 0) {
				// already exists in our hash
				ord = (-ord) - 1;
				//System.out.println("  output=" + output + " old ord=" + ord);
			} else {
				//System.out.println("  output=" + output + " new ord=" + ord);
			}

			IntsRefBuilder analyzedIntsRefBuilder = new IntsRefBuilder();
			Util.toUTF32(analyzedText, analyzedIntsRefBuilder);
			IntsRef analyzedIntsRef = analyzedIntsRefBuilder.toIntsRef();
			Integer oldOrd = workingSet.put(analyzedIntsRef, ord);
			assert oldOrd == null;
		}

		/**
		 * Builds an {@link SynonymMap} and returns it.
		 */
		public CcWordSet build() throws IOException {
			PositiveIntOutputs outputs = PositiveIntOutputs.getSingleton();
			// TODO: are we using the best sharing options?
			org.apache.lucene.util.fst.Builder<Long> builder = new org.apache.lucene.util.fst.Builder<>(FST.INPUT_TYPE.BYTE4, outputs);

			Set<IntsRef> keys = workingSet.keySet();

			IntsRef sortedKeys[] = keys.toArray(new IntsRef[keys.size()]);
			Arrays.sort(sortedKeys);

			//System.out.println("fmap.build");
			for (int keyIdx = 0; keyIdx < sortedKeys.length; keyIdx++) {
				IntsRef analyzedText = sortedKeys[keyIdx];
				Integer origTextOrd = workingSet.get(analyzedText);

				builder.add(analyzedText, origTextOrd.longValue());
			}

			FST<Long> fst = builder.finish();
			return new CcWordSet(fst, words);
		}
	}
}
