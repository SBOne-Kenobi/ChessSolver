import com.microsoft.z3.ArithExpr
import com.microsoft.z3.BitVecExpr
import com.microsoft.z3.BitVecNum
import com.microsoft.z3.BoolExpr
import com.microsoft.z3.Context
import com.microsoft.z3.Status

class QueenSolver(val queens: Int) : Iterator<QueenSolution>, AutoCloseable {
    private val context = Context()
    private val params = context.mkDefaultParams()
    private val z3Solver = context.mkSolver().apply { setParameters(params) }

    private val rows = addQueenConstraints()

    /**
     * Create rows as bitvectors.
     */
    private fun Context.mkRowConsts() = List(queens) { i ->
        val name = mkSymbol(i)
        mkBVConst(name, queens)
    }

    /**
     * Main idea: (bv == 0) || (bv & (bv - 1) == 0)
     */
    private fun Context.atMostOnceInRow(bv: BitVecExpr): BoolExpr {
        val zero = mkBV(0, queens)
        val one = mkBV(1, queens)
        val isZero = mkEq(zero, bv)
        val shiftedResult = mkBVAND(bv, mkBVSub(bv, one))
        val resIsZero = mkEq(zero, shiftedResult)
        return mkOr(isZero, resIsZero)
    }

    private fun Context.sum(bv: BitVecExpr): ArithExpr {
        var sumOfOnes = mkInt(0) as ArithExpr
        repeat(bv.sortSize) { i ->
            val extracted = mkBV2Int(mkExtract(i, i, bv), false)
            sumOfOnes = mkAdd(sumOfOnes, extracted)
        }
        return sumOfOnes
    }

    /**
     * Main idea: sum of rows must contain ones as many as queens.
     */
    private fun Context.atMostOnceInCol(rows: List<BitVecExpr>): BoolExpr {
        val sum = rows.reduce(::mkBVAdd)
        val sumOfOnes = sum(sum)
        return mkEq(sumOfOnes, mkInt(queens))
    }

    private fun Context.shiftDiagIntoColAndCheck(
        extended: List<BitVecExpr>,
        shift: (Int, BitVecExpr) -> BitVecExpr
    ): BoolExpr {
        val shifted = extended.mapIndexed(shift)
        return atMostOnceInCol(shifted)
    }

    /**
     * Main idea: check diagonals is the same with shift rows and check columns.
     */
    private fun Context.atMostOnceInDiagonal(rows: List<BitVecExpr>): BoolExpr {
        val extendedSize = 2 * queens

        val extendedLeft = rows.map { mkConcat(mkBV(0, queens), it) }
        val mainDiagOk = shiftDiagIntoColAndCheck(extendedLeft) { i, bv ->
            mkBVSHL(bv, mkBV(i, extendedSize))
        }

        val extendedRight = rows.map { mkConcat(it, mkBV(0, queens)) }
        val sideDiagOk = shiftDiagIntoColAndCheck(extendedRight) { i, bv ->
            mkBVLSHR(bv, mkBV(i, extendedSize))
        }

        return mkAnd(mainDiagOk, sideDiagOk)
    }

    private fun addQueenConstraints(): List<BitVecExpr> {
        context.run {
            val rows = mkRowConsts()

            rows.forEach { bv ->
                z3Solver.add(atMostOnceInRow(bv))
            }
            z3Solver.add(atMostOnceInCol(rows))
            z3Solver.add(atMostOnceInDiagonal(rows))

            return rows
        }
    }

    override fun close() {
        context.close()
    }

    private val handSolutions = mutableListOf<QueenSolution>()

    override fun hasNext(): Boolean {
        if (handSolutions.isNotEmpty()) {
            return true
        }
        val status = z3Solver.check()
        return status == Status.SATISFIABLE
    }

    private fun Int.log2(): Int {
        var res = 0
        var nxt = 2
        while (nxt <= this) {
            nxt *= 2
            ++res
        }
        assert(2 * this == nxt)
        return res
    }

    private fun Int.exp2(): Int {
        var res = 1
        repeat(this) { res *= 2 }
        return res
    }

    /**
     * Optimization that reduces number of calls of z3.
     */
    private fun buildHandSolutions(positions: List<Position>): List<QueenSolution> {
        val original = positions.sorted()
        val mirrorRow = positions.map {
            it.copy(row = queens - 1 - it.row)
        }.sorted()
        val mirrorCol = positions.map {
            it.copy(col = queens - 1 - it.col)
        }.sorted()
        val mirrorAll = positions.map {
            Position(queens - 1 - it.row, queens - 1 - it.col)
        }.sorted()
        return listOf(original, mirrorRow, mirrorCol, mirrorAll)
            .distinctBy { it.toString() }
            .map(::QueenSolution)
    }

    private fun Context.solutionConstraints(solution: QueenSolution): BoolExpr =
        solution.queenPositions.map { (row, col) ->
            mkEq(rows[row], mkBV(col.exp2(), queens))
        }.reduce { a, b -> mkAnd(a, b) }

    override fun next(): QueenSolution {
        if (handSolutions.isEmpty()) {
            val model = z3Solver.model
            val evaluated = rows.map { model.eval(it, false) as BitVecNum }
            val positions = evaluated.mapIndexedNotNull { i, bv ->
                val value = bv.int
                if (value == 0) {
                    null
                } else {
                    Position(i, value.log2())
                }
            }

            val newSolutions = buildHandSolutions(positions)

            newSolutions.forEach {
                val solutionConstraint = context.solutionConstraints(it)
                z3Solver.add(context.mkNot(solutionConstraint))
            }

            handSolutions.addAll(newSolutions)
        }

        return handSolutions.removeLast()
    }

}