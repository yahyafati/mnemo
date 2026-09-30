package com.yahyafati.mnemo.core.ui.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Each launcher opens the right system dialog, and reports the location the user chose as a string. */
@RunWith(RobolectricTestRunner::class)
class FilePickersTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val picked = mutableListOf<String>()

    private fun started() = assertNotNull(shadowOf(composeRule.activity).nextStartedActivityForResult).intent

    private fun answer(uri: String) {
        val shadow = shadowOf(composeRule.activity)
        // The request was just consumed by `started()`; answer the same one.
        shadow.receiveResult(lastRequest, Activity.RESULT_OK, Intent().setData(Uri.parse(uri)))
    }

    private lateinit var lastRequest: Intent

    @Test
    fun aFilePickerOpensTheDocumentPickerForTheGivenTypes() {
        lateinit var picker: FilePicker
        composeRule.setContent { picker = rememberFilePicker(listOf("application/pdf"), picked::add) }
        composeRule.waitForIdle()

        picker.launch()
        composeRule.waitForIdle()
        lastRequest = started()

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, lastRequest.action)
        assertEquals(listOf("application/pdf"), lastRequest.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
        answer("content://docs/pdf/1")
        composeRule.waitForIdle()
        assertEquals(listOf("content://docs/pdf/1"), picked)
    }

    @Test
    fun aCancelledDialogReportsNothing() {
        lateinit var picker: FilePicker
        composeRule.setContent { picker = rememberFilePicker(onPicked = picked::add) }
        composeRule.waitForIdle()

        picker.launch()
        composeRule.waitForIdle()
        shadowOf(composeRule.activity).receiveResult(started(), Activity.RESULT_CANCELED, null)
        composeRule.waitForIdle()

        assertEquals(emptyList(), picked)
    }

    @Test
    fun aMediaPickerOpensTheContentChooser() {
        lateinit var picker: FilePicker
        composeRule.setContent { picker = rememberMediaPicker("image/*", picked::add) }
        composeRule.waitForIdle()

        picker.launch()
        composeRule.waitForIdle()
        lastRequest = started()

        assertEquals(Intent.ACTION_GET_CONTENT, lastRequest.action)
        assertEquals("image/*", lastRequest.type)
        answer("content://media/external/images/7")
        composeRule.waitForIdle()
        assertEquals(listOf("content://media/external/images/7"), picked)
    }

    @Test
    fun aFileSaverSuggestsAName() {
        lateinit var saver: FileSaver
        composeRule.setContent { saver = rememberFileSaver("application/zip", picked::add) }
        composeRule.waitForIdle()

        saver.launch("mnemo-backup.zip")
        composeRule.waitForIdle()
        lastRequest = started()

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, lastRequest.action)
        assertEquals("application/zip", lastRequest.type)
        assertEquals("mnemo-backup.zip", lastRequest.getStringExtra(Intent.EXTRA_TITLE))
        answer("content://docs/new/1")
        composeRule.waitForIdle()
        assertEquals(listOf("content://docs/new/1"), picked)
    }

    @Test
    fun aFolderPickerOpensTheTreePicker() {
        lateinit var picker: FolderPicker
        composeRule.setContent { picker = rememberFolderPicker(picked::add) }
        composeRule.waitForIdle()

        picker.launch()
        composeRule.waitForIdle()
        lastRequest = started()

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, lastRequest.action)
        answer("content://tree/backups")
        composeRule.waitForIdle()
        assertEquals(listOf("content://tree/backups"), picked)
        assertNull(shadowOf(composeRule.activity).nextStartedActivityForResult)
    }
}
