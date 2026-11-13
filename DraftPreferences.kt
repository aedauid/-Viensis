package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.MarchingFigureStyle
import com.example.data.model.NotepadBackgroundStyle
import com.example.ui.theme.BlueprintThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "ensis_vi_drafts")

class DraftPreferences(private val context: Context) {

    private val noteDraftKey = stringPreferencesKey("autosave_note_draft")
    private val themeStyleKey = stringPreferencesKey("blueprint_theme_style")
    private val marchingFigureKey = stringPreferencesKey("marching_figure_style")
    private val notepadBackgroundKey = stringPreferencesKey("notepad_background_style")

    val noteDraft: Flow<String> = context.studioDataStore.data.map { prefs ->
        prefs[noteDraftKey] ?: ""
    }

    val themeStyle: Flow<BlueprintThemeStyle> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[themeStyleKey]
        runCatching { BlueprintThemeStyle.valueOf(raw ?: "") }
            .getOrDefault(BlueprintThemeStyle.CREAM_DRAFT)
    }

    val marchingFigure: Flow<MarchingFigureStyle> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[marchingFigureKey]
        runCatching { MarchingFigureStyle.valueOf(raw ?: "") }
            .getOrDefault(MarchingFigureStyle.FLAG_BEARER)
    }

    val notepadBackground: Flow<NotepadBackgroundStyle> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[notepadBackgroundKey]
        runCatching { NotepadBackgroundStyle.valueOf(raw ?: "") }
            .getOrDefault(NotepadBackgroundStyle.SQUARES)
    }

    suspend fun saveNoteDraft(text: String) {
        context.studioDataStore.edit { prefs ->
            prefs[noteDraftKey] = text
        }
    }

    suspend fun saveThemeStyle(style: BlueprintThemeStyle) {
        context.studioDataStore.edit { prefs ->
            prefs[themeStyleKey] = style.name
        }
    }

    suspend fun saveMarchingFigure(figure: MarchingFigureStyle) {
        context.studioDataStore.edit { prefs ->
            prefs[marchingFigureKey] = figure.name
        }
    }

    suspend fun saveNotepadBackground(style: NotepadBackgroundStyle) {
        context.studioDataStore.edit { prefs ->
            prefs[notepadBackgroundKey] = style.name
        }
    }

    suspend fun clearDrafts() {
        context.studioDataStore.edit { prefs ->
            prefs.remove(noteDraftKey)
        }
    }
}
