import org.nevec.rjm.BigDecimalMath
import java.io.File
import java.io.FileInputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.Arrays
import kotlin.random.Random

data class Neuron(
    var active: BigDecimal,
    var notActive: BigDecimal
)

val exp = { x: BigDecimal ->
    BigDecimal.ONE.divide(BigDecimal.ONE + BigDecimalMath.exp(-x), MathContext(5))
}
val dExp = { n: Neuron -> n.active * (BigDecimal.ONE - n.active) }

class NeuralNetwork(
    val size: ArrayList<Int>,
    val activate: (BigDecimal) -> BigDecimal,
    val dActivation: (Neuron) -> BigDecimal,
    val err: (ArrayList<Neuron>, ArrayList<BigDecimal>) -> ArrayList<BigDecimal>
) {
    val weights: ArrayList<ArrayList<ArrayList<BigDecimal>>> =
        Array(size.size - 1) { arrayListOf(arrayListOf<BigDecimal>()) }.toCollection(ArrayList())
    val neurons: ArrayList<ArrayList<Neuron>> = arrayListOf()

    init {
        for (i in 0 until size.size - 1) {
            weights[i] = Array(size[i] + 1) { arrayListOf<BigDecimal>() }.toCollection(ArrayList())
            for (j in weights[i].indices) {
                weights[i][j] = Array(size[i + 1]) { Random.nextDouble(-0.5, 0.5).toBigDecimal() }.toCollection(ArrayList())
            }
        }

        for (i in 0 until size.size) {
            neurons.add(Array(size[i]){ Neuron(BigDecimal.ZERO, BigDecimal.ZERO) }.toCollection(ArrayList()))
        }
    }

    fun calc(input: ArrayList<BigDecimal>): ArrayList<Neuron> {
        assert(neurons[0].size == input.size)

        for (i in input.indices) {
            neurons[0][i].notActive = BigDecimal.ZERO
            neurons[0][i].active = input[i]
        }

        for (i in 0 until size.size-1) {
            for (right in 0 until size[i+1]) {
                var rightNeuron = neurons[i+1][right]
                rightNeuron.notActive = weights[i][size[i]][right]
                for (left in 0 until size[i]) {
                    var leftActive = neurons[i][left].active
                    rightNeuron.notActive += weights[i][left][right] * leftActive
                }
                rightNeuron.active = activate(rightNeuron.notActive)
                neurons[i+1][right] = rightNeuron
            }
        }
        return neurons.last()
    }

    fun calcError(input: ArrayList<BigDecimal>, correct: ArrayList<BigDecimal>): ArrayList<BigDecimal> {
        val output = calc(input)
        return err(output, correct)
    }

    fun study(input: ArrayList<BigDecimal>, correct: ArrayList<BigDecimal>, nu: BigDecimal): ArrayList<BigDecimal> {
        val _error = calcError(input, correct)
        var error = _error
        for (i in size.size - 2 downTo 0) {
            val nextError = Array<BigDecimal>(size[i]) { BigDecimal.ZERO }
            for (left in 0 until size[i] + 1) {
                for (right in 0 until size[i + 1]) {
                    if (left != size[i]) { nextError[left] += weights[i][left][right] * error[right] }
                    val leftActive = if (left == size[i]) BigDecimal.ONE else neurons[i][left].active
                    val rightNeuron = neurons[i+1][right]
                    weights[i][left][right] += nu * error[right] * dActivation(rightNeuron) * leftActive
                }
            }
            error = nextError.toCollection(ArrayList())
        }
        return _error
    }
}

val n1 = BigDecimal(0.8)
val n0 = BigDecimal(0.2)

typealias Tests = ArrayList<Pair<ArrayList<BigDecimal>, ArrayList<BigDecimal>>>

fun sqrError(error: ArrayList<BigDecimal>): BigDecimal {
    val sumSqr = error.sumOf { x -> x * x }
    return if (sumSqr != BigDecimal.ZERO) BigDecimalMath.sqrt(sumSqr) else BigDecimal.ZERO
}

fun bigStudy(
    nn: NeuralNetwork,
    allTests: Tests,
    controlTests: Tests,
    nu: BigDecimal,
    correctError: BigDecimal = BigDecimal(1e-4)
): Triple<Int, BigDecimal, Tests> {
    var i = 0
    val errors = ArrayList<BigDecimal>()
    var reduceError: BigDecimal
    do {
        i++
        errors.clear()
        for (test in allTests) {
            val error = nn.study(test.first, test.second, nu)
            errors.add(sqrError(error))
        }
        for (test in controlTests) {
            val error = nn.calcError(test.first, test.second)
            errors.add(sqrError(error))
        }
        reduceError = sqrError(errors) / BigDecimal(errors.size)
        println("$i $reduceError\n")
    } while (reduceError > correctError)

    return Triple(i, reduceError, allTests)
}

