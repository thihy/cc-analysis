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

import java.util.Arrays;

public final class CircularArrayList<E> {
	private static final int DEFAULT_CAPACITY = 10;

	private static final Object[] EMPTY_ELEMENTDATA = {};

	/*
	 * 实际存储的位置为elementData[first,last) 
	 */
	private transient Object[] elementData;

	private int first;

	private int last;

	private int modCount;

	/**
	* Constructs an empty list with the specified initial capacity.
	*
	* @param  initialCapacity  the initial capacity of the list
	* @throws IllegalArgumentException if the specified initial capacity
	*         is negative
	*/
	public CircularArrayList(int initialCapacity) {
		super();
		if (initialCapacity < 0)
			throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
		this.elementData = new Object[initialCapacity];
	}

	/**
	 * Constructs an empty list with an initial capacity of ten.
	 */
	public CircularArrayList() {
		super();
		this.elementData = EMPTY_ELEMENTDATA;
	}

	public void ensureCapacity(int minCapacity) {
		int minExpand = (elementData != EMPTY_ELEMENTDATA)
		// any size if real element table
		? 0
				// larger than default for empty table. It's already supposed to be
				// at default size.
				: DEFAULT_CAPACITY;

		if (minCapacity > minExpand) {
			ensureExplicitCapacity(minCapacity);
		}
	}

	private void ensureCapacityInternal(int minCapacity) {
		if (elementData == EMPTY_ELEMENTDATA) {
			minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
		}

		ensureExplicitCapacity(minCapacity);
	}

	private void ensureExplicitCapacity(int minCapacity) {
		modCount++;

		// overflow-conscious code
		if (minCapacity - currentCapacity() > 0)
			grow(minCapacity);
	}

	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private void grow(int minCapacity) {
		// overflow-conscious code
		int oldCapacity = currentCapacity();
		int newCapacity = oldCapacity + (oldCapacity >> 1);
		if (newCapacity - minCapacity < 0)
			newCapacity = minCapacity;
		if (newCapacity - MAX_ARRAY_SIZE > 0)
			newCapacity = hugeCapacity(minCapacity);
		// minCapacity is usually close to size, so this is a win:
		elementData = copyOfElementData(newCapacity);
	}

	private static int hugeCapacity(int minCapacity) {
		if (minCapacity < 0) // overflow
			throw new OutOfMemoryError();
		return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	// ---
	/*
	 * 由于循环数组需要额外一个空位，用于方便first和last计算。故而在计算容量和创建数组时，需要考虑此空位。
	 */
	private int currentCapacity() {
		return elementData.length == 0 ? 0 : elementData.length - 1;
	}

	private Object[] copyOfElementData(int newCapacity) {
		int newSize = newCapacity + 1;
		return Arrays.copyOf(elementData, newSize);
	}

	// ---

	public int size() {
		return first < last ? last - first : last + elementData.length - first;
	}

	public boolean isEmpty() {
		return first == last;
	}

	public boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	public int indexOf(Object o) {
		int size = size();
		if (o == null) {
			for (int i = 0; i < size; i++)
				if (elementData[i] == null)
					return i;
		} else {
			for (int i = 0; i < size; i++)
				if (o.equals(elementData[i]))
					return i;
		}
		return -1;
	}

	public int lastIndexOf(Object o) {
		int size = size();
		if (o == null) {
			for (int i = size - 1; i >= 0; i--)
				if (elementData[i] == null)
					return i;
		} else {
			for (int i = size - 1; i >= 0; i--)
				if (o.equals(elementData[i]))
					return i;
		}
		return -1;
	}

	// Positional Access Operations
	private int realIndex(int index) {
		if (first < last || last == 0) {
			return first + index;
		} else {
			return (first + index) % elementData.length;
		}
	}

	private E elementData(int index) {
		return (E) elementData[realIndex(index)];
	}

	public E get(int index) {
		rangeCheck(index);

		return elementData(index);
	}

	public E set(int index, E element) {
		rangeCheck(index);

		int realIndex = realIndex(index);
		E oldValue = elementData(realIndex);
		elementData[realIndex] = element;
		return oldValue;
	}

	public boolean add(E e) {
		ensureCapacityInternal(size() + 1); // Increments modCount!!
		elementData[last++] = e;
		if (last == elementData.length) {
			last = 0;
		}
		return true;
	}

	private void rangeCheck(int index) {
		if (index >= size() || index < 0)
			throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	private String outOfBoundsMsg(int index) {
		return "Index: " + index + ", Size: " + size();
	}

	public void clear() {
		if (first == last) {
			return;
		}
		modCount++;

		// clear to let GC do its work
		if (first < last) {
			Arrays.fill(elementData, first, last, null);
		} else if (last == 0) {
			Arrays.fill(elementData, first, elementData.length, null);
		} else {
			Arrays.fill(elementData, first, elementData.length, null);
			Arrays.fill(elementData, 0, last, null);
		}
		first = last = 0;
	}
}
