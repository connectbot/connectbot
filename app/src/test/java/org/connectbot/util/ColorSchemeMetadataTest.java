/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.util;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for color scheme metadata operations in HostDatabase.
 */
@RunWith(AndroidJUnit4.class)
public class ColorSchemeMetadataTest {
	private Context context;
	private HostDatabase database;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		HostDatabase.resetInMemoryInstance(context);
		database = HostDatabase.get(context);
	}

	@After
	public void tearDown() {
		if (database != null) {
			database.resetDatabase();
		}
	}

	@Test
	public void createColorScheme_ValidData_Success() {
		long schemeId = database.createColorScheme(1, "Test Scheme", "A test color scheme", false);

		assertTrue("Scheme ID should be valid", schemeId > 0);

		Cursor cursor = database.getColorSchemeMetadata(1);
		assertNotNull("Cursor should not be null", cursor);
		assertTrue("Cursor should have data", cursor.moveToFirst());

		int nameIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_NAME);
		int descIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_DESCRIPTION);
		int builtInIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_IS_BUILTIN);

		assertEquals("Scheme name should match", "Test Scheme", cursor.getString(nameIndex));
		assertEquals("Scheme description should match", "A test color scheme", cursor.getString(descIndex));
		assertEquals("Scheme should not be built-in", 0, cursor.getInt(builtInIndex));

		cursor.close();
	}

	@Test
	public void createColorScheme_BuiltIn_Success() {
		long schemeId = database.createColorScheme(1, "Built-in Scheme", "Built-in", true);

		assertTrue("Scheme ID should be valid", schemeId > 0);

		Cursor cursor = database.getColorSchemeMetadata(1);
		assertTrue("Cursor should have data", cursor.moveToFirst());

		int builtInIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_IS_BUILTIN);
		assertEquals("Scheme should be built-in", 1, cursor.getInt(builtInIndex));

		cursor.close();
	}

	@Test
	public void getAllColorSchemeMetadata_MultipleSchemes_ReturnsAll() {
		database.createColorScheme(1, "Scheme 1", "First scheme", false);
		database.createColorScheme(2, "Scheme 2", "Second scheme", false);
		database.createColorScheme(3, "Scheme 3", "Third scheme", true);

		Cursor cursor = database.getAllColorSchemeMetadata();
		assertNotNull("Cursor should not be null", cursor);

		int count = 0;
		while (cursor.moveToNext()) {
			count++;
		}

		assertEquals("Should have 3 schemes", 3, count);
		cursor.close();
	}

	@Test
	public void updateColorSchemeMetadata_ValidData_Success() {
		database.createColorScheme(1, "Original Name", "Original description", false);

		int rowsAffected = database.updateColorSchemeMetadata(1, "Updated Name", "Updated description");

		assertEquals("Should update 1 row", 1, rowsAffected);

		Cursor cursor = database.getColorSchemeMetadata(1);
		assertTrue("Cursor should have data", cursor.moveToFirst());

		int nameIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_NAME);
		int descIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_DESCRIPTION);

		assertEquals("Name should be updated", "Updated Name", cursor.getString(nameIndex));
		assertEquals("Description should be updated", "Updated description", cursor.getString(descIndex));

		cursor.close();
	}

	@Test
	public void updateColorSchemeMetadata_NonExistent_NoChange() {
		int rowsAffected = database.updateColorSchemeMetadata(999, "Name", "Description");

		assertEquals("Should not update any rows", 0, rowsAffected);
	}

	@Test
	public void deleteColorSchemeMetadata_Exists_Success() {
		database.createColorScheme(1, "To Delete", "Will be deleted", false);

		int rowsAffected = database.deleteColorSchemeMetadata(1);

		assertEquals("Should delete 1 row", 1, rowsAffected);

		Cursor cursor = database.getColorSchemeMetadata(1);
		assertFalse("Cursor should be empty after deletion", cursor.moveToFirst());
		cursor.close();
	}

	@Test
	public void deleteColorSchemeMetadata_NonExistent_NoChange() {
		int rowsAffected = database.deleteColorSchemeMetadata(999);

		assertEquals("Should not delete any rows", 0, rowsAffected);
	}

	@Test
	public void colorSchemeNameExists_Exists_ReturnsTrue() {
		database.createColorScheme(1, "Existing Scheme", "Description", false);

		boolean exists = database.colorSchemeNameExists("Existing Scheme", null);

		assertTrue("Should find existing scheme", exists);
	}

	@Test
	public void colorSchemeNameExists_NotExists_ReturnsFalse() {
		boolean exists = database.colorSchemeNameExists("Non-existent Scheme", null);

		assertFalse("Should not find non-existent scheme", exists);
	}

	@Test
	public void colorSchemeNameExists_ExcludeScheme_IgnoresExcluded() {
		database.createColorScheme(1, "Test Scheme", "Description", false);

		boolean exists = database.colorSchemeNameExists("Test Scheme", 1);

		assertFalse("Should exclude the specified scheme", exists);
	}

	@Test
	public void colorSchemeNameExists_ExcludeScheme_FindsOthers() {
		database.createColorScheme(1, "Scheme 1", "Description", false);
		database.createColorScheme(2, "Scheme 1", "Duplicate name", false);

		boolean exists = database.colorSchemeNameExists("Scheme 1", 1);

		assertTrue("Should find other schemes with same name", exists);
	}

	@Test
	public void createColorScheme_NullDescription_UsesEmptyString() {
		database.createColorScheme(1, "Test", null, false);

		Cursor cursor = database.getColorSchemeMetadata(1);
		assertTrue("Cursor should have data", cursor.moveToFirst());

		int descIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_DESCRIPTION);
		String description = cursor.getString(descIndex);

		assertNotNull("Description should not be null", description);
		assertEquals("Description should be empty string", "", description);

		cursor.close();
	}

	@Test
	public void updateColorSchemeMetadata_NullDescription_UsesEmptyString() {
		database.createColorScheme(1, "Test", "Original", false);
		database.updateColorSchemeMetadata(1, "Test", null);

		Cursor cursor = database.getColorSchemeMetadata(1);
		assertTrue("Cursor should have data", cursor.moveToFirst());

		int descIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_DESCRIPTION);
		String description = cursor.getString(descIndex);

		assertNotNull("Description should not be null", description);
		assertEquals("Description should be empty string", "", description);

		cursor.close();
	}
}
