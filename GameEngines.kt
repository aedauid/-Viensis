package com.example.ui.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class StudioGameType(val label: String, val subtitle: String) {
    CONNECT_3("CONNECT 3", "4x5 gravity blueprint"),
    CONNECT_5("CONNECT 5", "10x10 five-in-a-row"),
    CHESS("CHESS", "8x8 studio tactics")
}

// ============================================================================
// CONNECT 3 ENGINE (4 rows x 5 cols)
// ============================================================================

data class Connect3State(
    val grid: List<Int> = List(ROWS * COLS) { 0 },
    val currentPlayer: Int = 1,
    val winner: Int = 0,
    val isDraw: Boolean = false,
    val winningCells: Set<Pair<Int, Int>> = emptySet(),
    val lastMove: Pair<Int, Int>? = null,
    val history: List<Connect3Snapshot> = emptyList()
) {
    val isGameOver: Boolean get() = winner != 0 || isDraw

    fun cell(r: Int, c: Int): Int = grid[r * COLS + c]

    companion object {
        const val ROWS = 4
        const val COLS = 5
    }
}

data class Connect3Snapshot(
    val grid: List<Int>,
    val currentPlayer: Int,
    val lastMove: Pair<Int, Int>?
)

object Connect3Engine {
    fun dropDisc(state: Connect3State, col: Int): Connect3State {
        if (state.isGameOver || col !in 0 until Connect3State.COLS) return state
        var targetRow = -1
        for (r in Connect3State.ROWS - 1 downTo 0) {
            if (state.cell(r, col) == 0) {
                targetRow = r
                break
            }
        }
        if (targetRow == -1) return state

        val nextGrid = state.grid.toMutableList()
        nextGrid[targetRow * Connect3State.COLS + col] = state.currentPlayer
        val winCells = findWin3(nextGrid, state.currentPlayer)
        val winner = if (winCells.isNotEmpty()) state.currentPlayer else 0
        val isDraw = winner == 0 && nextGrid.none { it == 0 }

        return state.copy(
            grid = nextGrid,
            currentPlayer = if (winner == 0 && !isDraw) (3 - state.currentPlayer) else state.currentPlayer,
            winner = winner,
            isDraw = isDraw,
            winningCells = winCells,
            lastMove = targetRow to col,
            history = state.history + Connect3Snapshot(state.grid, state.currentPlayer, state.lastMove)
        )
    }

    fun undo(state: Connect3State, steps: Int = 1): Connect3State {
        var cur = state
        repeat(steps) {
            val prev = cur.history.lastOrNull() ?: return cur
            cur = Connect3State(
                grid = prev.grid,
                currentPlayer = prev.currentPlayer,
                winner = 0,
                isDraw = false,
                winningCells = emptySet(),
                lastMove = prev.lastMove,
                history = cur.history.dropLast(1)
            )
        }
        return cur
    }

    fun pickAiColumn(state: Connect3State): Int {
        val ai = state.currentPlayer
        val human = 3 - ai
        val validCols = (0 until Connect3State.COLS).filter { state.cell(0, it) == 0 }
        if (validCols.isEmpty()) return 0

        // 1. Immediate win
        for (c in validCols) {
            if (dropDisc(state, c).winner == ai) return c
        }
        // 2. Immediate block
        val humanSim = state.copy(currentPlayer = human)
        for (c in validCols) {
            if (dropDisc(humanSim, c).winner == human) return c
        }
        // 3. Avoid gifting opponent an immediate win above our move, prefer center & pairs
        val center = Connect3State.COLS / 2
        return validCols.maxByOrNull { c ->
            val afterAi = dropDisc(state, c)
            val givesHumanWin = validCols.any { hc ->
                afterAi.cell(0, hc) == 0 && dropDisc(afterAi, hc).winner == human
            }
            val centerBonus = (3 - abs(c - center)) * 4
            val adjacentBonus = scoreAdjacentConnect3(afterAi, ai)
            (if (givesHumanWin) -1000 else 0) + centerBonus + adjacentBonus
        } ?: validCols.first()
    }

