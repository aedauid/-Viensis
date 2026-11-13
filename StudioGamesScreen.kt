package com.example.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BlueprintCard
import com.example.ui.components.BlueprintGridBackground
import com.example.ui.theme.GaeguFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun StudioGamesScreen(
    onBack: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    var selectedGame by remember { mutableStateOf(StudioGameType.CONNECT_3) }
    var vsAi by remember { mutableStateOf(true) }

    var connect3State by remember { mutableStateOf(Connect3State()) }
    var connect5State by remember { mutableStateOf(Connect5State()) }
    var chessState by remember { mutableStateOf(ChessState()) }
    var selectedChessSquare by remember { mutableStateOf<Int?>(null) }
    var pendingPromotions by remember { mutableStateOf<List<ChessMove>?>(null) }

    BackHandler(onBack = onBack)

    // AI Turn Hooks
    LaunchedEffect(selectedGame, vsAi, connect3State.currentPlayer, connect3State.isGameOver) {
        if (selectedGame == StudioGameType.CONNECT_3 && vsAi && connect3State.currentPlayer == 2 && !connect3State.isGameOver) {
            delay(300)
            val col = withContext(Dispatchers.Default) { Connect3Engine.pickAiColumn(connect3State) }
            connect3State = Connect3Engine.dropDisc(connect3State, col)
        }
    }

    LaunchedEffect(selectedGame, vsAi, connect5State.currentPlayer, connect5State.isGameOver) {
        if (selectedGame == StudioGameType.CONNECT_5 && vsAi && connect5State.currentPlayer == 2 && !connect5State.isGameOver) {
            delay(280)
            val (r, c) = withContext(Dispatchers.Default) { Connect5Engine.pickAiCell(connect5State) }
            connect5State = Connect5Engine.placeStone(connect5State, r, c)
        }
    }

    LaunchedEffect(selectedGame, vsAi, chessState.turn, chessState.isGameOver) {
        if (selectedGame == StudioGameType.CHESS && vsAi && chessState.turn == PieceColor.BLACK && !chessState.isGameOver) {
            delay(260)
            val aiMove = withContext(Dispatchers.Default) { ChessEngine.pickAiMove(chessState) }
            if (aiMove != null) {
                chessState = ChessEngine.applyMove(chessState, aiMove)
                selectedChessSquare = null
            }
        }
    }

    BlueprintGridBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .background(tokens.cardBg)
                            .border(1.5.dp, tokens.border)
                            .clickable(onClick = onBack)
                            .padding(horizontal = 10.dp)
                            .testTag("games_back_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Studio",
                            tint = tokens.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "STUDIO",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = tokens.inkPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            tint = tokens.plumAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "DAUDIO ARCADE",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp,
                            color = tokens.inkPrimary,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(if (vsAi) tokens.tealAccent else tokens.plumAccent)
                            .border(1.5.dp, tokens.border)
                            .clickable { vsAi = !vsAi }
                            .padding(horizontal = 10.dp)
                            .testTag("games_mode_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (vsAi) "VS AI" else "2P PASS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }

                // Game Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioGameType.entries.forEach { game ->
                        val active = game == selectedGame
                        val accent = when (game) {
                            StudioGameType.CONNECT_3 -> tokens.gold
                            StudioGameType.CONNECT_5 -> tokens.tealAccent
                            StudioGameType.CHESS -> tokens.plumAccent
                        }
                        BlueprintCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedGame = game }
                                .testTag("game_tab_${game.name}"),
                            containerColor = if (active) accent.copy(alpha = 0.24f) else tokens.cardBg,
                            shadowOffset = if (active) 3.dp else 1.5.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = game.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = game.subtitle,
                                    fontFamily = GaeguFontFamily,
                                    fontSize = 13.sp,
                                    color = tokens.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Controls + Status Bar
                BlueprintCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = tokens.transportBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusText = when (selectedGame) {
                            StudioGameType.CONNECT_3 -> when {
                                connect3State.winner == 1 -> "GOLD WINS!"
                                connect3State.winner == 2 -> if (vsAi) "STUDIO AI WINS!" else "TEAL WINS!"
                                connect3State.isDraw -> "DRAW BOARD"
                                connect3State.currentPlayer == 1 -> "GOLD'S TURN"
                                else -> if (vsAi) "AI THINKING..." else "TEAL'S TURN"
                            }
                            StudioGameType.CONNECT_5 -> when {
                                connect5State.winner == 1 -> "GOLD WINS 5!"
                                connect5State.winner == 2 -> if (vsAi) "STUDIO AI WINS!" else "TEAL WINS 5!"
                                connect5State.isDraw -> "DRAW BOARD"
                                connect5State.currentPlayer == 1 -> "GOLD'S TURN"
                                else -> if (vsAi) "AI THINKING..." else "TEAL'S TURN"
                            }
                            StudioGameType.CHESS -> when (chessState.status) {
                                ChessStatus.CHECKMATE ->
                                    if (chessState.winner == PieceColor.WHITE) "CHECKMATE · WHITE WINS!" else "CHECKMATE · BLACK WINS!"
                                ChessStatus.STALEMATE -> "STALEMATE · DRAW"
                                ChessStatus.DRAW -> "DRAW"
                                ChessStatus.CHECK ->
                                    if (chessState.turn == PieceColor.WHITE) "WHITE IN CHECK" else "BLACK IN CHECK"
                                ChessStatus.PLAYING ->
                                    if (chessState.turn == PieceColor.WHITE) "WHITE TO MOVE"
                                    else if (vsAi) "BLACK AI THINKING..." else "BLACK TO MOVE"
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val badgeColor = when (selectedGame) {
                                StudioGameType.CONNECT_3 -> if (connect3State.currentPlayer == 1) tokens.gold else tokens.tealAccent
                                StudioGameType.CONNECT_5 -> if (connect5State.currentPlayer == 1) tokens.gold else tokens.tealAccent
                                StudioGameType.CHESS -> if (chessState.turn == PieceColor.WHITE) tokens.gold else tokens.plumAccent
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(badgeColor, CircleShape)
                                    .border(1.dp, tokens.border, CircleShape)
                            )
                            Text(
                                text = statusText,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = tokens.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val steps = if (vsAi) 2 else 1
                                    when (selectedGame) {
                                        StudioGameType.CONNECT_3 -> connect3State = Connect3Engine.undo(connect3State, steps)
                                        StudioGameType.CONNECT_5 -> connect5State = Connect5Engine.undo(connect5State, steps)
                                        StudioGameType.CHESS -> {
                                            chessState = ChessEngine.undo(chessState, steps)
                                            selectedChessSquare = null
                                        }
                                    }
                                },
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, tokens.border),
                                modifier = Modifier.height(34.dp).testTag("games_undo_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo move", tint = tokens.inkPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("UNDO", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = tokens.inkPrimary)
                            }
                            Button(
                                onClick = {
                                    when (selectedGame) {
                                        StudioGameType.CONNECT_3 -> connect3State = Connect3State()
                                        StudioGameType.CONNECT_5 -> connect5State = Connect5State()
                                        StudioGameType.CHESS -> {
                                            chessState = ChessState()
                                            selectedChessSquare = null
                                        }
                                    }
                                },
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, tokens.border),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                                modifier = Modifier.height(34.dp).testTag("games_reset_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "New game", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("NEW", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                }

                // Active Game Board
                when (selectedGame) {
                    StudioGameType.CONNECT_3 -> Connect3BoardCard(
                        state = connect3State,
                        canInteract = !connect3State.isGameOver && (!vsAi || connect3State.currentPlayer == 1),
                        onColumnTap = { col ->
                            connect3State = Connect3Engine.dropDisc(connect3State, col)
                        }
                    )
                    StudioGameType.CONNECT_5 -> Connect5BoardCard(
                        state = connect5State,
                        canInteract = !connect5State.isGameOver && (!vsAi || connect5State.currentPlayer == 1),
                        onCellTap = { r, c ->
                            connect5State = Connect5Engine.placeStone(connect5State, r, c)
                        }
                    )
                    StudioGameType.CHESS -> ChessBoardCard(
                        state = chessState,
                        selectedSquare = selectedChessSquare,
                        canInteract = !chessState.isGameOver && (!vsAi || chessState.turn == PieceColor.WHITE),
                        onSquareTap = { sq ->
                            val legalFromSelected = selectedChessSquare?.let { sel ->
                                ChessEngine.legalMovesForSquare(chessState, sel).filter { it.to == sq }
                            }.orEmpty()

                            if (legalFromSelected.isNotEmpty()) {
                                if (legalFromSelected.size > 1) {
                                    pendingPromotions = legalFromSelected
                                } else {
                                    chessState = ChessEngine.applyMove(chessState, legalFromSelected.first())
                                    selectedChessSquare = null
                                }
                            } else {
                                val piece = chessState.board[sq]
                                selectedChessSquare = if (piece != null && piece.color == chessState.turn) {
                                    if (selectedChessSquare == sq) null else sq
                                } else {
                                    null
                                }
                            }
                        }
                    )
                }

                // Studio Footnote
                Text(
                    text = "DRAFTING BREAK · PICK UP YOUR SONG ANY TIME VIA STUDIO",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = tokens.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }

            // Pawn Promotion Chooser
            pendingPromotions?.let { promos ->
                AlertDialog(
                    onDismissRequest = { pendingPromotions = null },
                    containerColor = tokens.paperBg,
                    title = {
                        Text("PROMOTE PAWN", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.inkPrimary)
                    },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            promos.forEach { move ->
                                val pt = move.promotion ?: PieceType.QUEEN
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(tokens.cardBg)
                                        .border(1.5.dp, tokens.border)
                                        .clickable {
                                            chessState = ChessEngine.applyMove(chessState, move)
                                            selectedChessSquare = null
                                            pendingPromotions = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = pt.glyph(chessState.turn), fontSize = 28.sp, color = tokens.inkPrimary)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { pendingPromotions = null }) {
                            Text("CANCEL", fontFamily = JetBrainsMonoFontFamily, color = tokens.inkMuted)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Connect3BoardCard(
    state: Connect3State,
    canInteract: Boolean,
    onColumnTap: (Int) -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintCard(
        modifier = Modifier.fillMaxWidth(),
        showCornerBracket = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DROP 3 IN A ROW · TAP ANY COLUMN",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = tokens.inkMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(tokens.gold, CircleShape).border(1.dp, tokens.border, CircleShape))
                        Text("P1", fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, color = tokens.inkPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(tokens.tealAccent, CircleShape).border(1.dp, tokens.border, CircleShape))
                        Text("P2/AI", fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, color = tokens.inkPrimary)
                    }
                }
            }

            // Blueprint frame grid (4 rows x 5 cols)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.libraryBg.copy(alpha = 0.92f))
                    .border(2.dp, tokens.border)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until Connect3State.ROWS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until Connect3State.COLS) {
                            val cellVal = state.cell(r, c)
                            val isWinning = (r to c) in state.winningCells
                            val isLast = state.lastMove == (r to c)
                            val discColor = when (cellVal) {
                                1 -> tokens.gold
                                2 -> tokens.tealAccent
                                else -> tokens.paperBg.copy(alpha = 0.88f)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(discColor)
                                    .border(
                                        width = if (isWinning) 3.dp else if (isLast) 2.5.dp else 1.5.dp,
                                        color = if (isWinning) tokens.emeraldAccent else if (isLast) tokens.coralAccent else tokens.border,
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = canInteract && state.cell(0, c) == 0) {
                                        onColumnTap(c)
                                    }
                                    .testTag("c3_cell_${r}_${c}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isWinning) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color.White.copy(alpha = 0.7f), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Connect5BoardCard(
    state: Connect5State,
    canInteract: Boolean,
    onCellTap: (Int, Int) -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintCard(
        modifier = Modifier.fillMaxWidth(),
        showCornerBracket = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GOMOKU · ALIGN 5 STONES",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = tokens.inkMuted
                )
                Text(
                    text = "MOVES: ${state.history.size}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = tokens.tealAccent
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(tokens.paperBg)
                    .border(2.dp, tokens.border)
                    .padding(4.dp)
            ) {
                // Draw subtle board grid lines behind cells
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepX = size.width / Connect5State.SIZE
                    val stepY = size.height / Connect5State.SIZE
                    for (i in 0 until Connect5State.SIZE) {
                        val cx = stepX * (i + 0.5f)
                        val cy = stepY * (i + 0.5f)
                        drawLine(tokens.gridMajor, Offset(cx, stepY * 0.5f), Offset(cx, size.height - stepY * 0.5f), 1.2f)
                        drawLine(tokens.gridMajor, Offset(stepX * 0.5f, cy), Offset(size.width - stepX * 0.5f, cy), 1.2f)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    for (r in 0 until Connect5State.SIZE) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0 until Connect5State.SIZE) {
                                val cellVal = state.cell(r, c)
                                val isWinning = (r to c) in state.winningCells
                                val isLast = state.lastMove == (r to c)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clickable(enabled = canInteract && cellVal == 0) {
                                            onCellTap(r, c)
                                        }
                                        .testTag("c5_cell_${r}_${c}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cellVal != 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize(0.76f)
                                                .clip(CircleShape)
                                                .background(if (cellVal == 1) tokens.gold else tokens.tealAccent)
                                                .border(
                                                    width = if (isWinning || isLast) 2.dp else 1.2.dp,
                                                    color = when {
                                                        isWinning -> tokens.emeraldAccent
                                                        isLast -> tokens.coralAccent
                                                        else -> tokens.border
                                                    },
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessBoardCard(
    state: ChessState,
    selectedSquare: Int?,
    canInteract: Boolean,
    onSquareTap: (Int) -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val legalTargets = remember(state, selectedSquare) {
        selectedSquare?.let { ChessEngine.legalMovesForSquare(state, it).map { m -> m.to }.toSet() } ?: emptySet()
    }

    BlueprintCard(
        modifier = Modifier.fillMaxWidth(),
        showCornerBracket = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Captured pieces row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAP: " + state.capturedBlack.joinToString("") { it.type.glyph(it.color) }.ifBlank { "—" },
                    fontSize = 13.sp,
                    color = tokens.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = state.moveHistory.takeLast(3).joinToString(" "),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    color = tokens.tealAccent,
                    maxLines = 1
                )
            }

            // 8x8 Blueprint Chess Board
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(2.dp, tokens.border)
            ) {
                for (r in 0 until 8) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until 8) {
                            val sq = r * 8 + c
                            val isDarkSq = (r + c) % 2 == 1
                            val isSelected = selectedSquare == sq
                            val isLegalTarget = sq in legalTargets
                            val isLast = state.lastMoveFrom == sq || state.lastMoveTo == sq
                            val piece = state.board[sq]
                            val isInCheckKing = piece?.type == PieceType.KING && piece.color == state.inCheckColor

                            val baseBg = if (isDarkSq) {
                                tokens.libraryBg.copy(alpha = 0.24f)
                            } else {
                                tokens.paperBg
                            }
                            val bg = when {
                                isInCheckKing -> tokens.coralAccent.copy(alpha = 0.55f)
                                isSelected -> tokens.gold.copy(alpha = 0.45f)
                                isLast -> tokens.tealAccent.copy(alpha = 0.25f)
                                else -> baseBg
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(bg)
                                    .border(0.4.dp, tokens.gridMajor.copy(alpha = 0.5f))
                                    .clickable(enabled = canInteract) { onSquareTap(sq) }
                                    .testTag("chess_sq_$sq"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (piece != null) {
                                    Text(
                                        text = piece.type.glyph(piece.color),
                                        fontSize = 24.sp,
                                        color = if (piece.color == PieceColor.WHITE) tokens.gold else tokens.inkPrimary
                                    )
                                }
                                if (isLegalTarget) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        if (piece == null) {
                                            drawCircle(
                                                color = tokens.emeraldAccent.copy(alpha = 0.8f),
                                                radius = size.minDimension * 0.16f
                                            )
                                        } else {
                                            drawCircle(
                                                color = tokens.coralAccent.copy(alpha = 0.85f),
                                                radius = size.minDimension * 0.42f,
                                                style = Stroke(width = 3.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LOST: " + state.capturedWhite.joinToString("") { it.type.glyph(it.color) }.ifBlank { "—" },
                    fontSize = 13.sp,
                    color = tokens.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "TAP PIECE -> TAP DOT",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    color = tokens.inkMuted
                )
            }
        }
    }
}
