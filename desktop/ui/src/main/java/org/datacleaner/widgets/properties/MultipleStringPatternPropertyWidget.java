/**
 * DataCleaner (community edition)
 * Copyright (C) 2014 Neopost - Customer Information Management
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.datacleaner.widgets.properties;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JButton;

import org.datacleaner.descriptors.ConfiguredPropertyDescriptor;
import org.datacleaner.job.builder.ComponentBuilder;
import org.datacleaner.reference.StringPattern;
import org.datacleaner.panels.DCPanel;
import org.datacleaner.user.MutableReferenceDataCatalog;
import org.datacleaner.user.StringPatternChangeListener;
import org.datacleaner.util.IconUtils;
import org.datacleaner.util.WidgetFactory;
import org.datacleaner.windows.ReferenceDataDialog;

public class MultipleStringPatternPropertyWidget extends AbstractMultipleCheckboxesPropertyWidget<StringPattern> implements
		StringPatternChangeListener {

	private final MutableReferenceDataCatalog _referenceDataCatalog;
	private final Provider<ReferenceDataDialog> _referenceDataDialogProvider;

	@Inject
	public MultipleStringPatternPropertyWidget(ComponentBuilder componentBuilder,
			ConfiguredPropertyDescriptor propertyDescriptor, MutableReferenceDataCatalog referenceDataCatalog,
			Provider<ReferenceDataDialog> referenceDataDialogProvider) {
		super(componentBuilder, propertyDescriptor, StringPattern.class);
		_referenceDataCatalog = referenceDataCatalog;
		_referenceDataDialogProvider = referenceDataDialogProvider;
	}

	@Override
	public void onPanelAdd() {
		super.onPanelAdd();
		_referenceDataCatalog.addStringPatternListener(this);
	}

	@Override
	public void onPanelRemove() {
		super.onPanelRemove();
		_referenceDataCatalog.removeStringPatternListener(this);
	}

	@Override
	protected DCPanel createButtonPanel() {
		DCPanel buttonPanel = super.createButtonPanel();

		final JButton dialogButton = WidgetFactory.createSmallButton(IconUtils.MENU_OPTIONS);
		dialogButton.setToolTipText("Configure string patterns");
		dialogButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ReferenceDataDialog dialog = _referenceDataDialogProvider.get();
				dialog.selectStringPatternsTab();
				dialog.setVisible(true);
			}
		});

		buttonPanel.add(dialogButton);
		return buttonPanel;
	}

	@Override
	protected StringPattern[] getAvailableValues() {
		String[] names = _referenceDataCatalog.getStringPatternNames();
		StringPattern[] result = new StringPattern[names.length];
		for (int i = 0; i < names.length; i++) {
			result[i] = _referenceDataCatalog.getStringPattern(names[i]);
		}
		return result;
	}

	@Override
	protected String getName(StringPattern item) {
		return item.getName();
	}

	@Override
	public void onAdd(StringPattern stringPattern) {
		addCheckBox(stringPattern, false);
	}

	@Override
	public void onRemove(StringPattern stringPattern) {
		removeCheckBox(stringPattern);
	}

	@Override
	protected String getNotAvailableText() {
		return "- no string patterns available - ";
	}
}
