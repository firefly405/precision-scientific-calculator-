package com.example.calculator

import java.math.BigInteger
import java.util.Locale
import kotlin.math.*

enum class AngleMode { DEG, RAD }

private enum class Kind { NUMBER, IDENT, OP, LPAREN, RPAREN, COMMA, END }
private data class Token(val kind: Kind, val text: String)

private val functionNames = setOf(
    "sin", "cos", "tan", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
    "sqrt", "ln", "log", "abs"
)

private object Tokenizer {
    fun tokenize(input: String): List<Token> {
        val out = mutableListOf<Token>(); var i = 0
        fun add(k: Kind, s: String) { out += Token(k, s) }
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i; var dots = 0
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) { if (input[i] == '.') dots++; i++ }
                    if (dots > 1) throw CalcError.BadExpression
                    add(Kind.NUMBER, input.substring(start, i))
                }
                c.isLetter() || c == 'π' -> {
                    val start = i; i++
                    while (i < input.length && (input[i].isLetter() || input[i] == 'π')) i++
                    add(Kind.IDENT, input.substring(start, i))
                }
                c == '(' -> { add(Kind.LPAREN, "("); i++ }
                c == ')' -> { add(Kind.RPAREN, ")"); i++ }
                c == ',' -> { add(Kind.COMMA, ","); i++ }
                c == '×' -> { add(Kind.OP, "*"); i++ }
                c == '÷' -> { add(Kind.OP, "/"); i++ }
                c == '√' -> { add(Kind.IDENT, "sqrt"); i++ }
                c == '^' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '!' || c == '°' || c == '′' || c == '\'' || c == '″' || c == '"' -> {
                    val symbol = when (c) { '\'' -> '′'; '"' -> '″'; else -> c }
                    add(Kind.OP, symbol.toString()); i++
                }
                else -> throw CalcError.BadExpression
            }
        }
        add(Kind.END, "")
        return insertImplicitMultiplication(out)
    }
    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        fun endValue(t: Token) = t.kind == Kind.NUMBER || t.kind == Kind.IDENT || t.kind == Kind.RPAREN || (t.kind == Kind.OP && t.text == "!")
        fun startValue(t: Token) = t.kind == Kind.NUMBER || t.kind == Kind.IDENT || t.kind == Kind.LPAREN
        for (i in tokens.indices) {
            val t = tokens[i]
            val callOpen = result.lastOrNull()?.kind == Kind.IDENT && t.kind == Kind.LPAREN
            val implicitCall = result.lastOrNull()?.kind == Kind.IDENT && result.last().text.lowercase() in functionNames && t.kind != Kind.LPAREN
            if (result.isNotEmpty() && endValue(result.last()) && startValue(t) && !callOpen && !implicitCall && result.last().text != "sqrt") result += Token(Kind.OP, "*")
            result += t
        }
        return result
    }
}

private sealed interface Node {
    data class Number(val value: Exact.Rational) : Node
    data class Name(val value: String) : Node
    data class Unary(val op: String, val child: Node) : Node
    data class Binary(val op: String, val left: Node, val right: Node) : Node
    data class Call(val name: String, val arg: Node) : Node
    data class Postfix(val op: String, val child: Node) : Node
}

private class Parser(private val tokens: List<Token>) {
    private var pos = 0
    private fun current() = tokens[pos]
    private fun take(): Token = tokens[pos++]
    fun parse(): Node { val n = expr(0); if (current().kind != Kind.END) throw CalcError.BadExpression; return n }
    private fun expr(minPrec: Int): Node {
        var left = prefix()
        while (true) {
            val op = current().text
            val prec = when (op) { "+", "-" -> 10; "*", "/" -> 20; "^" -> 30; else -> -1 }
            if (prec >= minPrec) {
                take()
                val right = expr(if (op == "^") prec else prec + 1)
                left = Node.Binary(op, left, right)
                continue
            }
            if (minPrec <= 30 && containsAngleUnit(left) && startsDmsPart()) {
                left = Node.Binary("+", left, expr(31))
                continue
            }
            break
        }
        return left
    }
    private fun prefix(): Node {
        val t = take()
        return when (t.kind) {
            Kind.NUMBER -> Node.Number(Exact.Rational.of(t.text))
            Kind.IDENT -> {
                val name = t.text.lowercase()
                if (current().kind == Kind.LPAREN) {
                    take()
                    val hasClosingParen = hasMatchingParen()
                    val arg = if (hasClosingParen) expr(0) else expr(25)
                    if (current().kind == Kind.RPAREN) take() else if (hasClosingParen) throw CalcError.BadExpression
                    Node.Call(name, arg)
                } else if (name in functionNames && canStartValue(current())) {
                    Node.Call(name, expr(25))
                } else Node.Name(name)
            }
            Kind.OP -> when (t.text) { "+", "-" -> Node.Unary(t.text, expr(25)); else -> throw CalcError.BadExpression }
            Kind.LPAREN -> { val n = expr(0); if (take().kind != Kind.RPAREN) throw CalcError.BadExpression; n }
            else -> throw CalcError.BadExpression
        }.let { base ->
            var n = base
            while (current().kind == Kind.OP && (current().text == "!" || current().text == "%" || isAngleUnit(current().text))) {
                val op = take().text
                n = Node.Postfix(op, n)
            }
            n
        }
    }