    private fun scoreAdjacentConnect3(state: Connect3State, player: Int): Int {
        var score = 0
        for (r in 0 until Connect3State.ROWS) {
            for (c in 0 until Connect3State.COLS) {
                if (state.cell(r, c) != player) continue
                val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
                for ((dr, dc) in dirs) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until Connect3State.ROWS && nc in 0 until Connect3State.COLS &&
                        state.cell(nr, nc) == player
                    ) {
                        score += 5
                    }
                }
            }
        }
        return score
    }

    private fun findWin3(grid: List<Int>, player: Int): Set<Pair<Int, Int>> {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        val won = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until Connect3State.ROWS) {
            for (c in 0 until Connect3State.COLS) {
                if (grid[r * Connect3State.COLS + c] != player) continue
                for ((dr, dc) in dirs) {
                    val cells = (0 until 3).map { k -> (r + dr * k) to (c + dc * k) }
                    if (cells.all { (rr, cc) ->
                            rr in 0 until Connect3State.ROWS &&
                                cc in 0 until Connect3State.COLS &&
                                grid[rr * Connect3State.COLS + cc] == player
                        }
                    ) {
                        won.addAll(cells)
                    }
                }
            }
        }
        return won
    }
}

// ============================================================================
// CONNECT 5 (GOMOKU) ENGINE (10 x 10 grid)
// ============================================================================

data class Connect5State(
    val grid: List<Int> = List(SIZE * SIZE) { 0 },
    val currentPlayer: Int = 1,
    val winner: Int = 0,
    val isDraw: Boolean = false,
    val winningCells: Set<Pair<Int, Int>> = emptySet(),
    val lastMove: Pair<Int, Int>? = null,
    val history: List<Connect5Snapshot> = emptyList()
) {
    val isGameOver: Boolean get() = winner != 0 || isDraw

    fun cell(r: Int, c: Int): Int = grid[r * SIZE + c]

    companion object {
        const val SIZE = 10
    }
}

data class Connect5Snapshot(
    val grid: List<Int>,
    val currentPlayer: Int,
    val lastMove: Pair<Int, Int>?
)

object Connect5Engine {
    fun placeStone(state: Connect5State, row: Int, col: Int): Connect5State {
        if (state.isGameOver ||
            row !in 0 until Connect5State.SIZE ||
            col !in 0 until Connect5State.SIZE ||
            state.cell(row, col) != 0
        ) return state

        val nextGrid = state.grid.toMutableList()
        nextGrid[row * Connect5State.SIZE + col] = state.currentPlayer
        val winCells = findWin5(nextGrid, state.currentPlayer)
        val winner = if (winCells.isNotEmpty()) state.currentPlayer else 0
        val isDraw = winner == 0 && nextGrid.none { it == 0 }

        return state.copy(
            grid = nextGrid,
            currentPlayer = if (winner == 0 && !isDraw) (3 - state.currentPlayer) else state.currentPlayer,
            winner = winner,
            isDraw = isDraw,
            winningCells = winCells,
            lastMove = row to col,
            history = state.history + Connect5Snapshot(state.grid, state.currentPlayer, state.lastMove)
        )
    }

    fun undo(state: Connect5State, steps: Int = 1): Connect5State {
        var cur = state
        repeat(steps) {
            val prev = cur.history.lastOrNull() ?: return cur
            cur = Connect5State(
                grid = prev.grid,
                currentPlayer = prev.currentPlayer,
                winner = 0,
                isDraw = false,
                winningCells = emptySet(),
                lastMove = prev.lastMove,
                history = cur.history.dropLast(1)
            )
        }
        return cur
    }

