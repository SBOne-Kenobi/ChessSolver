/*
size    queens  result
8       8       92
4       4       2
3       3       0
*/
fun main() {
    Z3Initializer.init()
    val n = 8
    val queenSolver3 = QueenSolver(n)
//    queenSolver3.printAllSolutions()
    println(queenSolver3.numberOfSolutions())
    queenSolver3.close()
}