    private fun canStartValue(token: Token) = token.kind == Kind.NUMBER || token.kind == Kind.IDENT || token.kind == Kind.LPAREN || (token.kind == Kind.OP && token.text in listOf("+", "-"))

    private fun isAngleUnit(value: String) = value == "°" || value == "′" || value == "″"

    private fun startsDmsPart() = canStartValue(current()) && pos + 1 < tokens.size && tokens[pos + 1].kind == Kind.OP && isAngleUnit(tokens[pos + 1].text)

    private fun containsAngleUnit(node: Node): Boolean = when (node) {
        is Node.Postfix -> isAngleUnit(node.op) || containsAngleUnit(node.child)
        is Node.Binary -> containsAngleUnit(node.left) || containsAngleUnit(node.right)
        is Node.Unary -> containsAngleUnit(node.child)
        else -> false
    }

    private fun hasMatchingParen(): Boolean {
        var depth = 1
        var index = pos
        while (index < tokens.size) {
            when (tokens[index].kind) {
                Kind.LPAREN -> depth++
                Kind.RPAREN -> {
                    depth--
                    if (depth == 0) return true
                }
                else -> Unit
            }
            index++
        }
        return false
    }
}

object Evaluator {
    fun evaluate(expression: String, mode: AngleMode = AngleMode.DEG): Exact = evaluateNode(Parser(Tokenizer.tokenize(expression)).parse(), mode)
    private fun evaluateNode(n: Node, mode: AngleMode): Exact = when (n) {
        is Node.Number -> n.value
        is Node.Name -> when (n.value) {
            "pi", "π" -> Exact.PiMultiple(Exact.Rational.of(1))
            "e" -> Exact.Numeric(Math.E)
            else -> throw CalcError.BadExpression
        }
        is Node.Unary -> if (n.op == "-") ExactOps.negate(evaluateNode(n.child, mode)) else evaluateNode(n.child, mode)
        is Node.Binary -> {
            val a = evaluateNode(n.left, mode); val b = evaluateNode(n.right, mode)
            when (n.op) { "+" -> ExactOps.add(a, b); "-" -> ExactOps.sub(a, b); "*" -> ExactOps.mul(a, b); "/" -> ExactOps.div(a, b); "%" -> ExactOps.div(ExactOps.mul(a, Exact.Rational.of(1)), Exact.Rational.of(100)).let { ExactOps.mul(a, b) } ; "^" -> pow(a, b); else -> throw CalcError.BadExpression }
        }
        is Node.Call -> call(n.name, evaluateNode(n.arg, mode), mode)
        is Node.Postfix -> when (n.op) {
            "!" -> factorial(evaluateNode(n.child, mode))
            "%" -> ExactOps.div(evaluateNode(n.child, mode), Exact.Rational.of(100))
            else -> angleUnit(evaluateNode(n.child, mode), n.op)
        }
    }
    private fun angleUnit(value: Exact, unit: String): Exact {
        val denominator = when (unit) { "°" -> 180L; "′" -> 10800L; else -> 648000L }
        return if (value is Exact.Rational) {
            Exact.PiMultiple(Exact.Rational(value.num, value.den * BigInteger.valueOf(denominator)).reduced)
        } else {
            Exact.Numeric(ExactOps.toDouble(value) * Math.PI / denominator)
        }
    }
    private fun pow(a: Exact, b: Exact): Exact {
        if (b is Exact.Rational && b.den == BigInteger.ONE) {
            val e = b.num.toInt()
            if (e in -20..20) { var result: Exact = Exact.Rational.of(1); repeat(abs(e)) { result = ExactOps.mul(result, a) }; return if (e < 0) ExactOps.div(Exact.Rational.of(1), result) else result }
        }
        return Exact.Numeric(ExactOps.toDouble(a).pow(ExactOps.toDouble(b)))
    }
    private fun factorial(a: Exact): Exact {
        if (a !is Exact.Rational || a.den != BigInteger.ONE || a.num.signum() < 0 || a.num > BigInteger.valueOf(1000)) throw CalcError.Factorial
        var r = BigInteger.ONE; var i = BigInteger.ONE
        while (i <= a.num) { r *= i; i += BigInteger.ONE }
        return Exact.Rational(r, BigInteger.ONE).reduced
    }
    private fun call(name: String, x: Exact, mode: AngleMode): Exact {
        val n = ExactOps.toDouble(x)
        return when (name) {
            "sqrt" -> if (ExactOps.isZero(x) || n >= 0) sqrtExact(x) else throw CalcError.NegativeSqrt
            "sin", "cos", "tan" -> trig(name, x, mode)
            "asin", "arcsin", "acos", "arccos", "atan", "arctan" -> inverseTrig(
                when (name) {
                    "arcsin" -> "asin"
                    "arccos" -> "acos"
                    "arctan" -> "atan"
                    else -> name
                }, x, mode
            )
            "ln" -> Exact.Numeric(ln(n))
            "log" -> Exact.Numeric(log10(n))
            "abs" -> Exact.Numeric(abs(n))
            else -> throw CalcError.BadExpression
        }
    }
    private fun sqrtExact(x: Exact): Exact {
        if (x is Exact.Rational && x.num.signum() >= 0) {
            val num = x.num.toLong(); val den = x.den.toLong()
            val sn = sqrt(num.toDouble()).roundToLong(); val sd = sqrt(den.toDouble()).roundToLong()
            if (sn * sn == num && sd * sd == den) return ExactOps.div(Exact.Rational.of(sn), Exact.Rational.of(sd))
            if (den == 1L && num <= Int.MAX_VALUE) {
                var n = num; var outside = 1L; var p = 2L
                while (p * p <= n) { while (n % (p * p) == 0L) { n /= p * p; outside *= p }; p++ }
                val coef = Exact.Rational.of(outside)
                return if (n == 1L) coef else Exact.Surd(coef, n.toInt())
            }
        }
        return Exact.Numeric(sqrt(ExactOps.toDouble(x)))
    }
    private fun trig(name: String, x: Exact, mode: AngleMode): Exact {
        // Pi multiples are canonical radian input, even if the user has not toggled the mode first.
        val degrees = if (mode == AngleMode.RAD || x is Exact.PiMultiple) Math.toDegrees(ExactOps.toDouble(x)) else ExactOps.toDouble(x)
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        val table = TrigTable.lookup(name, normalized)
        if (table != null) return table
        if (name == "tan" && abs(cos(Math.toRadians(normalized))) < 1e-12) throw CalcError.Undefined
        val r = Math.toRadians(degrees)
        return Exact.Numeric(when (name) { "sin" -> sin(r); "cos" -> cos(r); else -> tan(r) })
    }
    private fun inverseTrig(name: String, x: Exact, mode: AngleMode): Exact {
        val n = ExactOps.toDouble(x); if (name != "atan" && (n < -1.0 || n > 1.0)) throw CalcError.Domain
        val exact = TrigTable.inverse(name, x, mode); if (exact != null) return exact
        val radians = when (name) { "asin" -> asin(n); "acos" -> acos(n); else -> atan(n) }
        return if (mode == AngleMode.DEG) Exact.Numeric(Math.toDegrees(radians)) else {
            val scaled = (radians / Math.PI * 1000000).roundToLong()
            Exact.PiMultiple(Exact.Rational(BigInteger.valueOf(scaled), BigInteger.valueOf(1000000)).reduced)
        }
    }
}