    fun pickAiCell(state: Connect5State): Pair<Int, Int> {
        val size = Connect5State.SIZE
        val ai = state.currentPlayer
        val human = 3 - ai

        // First move: play center
        if (state.grid.all { it == 0 }) return (size / 2) to (size / 2)

        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (state.cell(r, c) != 0) continue
                if (hasNeighborWithin(state, r, c, 2)) {
                    candidates.add(r to c)
                }
            }
        }
        if (candidates.isEmpty()) return (size / 2) to (size / 2)

        var bestCell = candidates.first()
        var bestScore = Long.MIN_VALUE

        for ((r, c) in candidates) {
            val atk = evaluatePoint(state.grid, r, c, ai)
            val def = evaluatePoint(state.grid, r, c, human)
            val centerDist = abs(r - size / 2) + abs(c - size / 2)
            // Prioritize winning immediately, else blocking opponent 4/open-3, else building
            val score = atk * 11L + def * 10L - centerDist
            if (score > bestScore) {
                bestScore = score
                bestCell = r to c
            }
        }
        return bestCell
    }

    private fun hasNeighborWithin(state: Connect5State, row: Int, col: Int, dist: Int): Boolean {
        val rMin = max(0, row - dist)
        val rMax = min(Connect5State.SIZE - 1, row + dist)
        val cMin = max(0, col - dist)
        val cMax = min(Connect5State.SIZE - 1, col + dist)
        for (r in rMin..rMax) {
            for (c in cMin..cMax) {
                if (state.cell(r, c) != 0) return true
            }
        }
        return false
    }

    private fun evaluatePoint(grid: List<Int>, row: Int, col: Int, player: Int): Long {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        var total = 0L
        for ((dr, dc) in dirs) {
            var count = 1
            var openEnds = 0

            var step = 1
            while (step <= 4) {
                val nr = row + dr * step
                val nc = col + dc * step
                if (nr !in 0 until Connect5State.SIZE || nc !in 0 until Connect5State.SIZE) break
                val v = grid[nr * Connect5State.SIZE + nc]
                if (v == player) {
                    count++
                    step++
                } else {
                    if (v == 0) openEnds++
                    break
                }
            }

            step = 1
            while (step <= 4) {
                val nr = row - dr * step
                val nc = col - dc * step
                if (nr !in 0 until Connect5State.SIZE || nc !in 0 until Connect5State.SIZE) break
                val v = grid[nr * Connect5State.SIZE + nc]
                if (v == player) {
                    count++
                    step++
                } else {
                    if (v == 0) openEnds++
                    break
                }
            }

            total += when {
                count >= 5 -> 1_000_000L
                count == 4 && openEnds == 2 -> 90_000L
                count == 4 && openEnds == 1 -> 15_000L
                count == 3 && openEnds == 2 -> 8_000L
                count == 3 && openEnds == 1 -> 900L
                count == 2 && openEnds == 2 -> 350L
                count == 2 && openEnds == 1 -> 60L
                openEnds > 0 -> 12L
                else -> 1L
            }
        }
        return total
    }

    private fun findWin5(grid: List<Int>, player: Int): Set<Pair<Int, Int>> {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        val won = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until Connect5State.SIZE) {
            for (c in 0 until Connect5State.SIZE) {
                if (grid[r * Connect5State.SIZE + c] != player) continue
                for ((dr, dc) in dirs) {
                    val cells = (0 until 5).map { k -> (r + dr * k) to (c + dc * k) }
                    if (cells.all { (rr, cc) ->
                            rr in 0 until Connect5State.SIZE &&
                                cc in 0 until Connect5State.SIZE &&
                                grid[rr * Connect5State.SIZE + cc] == player
                        }
                    ) {
                        won.addAll(cells)
                    }
                }
            }
        }
        return won
    }
}

// ============================================================================
// CHESS ENGINE (Complete 8x8 Rules + Castling + En Passant + 2-Ply Alpha-Beta AI)
// ============================================================================

enum class PieceColor {
    WHITE, BLACK;
    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbolWhite: String, val symbolBlack: String, val letter: String, val value: Int) {
    KING("♔", "♚", "K", 20000),
    QUEEN("♕", "♛", "Q", 900),
    ROOK("♖", "♜", "R", 500),
    BISHOP("♗", "♝", "B", 330),
    KNIGHT("♘", "♞", "N", 320),
    PAWN("♙", "♟", "", 100);

    fun glyph(color: PieceColor): String = if (color == PieceColor.WHITE) symbolWhite else symbolBlack
}

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor
)

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
)