fun testXor() {
    val nn = NeuralNetwork(
        arrayListOf(2, 2, 1),
        exp,
        dExp
    ) { output, correctOutput ->
        assert(output.size == correctOutput.size)

        val error = ArrayList<BigDecimal>()
        for (i in 0 until output.size) {
            var err = correctOutput[i] - output[i].active
            if (correctOutput[i] == n0) {
                err = min(err, BigDecimal.ZERO)
                if (err != BigDecimal.ZERO) {
                    err--
                }
            } else {
                err = max(err, BigDecimal.ZERO)
                if (err != BigDecimal.ZERO) {
                    err++
                }
            }
            error.add(err)
        }

        error
    }

    val res = bigStudy(
        nn,
        arrayListOf(
            Pair(arrayListOf(n0, n0), arrayListOf(n0)),
            Pair(arrayListOf(n0, n1), arrayListOf(n1)),
            Pair(arrayListOf(n1, n0), arrayListOf(n1)),
            Pair(arrayListOf(n1, n1), arrayListOf(n0)),
        ), arrayListOf(), BigDecimal("0.5"), BigDecimal(1e-7)
    )
    println("${res.first} ${res.second}")
    for (test in res.third) {
        println("$test ${nn.calc(test.first)}")
    }
    println(nn.weights)
}

fun testDigits(tests: Tests) {
    val nn = NeuralNetwork(
        arrayListOf(24, 10),
        exp,
        dExp
    ) { output, correctOutput ->
        assert(output.size == correctOutput.size)
        val error = ArrayList<BigDecimal>()
        var min = BigDecimal(Double.MIN_VALUE, MathContext(5))
        var indexMin = -1
        val correctIndex = correctOutput[0].round(MathContext(1)).toInt()
        for (i in 0 until output.size) {
            if (output[i].active > min) {
                min = output[i].active
                indexMin = i
            }
            val c = if (correctIndex == i) n1 else n0
            var err = c - output[i].active
            if (c < n0) {
                err = min(err, BigDecimal.ZERO)
            } else if (c > n1) {
                err = max(err, BigDecimal.ZERO)
            }
            error.add(err)
        }

        if (correctIndex == indexMin) {
            Array<BigDecimal>(output.size) {
                BigDecimal.ZERO
            }.toCollection(ArrayList())
        } else {
            error
        }
    }
    val res = bigStudy(nn, tests, arrayListOf(), BigDecimal(10), BigDecimal.ZERO)
    for (test in res.third) {
        val calc = nn.calc(test.first)
        var minValue = BigDecimal(Double.MIN_VALUE, MathContext(5))
        var minIndex = -1
        for (i in calc.indices) {
            if (calc[i].active > minValue) {
                minValue = calc[i].active
                minIndex = i
            }
        }
        println("${test.second} $minIndex")
    }
    println("i = ${res.first}")
    println(Arrays.deepToString(nn.weights.toArray()))
}

fun min(a: BigDecimal, b: BigDecimal): BigDecimal = if (a > b) b else a

fun max(a: BigDecimal, b: BigDecimal): BigDecimal = if (a > b) a else b

operator fun BigDecimal.plus(other: Int) = this + BigDecimal(other)
operator fun BigDecimal.minus(other: Int) = this - BigDecimal(other)

fun readTests(): Tests {
    val postfixes = IntRange(0, 9).map { "_$it" }.toCollection(ArrayList())
    postfixes.add(0, "")
    val t: Tests = arrayListOf()
    for (i in 0..9) {
        for (p in postfixes) {
            val fileName = "digits/${i}$p.bmp"
            val r = readBmpDigit(fileName)
            printDigit(r)
            t.add(Pair(r, arrayListOf(BigDecimal(i))))
        }
    }
    return t
}

fun readBmpDigit(filename: String): ArrayList<BigDecimal> {
    val fis = FileInputStream(File(filename))

    val reader = fis.bufferedReader()
    val content = reader.readText().toCharArray()


    val res = Array(24) { BigDecimal.ZERO }.toCollection(ArrayList())
    var index = 0
    for (i in 5 downTo 0) {
        for (j in 0 until 4) {
            val num = content[content.size - 24 + index++]
//            print("$i $j ${num.code}\n")
            val ind = i * 4 + j
            res[ind] = if (num < (128).toChar()) BigDecimal(0.2) else BigDecimal(0.8)
        }
    }
    println()
    return res
}

fun printDigit(tests: ArrayList<BigDecimal>) {
    for (i in 0 until 6) {
        for (j in 0 until 4) {
            print(if (tests[i * 4 + j] > BigDecimal(0.5)) '#' else ".")
        }
        println()
    }
    println()
}

fun main() {
//    testXor()

    testDigits(readTests())
}
