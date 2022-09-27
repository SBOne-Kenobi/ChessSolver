data class QueenSolution(
    val queenPositions: List<Position>
)

private val positionComparator = compareBy<Position> { it.row }.thenBy { it.col }

data class Position(
    val row: Int,
    val col: Int
) : Comparable<Position> {
    override fun compareTo(other: Position): Int {
        return positionComparator.compare(this, other)
    }
}