data class ChessMove(
    val from: Int,
    val to: Int,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false
)

enum class ChessStatus {
    PLAYING, CHECK, CHECKMATE, STALEMATE, DRAW
}

data class ChessSnapshot(
    val board: List<ChessPiece?>,
    val turn: PieceColor,
    val castling: CastlingRights,
    val enPassantSquare: Int?,
    val halfMoveClock: Int,
    val capturedWhite: List<ChessPiece>,
    val capturedBlack: List<ChessPiece>,
    val lastMoveFrom: Int?,
    val lastMoveTo: Int?,
    val moveHistory: List<String>
)

data class ChessState(
    val board: List<ChessPiece?> = initialBoard(),
    val turn: PieceColor = PieceColor.WHITE,
    val castling: CastlingRights = CastlingRights(),
    val enPassantSquare: Int? = null,
    val halfMoveClock: Int = 0,
    val status: ChessStatus = ChessStatus.PLAYING,
    val winner: PieceColor? = null,
    val inCheckColor: PieceColor? = null,
    val capturedWhite: List<ChessPiece> = emptyList(),
    val capturedBlack: List<ChessPiece> = emptyList(),
    val lastMoveFrom: Int? = null,
    val lastMoveTo: Int? = null,
    val moveHistory: List<String> = emptyList(),
    val history: List<ChessSnapshot> = emptyList()
) {
    val isGameOver: Boolean
        get() = status == ChessStatus.CHECKMATE || status == ChessStatus.STALEMATE || status == ChessStatus.DRAW

    companion object {
        fun initialBoard(): List<ChessPiece?> {
            val backRank = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
            )
            return List(64) { idx ->
                val r = idx / 8
                val c = idx % 8
                when (r) {
                    0 -> ChessPiece(backRank[c], PieceColor.BLACK)
                    1 -> ChessPiece(PieceType.PAWN, PieceColor.BLACK)
                    6 -> ChessPiece(PieceType.PAWN, PieceColor.WHITE)
                    7 -> ChessPiece(backRank[c], PieceColor.WHITE)
                    else -> null
                }
            }
        }
    }
}

object ChessEngine {
    fun legalMovesForSquare(state: ChessState, square: Int): List<ChessMove> {
        if (state.isGameOver) return emptyList()
        val piece = state.board.getOrNull(square) ?: return emptyList()
        if (piece.color != state.turn) return emptyList()
        return legalMoves(state).filter { it.from == square }
    }

    fun legalMoves(state: ChessState): List<ChessMove> {
        val pseudo = generatePseudoMoves(state.board, state.turn, state.castling, state.enPassantSquare)
        return pseudo.filter { move ->
            val nextBoard = applyBoardMove(state.board, move, state.turn)
            !isKingInCheck(nextBoard, state.turn)
        }
    }