object TrigTable {
    private val values = mapOf(
        0 to Triple(Exact.Rational.of(0), Exact.Rational.of(1), Exact.Rational.of(0)),
        30 to Triple(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, Exact.Surd(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, 3), Exact.Surd(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, 3)),
        45 to Triple(Exact.Surd(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, 2), Exact.Surd(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, 2), Exact.Rational.of(1)),
        60 to Triple(Exact.Surd(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, 3), Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, Exact.Surd(Exact.Rational.of(1).let { Exact.Rational(it.num, BigInteger.TWO) }, 3)),
        90 to Triple(Exact.Rational.of(1), Exact.Rational.of(0), Exact.Numeric(Double.NaN))
    )
    fun lookup(name: String, degree: Double): Exact? {
        val d = degree.roundToInt(); if (abs(degree - d) > 1e-9) return null
        return when ((d % 360 + 360) % 360) {
            0 -> when (name) { "sin" -> Exact.Rational.of(0); "cos" -> Exact.Rational.of(1); else -> Exact.Rational.of(0) }
            30 -> when (name) { "sin" -> Exact.Rational(BigInteger.ONE, BigInteger.TWO); "cos" -> Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.TWO), 3); else -> Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.valueOf(3)), 3) }
            45 -> when (name) { "sin", "cos" -> Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.TWO), 2); else -> Exact.Rational.of(1) }
            60 -> when (name) { "sin" -> Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.TWO), 3); "cos" -> Exact.Rational(BigInteger.ONE, BigInteger.TWO); else -> Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.ONE), 3) }
            90 -> when (name) { "sin" -> Exact.Rational.of(1); "cos" -> Exact.Rational.of(0); else -> throw CalcError.Undefined }
            120 -> lookup(name, 60.0)?.let { if (name == "sin") it else ExactOps.negate(it) }
            135 -> lookup(name, 45.0)?.let { if (name == "sin") it else if (name == "cos") ExactOps.negate(it) else Exact.Rational.of(-1) }
            150 -> lookup(name, 30.0)?.let { if (name == "sin") it else ExactOps.negate(it) }
            180 -> when (name) { "sin", "tan" -> Exact.Rational.of(0); else -> Exact.Rational.of(-1) }
            210 -> lookup(name, 30.0)?.let { if (name == "sin") ExactOps.negate(it) else if (name == "cos") ExactOps.negate(it) else it }
            225 -> lookup(name, 45.0)?.let { if (name == "sin" || name == "cos") ExactOps.negate(it) else Exact.Rational.of(1) }
            240 -> lookup(name, 60.0)?.let { if (name == "sin" || name == "cos") ExactOps.negate(it) else Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.ONE), 3) }
            270 -> when (name) { "sin" -> Exact.Rational.of(-1); "cos" -> Exact.Rational.of(0); else -> throw CalcError.Undefined }
            300 -> lookup(name, 60.0)?.let { if (name == "cos") it else ExactOps.negate(it) }
            315 -> lookup(name, 45.0)?.let { if (name == "sin") ExactOps.negate(it) else if (name == "cos") it else Exact.Rational.of(-1) }
            330 -> lookup(name, 30.0)?.let { if (name == "cos") it else ExactOps.negate(it) }
            else -> null
        }
    }
    fun inverse(name: String, x: Exact, mode: AngleMode): Exact? {
        if (name == "atan" && same(Exact.Rational.of(1), x)) {
            return if (mode == AngleMode.DEG) Exact.Rational.of(45) else Exact.PiMultiple(Exact.Rational(BigInteger.ONE, BigInteger.valueOf(4)))
        }
        val candidates = listOf(0 to Exact.Rational.of(0), 30 to Exact.Rational(BigInteger.ONE, BigInteger.TWO), 45 to Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.TWO), 2), 60 to Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.TWO), 3), 90 to Exact.Rational.of(1))
        for ((d, value) in candidates) if (same(value, x)) {
            val degree = when (name) { "acos" -> 90 - d; else -> d }
            return if (mode == AngleMode.DEG) Exact.Rational.of(degree.toLong()) else Exact.PiMultiple(Exact.Rational(BigInteger.valueOf(degree.toLong()), BigInteger.valueOf(180)))
        }
        return null
    }
    private fun same(a: Exact, b: Exact) = abs(ExactOps.toDouble(a) - ExactOps.toDouble(b)) < 1e-10
}

