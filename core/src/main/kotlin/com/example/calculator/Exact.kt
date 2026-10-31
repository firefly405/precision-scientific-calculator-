package com.example.calculator

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.sqrt

sealed class Exact {
    data class Rational(val num: BigInteger, val den: BigInteger = BigInteger.ONE) : Exact() {
        init { require(den != BigInteger.ZERO) }
        companion object {
            fun of(n: Long) = Rational(BigInteger.valueOf(n), BigInteger.ONE)
            fun of(text: String): Rational {
                val parts = text.split('.')
                if (parts.size == 1) return of(parts[0].toLong())
                val scale = BigInteger.TEN.pow(parts[1].length)
                val sign = if (parts[0].startsWith("-")) -1 else 1
                val whole = parts[0].removePrefix("-").ifEmpty { "0" }
                val n = BigInteger(whole) * scale + BigInteger(parts[1])
                return Rational(n * BigInteger.valueOf(sign.toLong()), scale)
            }
        }
        val reduced: Rational
            get() {
                var n = num
                var d = den
                if (d.signum() < 0) { n = n.negate(); d = d.negate() }
                val g = n.gcd(d)
                return Rational(n.divide(g), d.divide(g), true)
            }
        private constructor(n: BigInteger, d: BigInteger, @Suppress("UNUSED_PARAMETER") reduced: Boolean) : this(n, d)
        operator fun unaryMinus() = Rational(num.negate(), den)
    }

    data class Surd(val coef: Rational, val radicand: Int) : Exact() {
        init { require(radicand > 0) }
    }

    data class PiMultiple(val coef: Rational) : Exact()
    data class Numeric(val value: Double) : Exact()
}

object ExactOps {
    private fun rat(v: Long) = Exact.Rational.of(v)
    fun add(a: Exact, b: Exact): Exact = when {
        a is Exact.Rational && b is Exact.Rational -> Exact.Rational(a.num * b.den + b.num * a.den, a.den * b.den).reduced
        a is Exact.Surd && b is Exact.Surd && a.radicand == b.radicand -> surd(a.coef + b.coef, a.radicand)
        a is Exact.PiMultiple && b is Exact.PiMultiple -> Exact.PiMultiple(a.coef + b.coef)
        a is Exact.Rational && b is Exact.Surd -> Exact.Numeric(toDouble(a) + toDouble(b))
        a is Exact.Surd && b is Exact.Rational -> addSurdRational(a, b)
        a is Exact.PiMultiple && b is Exact.Rational -> Exact.Numeric(toDouble(a) + toDouble(b))
        a is Exact.Rational && b is Exact.PiMultiple -> Exact.Numeric(toDouble(a) + toDouble(b))
        else -> Exact.Numeric(toDouble(a) + toDouble(b))
    }
    fun sub(a: Exact, b: Exact) = add(a, negate(b))
    fun mul(a: Exact, b: Exact): Exact = when {
        a is Exact.Rational && b is Exact.Rational -> Exact.Rational(a.num * b.num, a.den * b.den).reduced
        a is Exact.Rational && b is Exact.Surd -> surd(a * b.coef, b.radicand)
        a is Exact.Surd && b is Exact.Rational -> surd(a.coef * b, a.radicand)
        a is Exact.Surd && b is Exact.Surd -> {
            val product = a.radicand.toLong() * b.radicand.toLong()
            val (square, free) = factorSquare(product)
            val c = a.coef * b.coef * rat(square)
            if (free == 1) c else surd(c, free)
        }
        a is Exact.PiMultiple && b is Exact.Rational -> Exact.PiMultiple(a.coef * b)
        a is Exact.Rational && b is Exact.PiMultiple -> Exact.PiMultiple(b.coef * a)
        else -> Exact.Numeric(toDouble(a) * toDouble(b))
    }
    fun div(a: Exact, b: Exact): Exact {
        if (isZero(b)) throw CalcError.DivisionByZero
        return when {
            a is Exact.Rational && b is Exact.Rational -> Exact.Rational(a.num * b.den, a.den * b.num).reduced
            a is Exact.Surd && b is Exact.Rational -> surd(a.coef / b, a.radicand)
            a is Exact.Rational && b is Exact.Surd -> {
                val (square, free) = factorSquare(b.radicand.toLong())
                val c = a * b.coef / rat(square * free.toLong())
                if (free == 1) c else surd(c, free)
            }
            a is Exact.PiMultiple && b is Exact.Rational -> Exact.PiMultiple(a.coef / b)
            else -> Exact.Numeric(toDouble(a) / toDouble(b))
        }
    }
    fun negate(a: Exact): Exact = when (a) {
        is Exact.Rational -> -a
        is Exact.Surd -> surd(-a.coef, a.radicand)
        is Exact.PiMultiple -> Exact.PiMultiple(-a.coef)
        is Exact.Numeric -> Exact.Numeric(-a.value)
    }
    fun isZero(a: Exact) = when (a) {
        is Exact.Rational -> a.num.signum() == 0
        is Exact.Surd -> a.coef.num.signum() == 0
        is Exact.PiMultiple -> a.coef.num.signum() == 0
        is Exact.Numeric -> abs(a.value) < 1e-12
    }
    fun toDouble(a: Exact): Double = when (a) {
        is Exact.Rational -> a.num.toDouble() / a.den.toDouble()
        is Exact.Surd -> a.coef.num.toDouble() / a.coef.den.toDouble() * sqrt(a.radicand.toDouble())
        is Exact.PiMultiple -> a.coef.num.toDouble() / a.coef.den.toDouble() * Math.PI
        is Exact.Numeric -> a.value
    }
    private fun zero() = rat(0)
    private fun addSurdRational(s: Exact.Surd, r: Exact.Rational): Exact = Exact.Numeric(toDouble(s) + toDouble(r))
    private fun surd(c: Exact.Rational, rad: Int): Exact {
        if (c.num.signum() == 0) return rat(0)
        val (square, free) = factorSquare(rad.toLong())
        val coefficient = c * rat(square)
        return if (free == 1) coefficient else Exact.Surd(coefficient, free)
    }
    private fun factorSquare(value: Long): Pair<Long, Int> {
        var n = value; var square = 1L; var free = 1L; var p = 2L
        while (p * p <= n) {
            var count = 0
            while (n % p == 0L) { n /= p; count++ }
            repeat(count / 2) { square *= p }
            if (count % 2 == 1) free *= p
            p++
        }
        if (n > 1) free *= n
        return square to free.toInt()
    }
    private operator fun Exact.Rational.plus(o: Exact.Rational) = Exact.Rational(num * o.den + o.num * den, den * o.den).reduced
    private operator fun Exact.Rational.times(o: Exact.Rational) = Exact.Rational(num * o.num, den * o.den).reduced
    private operator fun Exact.Rational.div(o: Exact.Rational) = Exact.Rational(num * o.den, den * o.num).reduced
}

sealed class CalcError(message: String) : IllegalArgumentException(message) {
    data object DivisionByZero : CalcError("除数不能为零")
    data object Undefined : CalcError("未定义")
    data object Domain : CalcError("超出定义域")
    data object NegativeSqrt : CalcError("负数不能开平方")
    data object BadExpression : CalcError("表达式有误")
    data object Factorial : CalcError("阶乘仅支持非负整数")
    data object Overflow : CalcError("数值过大")
}