    fun applyMove(state: ChessState, move: ChessMove): ChessState {
        val movingPiece = state.board[move.from] ?: return state
        val capturedPiece = if (move.isEnPassant) {
            val capRow = move.from / 8
            val capCol = move.to % 8
            state.board[capRow * 8 + capCol]
        } else {
            state.board[move.to]
        }

        val nextBoard = applyBoardMove(state.board, move, state.turn)
        val nextCastling = updateCastlingRights(state.castling, move, movingPiece, capturedPiece)
        val nextEnPassant = if (movingPiece.type == PieceType.PAWN && abs(move.from / 8 - move.to / 8) == 2) {
            ((move.from / 8 + move.to / 8) / 2) * 8 + (move.from % 8)
        } else {
            null
        }
        val nextHalfMove = if (movingPiece.type == PieceType.PAWN || capturedPiece != null) 0 else state.halfMoveClock + 1
        val nextCapturedWhite = if (capturedPiece?.color == PieceColor.WHITE) state.capturedWhite + capturedPiece else state.capturedWhite
        val nextCapturedBlack = if (capturedPiece?.color == PieceColor.BLACK) state.capturedBlack + capturedPiece else state.capturedBlack
        val nextTurn = state.turn.opposite()

        val targetInCheck = isKingInCheck(nextBoard, nextTurn)
        val probeState = ChessState(
            board = nextBoard,
            turn = nextTurn,
            castling = nextCastling,
            enPassantSquare = nextEnPassant,
            halfMoveClock = nextHalfMove
        )
        val oppHasMoves = legalMoves(probeState).isNotEmpty()

        val nextStatus = when {
            !oppHasMoves && targetInCheck -> ChessStatus.CHECKMATE
            !oppHasMoves && !targetInCheck -> ChessStatus.STALEMATE
            nextHalfMove >= 100 || isInsufficientMaterial(nextBoard) -> ChessStatus.DRAW
            targetInCheck -> ChessStatus.CHECK
            else -> ChessStatus.PLAYING
        }
        val nextWinner = if (nextStatus == ChessStatus.CHECKMATE) state.turn else null
        val notation = formatMoveNotation(movingPiece, move, capturedPiece != null, targetInCheck, nextStatus == ChessStatus.CHECKMATE)

        val snapshot = ChessSnapshot(
            board = state.board,
            turn = state.turn,
            castling = state.castling,
            enPassantSquare = state.enPassantSquare,
            halfMoveClock = state.halfMoveClock,
            capturedWhite = state.capturedWhite,
            capturedBlack = state.capturedBlack,
            lastMoveFrom = state.lastMoveFrom,
            lastMoveTo = state.lastMoveTo,
            moveHistory = state.moveHistory
        )

        return ChessState(
            board = nextBoard,
            turn = nextTurn,
            castling = nextCastling,
            enPassantSquare = nextEnPassant,
            halfMoveClock = nextHalfMove,
            status = nextStatus,
            winner = nextWinner,
            inCheckColor = if (targetInCheck) nextTurn else null,
            capturedWhite = nextCapturedWhite,
            capturedBlack = nextCapturedBlack,
            lastMoveFrom = move.from,
            lastMoveTo = move.to,
            moveHistory = state.moveHistory + notation,
            history = state.history + snapshot
        )
    }

    fun undo(state: ChessState, steps: Int = 1): ChessState {
        var cur = state
        repeat(steps) {
            val prev = cur.history.lastOrNull() ?: return cur
            val check = isKingInCheck(prev.board, prev.turn)
            cur = ChessState(
                board = prev.board,
                turn = prev.turn,
                castling = prev.castling,
                enPassantSquare = prev.enPassantSquare,
                halfMoveClock = prev.halfMoveClock,
                status = if (check) ChessStatus.CHECK else ChessStatus.PLAYING,
                winner = null,
                inCheckColor = if (check) prev.turn else null,
                capturedWhite = prev.capturedWhite,
                capturedBlack = prev.capturedBlack,
                lastMoveFrom = prev.lastMoveFrom,
                lastMoveTo = prev.lastMoveTo,
                moveHistory = prev.moveHistory,
                history = cur.history.dropLast(1)
            )
        }
        return cur
    }

    fun pickAiMove(state: ChessState): ChessMove? {
        val moves = orderedMoves(state, legalMoves(state))
        if (moves.isEmpty()) return null
        val aiColor = state.turn
        var bestMove = moves.first()
        var bestEval = Int.MIN_VALUE / 2

        for (move in moves) {
            val next = applyMoveFast(state, move)
            val score = -negamax(next, depth = 1, alpha = -1_000_000, beta = 1_000_000, perspective = aiColor.opposite())
            if (score > bestEval) {
                bestEval = score
                bestMove = move
            }
        }
        return bestMove
    }

    private fun negamax(state: ChessState, depth: Int, alpha: Int, beta: Int, perspective: PieceColor): Int {
        val moves = legalMoves(state)
        if (moves.isEmpty()) {
            return if (isKingInCheck(state.board, perspective)) -100_000 - depth else 0
        }
        if (depth == 0) {
            return evaluateBoard(state.board, perspective)
        }
        var a = alpha
        var best = Int.MIN_VALUE / 2
        for (m in orderedMoves(state, moves)) {
            val child = applyMoveFast(state, m)
            val v = -negamax(child, depth - 1, -beta, -a, perspective.opposite())
            if (v > best) best = v
            if (best > a) a = best
            if (a >= beta) break
        }
        return best
    }

