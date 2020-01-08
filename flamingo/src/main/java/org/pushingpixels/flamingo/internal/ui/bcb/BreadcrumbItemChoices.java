/*
 * Copyright (c) 2003-2020 Flamingo Kirill Grouchnikov
 * and <a href="http://www.topologi.com">Topologi</a>. 
 * Contributed by <b>Rick Jelliffe</b> of <b>Topologi</b> 
 * in January 2006. 
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  o Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer. 
 *     
 *  o Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution. 
 *     
 *  o Neither the name of the copyright holder nor the names of
 *    its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *     
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.pushingpixels.flamingo.internal.ui.bcb;

import org.pushingpixels.flamingo.api.bcb.BreadcrumbItem;
import org.pushingpixels.flamingo.api.common.StringValuePair;

import javax.swing.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the model for the popup that is shown by clicking on the path
 * selector.
 */
final class BreadcrumbItemChoices<T> {
	/**
	 * Contains all possible choices.
	 */
	private List<BreadcrumbItem<T>> choices;

	/**
	 * The ancestor item. This can be <code>null</code> only for the root
	 * choices element.
	 */
	private BreadcrumbItem<T> ancestor;

	/**
	 * The index of <code>this</code> element.
	 */
	private int selectedIndex = 0;

	public BreadcrumbItemChoices(BreadcrumbItem<T> ancestor,
			List<StringValuePair<T>> entries) {
		this.ancestor = ancestor;
		this.choices = new ArrayList<>(entries.size());
		int index = 0;
		for (StringValuePair<T> pair : entries) {
			BreadcrumbItem<T> li = new BreadcrumbItem<>(pair.getKey(), pair.getValue());
			li.setIcon((Icon) pair.get("icon"));
			this.choices.add(index, li);
			index++;
		}
		this.selectedIndex = -1;
	}

	/**
	 * Returns the 0-based index of the first {@link BreadcrumbItem} whose
	 * display name matches the specified string.
	 * 
	 * @param s
	 *            String.
	 * @return The 0-based index of the first {@link BreadcrumbItem} whose
	 *         display name matches the specified string.
	 */
	public int getPosition(String s) {
		assert (s != null && s.length() > 0);
		for (int i = 0; i < choices.size(); i++) {
			BreadcrumbItem<T> it = choices.get(i);
			if (s.equals(it.getKey()))
				return i;
		}
		return -1;
	}

	public void setSelectedIndex(int index) {
		this.selectedIndex = index;
	}

	public int getSelectedIndex() {
		return this.selectedIndex;
	}

	/**
	 * Returns the item array of <code>true</code>his element.
	 * 
	 * @return The item array of <code>true</code>his element.
	 */
	public List<BreadcrumbItem<T>> getChoices() {
		return choices;
	}

	public BreadcrumbItem<T> getAncestor() {
		return ancestor;
	}
}
