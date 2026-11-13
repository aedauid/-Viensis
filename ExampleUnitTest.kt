package com.example

import com.example.data.model.MarchingFigureStyle
import com.example.data.model.NotepadBackgroundStyle
import com.example.news.StageScreenMode
import com.example.ui.games.ChessEngine
import com.example.ui.games.ChessMove
import com.example.ui.games.ChessState
import com.example.ui.games.ChessStatus
import com.example.ui.games.Connect3Engine
import com.example.ui.games.Connect3State
import com.example.ui.games.Connect5Engine
import com.example.ui.games.Connect5State
import com.example.ui.games.PieceColor
import com.example.ui.theme.pairedThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun marchingFiguresPairToDistinctThemesAndPaperStylesExist() {
        val paired = MarchingFigureStyle.entries.map { it.pairedThemeStyle() }.toSet()
        assertEquals(MarchingFigureStyle.entries.size, paired.size)
        assertTrue(NotepadBackgroundStyle.entries.contains(NotepadBackgroundStyle.GUTENBERG_PRESS))
        assertTrue(NotepadBackgroundStyle.entries.contains(NotepadBackgroundStyle.SQUARES))
        assertTrue(NotepadBackgroundStyle.entries.contains(NotepadBackgroundStyle.LINES))
        assertTrue(NotepadBackgroundStyle.entries.contains(NotepadBackgroundStyle.EMPTY))
        assertEquals(listOf(StageScreenMode.ANIM, StageScreenMode.VIDEO, StageScreenMode.NEWS), StageScreenMode.entries)
    }

    @Test
    fun connect3_detectsVerticalWinAndAiBlocks() {
        var s = Connect3State()
        s = Connect3Engine.dropDisc(s, 2) // P1 (row 3, col 2)
        s = Connect3Engine.dropDisc(s, 0) // P2
        s = Connect3Engine.dropDisc(s, 2) // P1 (row 2, col 2)
        // AI as P2 should block column 2
        val aiCol = Connect3Engine.pickAiColumn(s)
        assertEquals(2, aiCol)

        // Check P1 vertical win when unblocked
        s = Connect3Engine.dropDisc(s, 0) // P2
        s = Connect3Engine.dropDisc(s, 2) // P1 (row 1, col 2)
        assertEquals(1, s.winner)
        assertTrue(s.winningCells.contains(3 to 2))
    }

    @Test
    fun connect5_detectsHorizontalWinAndUndo() {
        var s = Connect5State()
        for (c in 0 until 5) {
            s = Connect5Engine.placeStone(s, 4, c) // P1
            if (c < 4) s = Connect5Engine.placeStone(s, 5, c) // P2
        }
        assertEquals(1, s.winner)
        assertEquals(5, s.winningCells.size)
        val undone = Connect5Engine.undo(s, steps = 1)
        assertEquals(0, undone.winner)
        assertEquals(0, undone.cell(4, 4))
    }

    @Test
    fun chess_initialLegalMovesAndFoolsMateCheckmate() {
        var state = ChessState()
        assertEquals(20, ChessEngine.legalMoves(state).size)

        // Fool's mate: 1. f3 e5 2. g4 Qh4#
        state = ChessEngine.applyMove(state, ChessMove(from = 53, to = 45)) // f2-f3
        state = ChessEngine.applyMove(state, ChessMove(from = 12, to = 28)) // e7-e5
        state = ChessEngine.applyMove(state, ChessMove(from = 54, to = 38)) // g2-g4
        state = ChessEngine.applyMove(state, ChessMove(from = 3, to = 39))  // Qd8-h4#

        assertEquals(ChessStatus.CHECKMATE, state.status)
        assertEquals(PieceColor.BLACK, state.winner)

        val afterUndo = ChessEngine.undo(state, steps = 1)
        assertEquals(ChessStatus.PLAYING, afterUndo.status)
        assertNotNull(ChessEngine.pickAiMove(afterUndo))
    }
}