    private fun orderedMoves(state: ChessState, moves: List<ChessMove>): List<ChessMove> {
        return moves.sortedByDescending { m ->
            val cap = state.board[m.to]?.type?.value ?: 0
            val promo = m.promotion?.value ?: 0
            cap * 10 + promo
        }
    }

    private fun applyMoveFast(state: ChessState, move: ChessMove): ChessState {
        val moving = state.board[move.from]
        val cap = state.board[move.to]
        val nextBoard = applyBoardMove(state.board, move, state.turn)
        val nextCastling = if (moving != null) updateCastlingRights(state.castling, move, moving, cap) else state.castling
        val nextEp = if (moving?.type == PieceType.PAWN && abs(move.from / 8 - move.to / 8) == 2) {
            ((move.from / 8 + move.to / 8) / 2) * 8 + (move.from % 8)
        } else null
        return ChessState(
            board = nextBoard,
            turn = state.turn.opposite(),
            castling = nextCastling,
            enPassantSquare = nextEp
        )
    }

    private fun evaluateBoard(board: List<ChessPiece?>, perspective: PieceColor): Int {
        var score = 0
        for (idx in 0 until 64) {
            val p = board[idx] ?: continue
            val r = idx / 8
            val c = idx % 8
            val centerBonus = (3.5 - abs(3.5 - r)) * 4 + (3.5 - abs(3.5 - c)) * 4
            val advanceBonus = if (p.type == PieceType.PAWN) {
                if (p.color == PieceColor.WHITE) (6 - r) * 8 else (r - 1) * 8
            } else 0
            val valTotal = p.type.value + centerBonus.toInt() + advanceBonus
            score += if (p.color == perspective) valTotal else -valTotal
        }
        return score
    }

    private fun applyBoardMove(board: List<ChessPiece?>, move: ChessMove, color: PieceColor): List<ChessPiece?> {
        val next = board.toMutableList()
        val piece = next[move.from] ?: return next
        next[move.from] = null

        if (move.isEnPassant) {
            val capRow = move.from / 8
            val capCol = move.to % 8
            next[capRow * 8 + capCol] = null
        }

        if (move.isCastling) {
            val row = move.from / 8
            if (move.to % 8 == 6) {
                next[row * 8 + 5] = next[row * 8 + 7]
                next[row * 8 + 7] = null
            } else if (move.to % 8 == 2) {
                next[row * 8 + 3] = next[row * 8 + 0]
                next[row * 8 + 0] = null
            }
        }

        val finalType = move.promotion ?: piece.type
        next[move.to] = ChessPiece(finalType, color)
        return next
    }

    private fun updateCastlingRights(
        rights: CastlingRights,
        move: ChessMove,
        moving: ChessPiece,
        captured: ChessPiece?
    ): CastlingRights {
        var wk = rights.whiteKingSide
        var wq = rights.whiteQueenSide
        var bk = rights.blackKingSide
        var bq = rights.blackQueenSide

        if (moving.color == PieceColor.WHITE && moving.type == PieceType.KING) {
            wk = false; wq = false
        } else if (moving.color == PieceColor.BLACK && moving.type == PieceType.KING) {
            bk = false; bq = false
        }

        if (move.from == 63 || move.to == 63) wk = false
        if (move.from == 56 || move.to == 56) wq = false
        if (move.from == 7 || move.to == 7) bk = false
        if (move.from == 0 || move.to == 0) bq = false

        if (captured == null && false) Unit
        return CastlingRights(wk, wq, bk, bq)
    }

