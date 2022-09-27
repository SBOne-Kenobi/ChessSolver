import com.microsoft.z3.Context
import com.microsoft.z3.Params

fun Context.mkDefaultParams(): Params = mkParams().apply {
    add("random_seed", 42)
    add("randomize", false)
}

fun QueenSolution.showString(size: Int = 8): String {
    val field = List(size) { StringBuilder("_".repeat(size)) }
    queenPositions.forEach { (row, col) ->
        field[row][col] = '*'
    }
    return field.joinToString("\n")
}

fun QueenSolver.printAllSolutions() {
    forEach {
        println(it.showString(queens))
        println("----------------")
    }
}

fun QueenSolver.numberOfSolutions(): Int =
    asSequence().count()