object ResultFormatter {
    fun format(value: Exact): String = when (value) {
        is Exact.Rational -> formatRat(value)
        is Exact.Surd -> {
            val c = value.coef.reduced
            when {
                c == Exact.Rational.of(1) -> "√${value.radicand}"
                c == Exact.Rational.of(-1) -> "-√${value.radicand}"
                c.den != BigInteger.ONE && c.num.abs() == BigInteger.ONE -> "${if (c.num.signum() < 0) "-" else ""}√${value.radicand}/${c.den}"
                else -> "${formatRat(c)}√${value.radicand}"
            }
        }
        is Exact.PiMultiple -> {
            val c = value.coef.reduced
            when {
                c == Exact.Rational.of(1) -> "π"
                c.num == BigInteger.ONE -> "π/${c.den}"
                else -> "${formatRat(c)}π"
            }
        }
        is Exact.Numeric -> if (!value.value.isFinite()) "未定义" else "%.10g".format(value.value).trimEnd('0').trimEnd('.')
    }
    fun formatDecimal(value: Exact): String {
        val number = ExactOps.toDouble(value)
        if (!number.isFinite()) return "未定义"
        val raw = String.format(Locale.US, "%.10g", number)
        return if (raw.contains('e', ignoreCase = true)) raw else raw.trimEnd('0').trimEnd('.')
    }
    private fun formatRat(r: Exact.Rational): String {
        val q = r.reduced; if (q.den == BigInteger.ONE) return q.num.toString()
        if (q.den == BigInteger.TEN) return "${q.num / BigInteger.TEN}.${q.num.abs().mod(BigInteger.TEN)}"
        return "${q.num}/${q.den}"
    }
}