    private fun generatePseudoMoves(
        board: List<ChessPiece?>,
        turn: PieceColor,
        castling: CastlingRights,
        enPassantSquare: Int?
    ): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (idx in 0 until 64) {
            val piece = board[idx] ?: continue
            if (piece.color != turn) continue
            val r = idx / 8
            val c = idx % 8

            when (piece.type) {
                PieceType.PAWN -> {
                    val dir = if (turn == PieceColor.WHITE) -1 else 1
                    val startRow = if (turn == PieceColor.WHITE) 6 else 1
                    val promoRow = if (turn == PieceColor.WHITE) 0 else 7
                    val oneStepRow = r + dir
                    if (oneStepRow in 0..7) {
                        val forward = oneStepRow * 8 + c
                        if (board[forward] == null) {
                            if (oneStepRow == promoRow) {
                                listOf(PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP).forEach { pt ->
                                    moves.add(ChessMove(idx, forward, promotion = pt))
                                }
                            } else {
                                moves.add(ChessMove(idx, forward))
                                if (r == startRow) {
                                    val twoStep = (r + 2 * dir) * 8 + c
                                    if (board[twoStep] == null) {
                                        moves.add(ChessMove(idx, twoStep))
                                    }
                                }
                            }
                        }
                        for (dc in listOf(-1, 1)) {
                            val nc = c + dc
                            if (nc in 0..7) {
                                val diag = oneStepRow * 8 + nc
                                val target = board[diag]
                                if (target != null && target.color != turn) {
                                    if (oneStepRow == promoRow) {
                                        listOf(PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP).forEach { pt ->
                                            moves.add(ChessMove(idx, diag, promotion = pt))
                                        }
                                    } else {
                                        moves.add(ChessMove(idx, diag))
                                    }
                                } else if (enPassantSquare != null && diag == enPassantSquare) {
                                    moves.add(ChessMove(idx, diag, isEnPassant = true))
                                }
                            }
                        }
                    }
                }
                PieceType.KNIGHT -> {
                    val jumps = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                    for ((dr, dc) in jumps) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val to = nr * 8 + nc
                            if (board[to]?.color != turn) moves.add(ChessMove(idx, to))
                        }
                    }
                }
                PieceType.BISHOP -> addSlidingMoves(board, idx, turn, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1), moves)
                PieceType.ROOK -> addSlidingMoves(board, idx, turn, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1), moves)
                PieceType.QUEEN -> addSlidingMoves(
                    board,
                    idx,
                    turn,
                    listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1),
                    moves
                )
                PieceType.KING -> {
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0..7 && nc in 0..7) {
                                val to = nr * 8 + nc
                                if (board[to]?.color != turn) moves.add(ChessMove(idx, to))
                            }
                        }
                    }
                    // Castling
                    if (turn == PieceColor.WHITE && idx == 60 && !isKingInCheck(board, PieceColor.WHITE)) {
                        if (castling.whiteKingSide && board[61] == null && board[62] == null &&
                            board[63]?.type == PieceType.ROOK && board[63]?.color == PieceColor.WHITE &&
                            !isSquareAttacked(board, 61, PieceColor.BLACK) &&
                            !isSquareAttacked(board, 62, PieceColor.BLACK)
                        ) {
                            moves.add(ChessMove(60, 62, isCastling = true))
                        }
                        if (castling.whiteQueenSide && board[59] == null && board[58] == null && board[57] == null &&
                            board[56]?.type == PieceType.ROOK && board[56]?.color == PieceColor.WHITE &&
                            !isSquareAttacked(board, 59, PieceColor.BLACK) &&
                            !isSquareAttacked(board, 58, PieceColor.BLACK)
                        ) {
                            moves.add(ChessMove(60, 58, isCastling = true))
                        }
                    } else if (turn == PieceColor.BLACK && idx == 4 && !isKingInCheck(board, PieceColor.BLACK)) {
                        if (castling.blackKingSide && board[5] == null && board[6] == null &&
                            board[7]?.type == PieceType.ROOK && board[7]?.color == PieceColor.BLACK &&
                            !isSquareAttacked(board, 5, PieceColor.WHITE) &&
                            !isSquareAttacked(board, 6, PieceColor.WHITE)
                        ) {
                            moves.add(ChessMove(4, 6, isCastling = true))
                        }
                        if (castling.blackQueenSide && board[3] == null && board[2] == null && board[1] == null &&
                            board[0]?.type == PieceType.ROOK && board[0]?.color == PieceColor.BLACK &&
                            !isSquareAttacked(board, 3, PieceColor.WHITE) &&
                            !isSquareAttacked(board, 2, PieceColor.WHITE)
                        ) {
                            moves.add(ChessMove(4, 2, isCastling = true))
                        }
                    }
                }
            }
        }
        return moves
    }

    private fun addSlidingMoves(
        board: List<ChessPiece?>,
        from: Int,
        turn: PieceColor,
        dirs: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>
    ) {
        val r0 = from / 8
        val c0 = from % 8
        for ((dr, dc) in dirs) {
            var r = r0 + dr
            var c = c0 + dc
            while (r in 0..7 && c in 0..7) {
                val to = r * 8 + c
                val occupant = board[to]
                if (occupant == null) {
                    out.add(ChessMove(from, to))
                } else {
                    if (occupant.color != turn) out.add(ChessMove(from, to))
                    break
                }
                r += dr
                c += dc
            }
        }
    }

    fun isKingInCheck(board: List<ChessPiece?>, color: PieceColor): Boolean {
        val kingSq = board.indexOfFirst { it?.type == PieceType.KING && it.color == color }
        if (kingSq == -1) return false
        return isSquareAttacked(board, kingSq, color.opposite())
    }

    private fun isSquareAttacked(board: List<ChessPiece?>, square: Int, byColor: PieceColor): Boolean {
        val r = square / 8
        val c = square % 8

        // Pawns
        val pawnAttackRow = if (byColor == PieceColor.WHITE) r + 1 else r - 1
        if (pawnAttackRow in 0..7) {
            for (dc in listOf(-1, 1)) {
                val nc = c + dc
                if (nc in 0..7) {
                    val p = board[pawnAttackRow * 8 + nc]
                    if (p?.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // Knights
        val jumps = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        for ((dr, dc) in jumps) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7) {
                val p = board[nr * 8 + nc]
                if (p?.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        // Diagonals (Bishop / Queen)
        for ((dr, dc) in listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
            var nr = r + dr
            var nc = c + dc
            while (nr in 0..7 && nc in 0..7) {
                val p = board[nr * 8 + nc]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                nr += dr
                nc += dc
            }
        }

        // Orthogonals (Rook / Queen)
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            var nr = r + dr
            var nc = c + dc
            while (nr in 0..7 && nc in 0..7) {
                val p = board[nr * 8 + nc]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                nr += dr
                nc += dc
            }
        }

        // Adjacent King
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr
                val nc = c + dc
                if (nr in 0..7 && nc in 0..7) {
                    val p = board[nr * 8 + nc]
                    if (p?.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }
        return false
    }

    private fun isInsufficientMaterial(board: List<ChessPiece?>): Boolean {
        val nonKings = board.filterNotNull().filter { it.type != PieceType.KING }
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1 && (nonKings[0].type == PieceType.BISHOP || nonKings[0].type == PieceType.KNIGHT)) {
            return true
        }
        return false
    }

    private fun formatMoveNotation(
        piece: ChessPiece,
        move: ChessMove,
        isCapture: Boolean,
        isCheck: Boolean,
        isMate: Boolean
    ): String {
        if (move.isCastling) {
            return if (move.to % 8 == 6) "O-O" else "O-O-O"
        }
        val files = "abcdefgh"
        val toFile = files[move.to % 8]
        val toRank = 8 - (move.to / 8)
        val prefix = if (piece.type == PieceType.PAWN) {
            if (isCapture) "${files[move.from % 8]}x" else ""
        } else {
            piece.type.letter + if (isCapture) "x" else ""
        }
        val promo = move.promotion?.let { "=${it.letter}" } ?: ""
        val suffix = when {
            isMate -> "#"
            isCheck -> "+"
            else -> ""
        }
        return "$prefix$toFile$toRank$promo$suffix"
    }
}
