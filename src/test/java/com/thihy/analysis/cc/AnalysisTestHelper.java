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
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

public class AnalysisTestHelper {
	private static final int PADDING_WIDTH = 4;

	public static void printResultOfTokenStream(PrintStream out, TokenStream ts) throws IOException {
		CharTermAttribute termAttr = ts.getAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = ts.getAttribute(TypeAttribute.class);
		OffsetAttribute offAttr = ts.getAttribute(OffsetAttribute.class);
		PositionIncrementAttribute posIncAttr = ts.getAttribute(PositionIncrementAttribute.class);
		PositionLengthAttribute posLenAttr = ts.getAttribute(PositionLengthAttribute.class);
		ts.reset();
		Table<String, String, String> contentTable = Tables.newCustomTable(new LinkedHashMap<String, Map<String, String>>(),
				new Supplier<Map<String, String>>() {
					@Override
					public Map<String, String> get() {
						return Maps.newLinkedHashMap();
					}
				});
		int lineNo = 1;
		int pos = 0;
		while (ts.incrementToken()) {
			String lineId = lineNo + ".";
			contentTable.put(lineId, "term", termAttr.toString());
			contentTable.put(lineId, "type", typeAttr.type());
			contentTable.put(lineId, "startOffset", offAttr.startOffset() + "");
			contentTable.put(lineId, "endOffset", offAttr.endOffset() + "");
			contentTable.put(lineId, "posInc", posIncAttr.getPositionIncrement() + "");
			contentTable.put(lineId, "posLen", posLenAttr.getPositionLength() + "");
			pos += posIncAttr.getPositionIncrement();
			contentTable.put(lineId, "pos", pos + "");

			lineNo++;
		}
		printTable(out, contentTable);
	}

	private static void printTable(PrintStream out, Table<String, String, String> table) {
		//gen width
		int firstColumnWidth = maxLength(table.rowKeySet().iterator());
		Map<String, Integer> otherColumnWidths = maxLengthOfTableColumn(table);

		// print header
		printTableHeader(out, table, firstColumnWidth, otherColumnWidths);

		// print row
		for (String row : table.rowKeySet()) {
			out.print(row);
			printWhiteSpace(out, firstColumnWidth - row.length());

			for (String column : table.columnKeySet()) {
				printWhiteSpace(out, PADDING_WIDTH);

				String columnValue = table.get(row, column);
				if (columnValue == null) {
					columnValue = "-";
				}

				int maxLengthOfThisColumn = otherColumnWidths.get(column);
				out.print(columnValue);
				printWhiteSpace(out, maxLengthOfThisColumn - columnValue.length());
			}

			out.println();
		}
	}

	private static void printTableHeader(PrintStream out, Table<String, String, String> table, int firstColumnWidth,
			Map<String, Integer> otherColumnWidths) {
		// the column of row key
		printWhiteSpace(out, firstColumnWidth);

		//
		for (String column : table.columnKeySet()) {
			printWhiteSpace(out, PADDING_WIDTH);
			int maxLengthOfThisColumn = otherColumnWidths.get(column);
			out.print(column);
			printWhiteSpace(out, maxLengthOfThisColumn - column.length());
		}
		//
		out.println();
	}

	private static void printWhiteSpace(PrintStream out, int count) {
		for (int i = 0; i < count; ++i) {
			out.print(' ');
		}
	}

	private static Map<String, Integer> maxLengthOfTableColumn(Table<String, String, String> table) {
		Map<String, Integer> columnWidths = Maps.newHashMap();
		for (String column : table.columnKeySet()) {
			int columnHeaderLength = column.length();
			Iterator<String> columnContentIter = table.column(column).values().iterator();
			int maxColumnContentLength = maxLength(columnContentIter);
			int maxColumnWidth = Math.max(columnHeaderLength, maxColumnContentLength);
			columnWidths.put(column, maxColumnWidth);
		}
		return columnWidths;
	}

	private static int maxLength(Iterator<String> iter) {
		int maxLen = -1;
		while (iter.hasNext()) {
			int len = iter.next().length();
			if (maxLen < len) {
				maxLen = len;
			}
		}
		return maxLen;
	}
}
