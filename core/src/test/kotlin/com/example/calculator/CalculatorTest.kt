package com.example.calculator

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CalculatorTest {
    private fun f(s: String, mode: AngleMode = AngleMode.DEG) = ResultFormatter.format(Evaluator.evaluate(s, mode))
    @Test fun rationalArithmetic() {
        assertEquals("0.3", f("0.1+0.2")); assertEquals("1/2", f("1/3+1/6"))
        assertEquals("100", f("200*sin(30)")); assertEquals("2", f("4*cos(60)"))
        assertEquals("2", f("1/sin(30)")); assertEquals("1", f("sin(30)+cos(60)"))
        assertEquals("-2", f("-5+3")); assertEquals("2", f("5-3")); assertEquals("-3", f("-(-(-3))"))
        assertEquals("2", f("2π/π")); assertEquals("999999999998000000000001", f("999999999999*999999999999"))
    }
    @Test fun trigDegSpecialAngles() {
        assertEquals("1/2", f("sin(30)")); assertEquals("1/2", f("cos(60)")); assertEquals("1", f("sin(90)"))
        assertEquals("1", f("tan(45)")); assertEquals("√2/2", f("sin(45)")); assertEquals("√3/2", f("cos(30)"))
        assertEquals("√3/3", f("tan(30)")); assertEquals("√3", f("tan(60)")); assertEquals("1/2", f("sin(390)"))
        assertEquals("1", f("cos(720)")); assertEquals("-1/2", f("sin(-30)"))
        assertEquals("√3/2", f("sin(60)")); assertEquals("-√3/2", f("cos(150)")); assertEquals("-√3", f("tan(120)"))
        assertEquals("-1/2", f("sin(210)")); assertEquals("√3", f("tan(240)")); assertEquals("-√3", f("tan(300)"))
        assertEquals("√2/2", f("cos(315)")); assertEquals("1", f("tan(225)")); assertEquals("√3/2", f("cos(330)"))
    }
    @Test fun trigRadAndInverse() {
        assertEquals("1/2", f("sin(π/6)"))
        assertEquals("1/2", f("sin(π/6)", AngleMode.RAD)); assertEquals("1/2", f("cos(π/3)", AngleMode.RAD))
        assertEquals("1", f("sin(π/2)", AngleMode.RAD)); assertEquals("100", f("200*sin(π/6)", AngleMode.RAD))
        assertEquals("30", f("asin(1/2)")); assertEquals("π/6", f("asin(1/2)", AngleMode.RAD))
        assertEquals("45", f("atan(1)")); assertEquals("π/4", f("atan(1)", AngleMode.RAD))
        assertEquals("30", f("arcsin(1/2)")); assertEquals("60", f("arccos(1/2)"))
        assertEquals("45", f("arctan(1)")); assertEquals("π/6", f("arcsin(1/2)", AngleMode.RAD))
    }
    @Test fun functionArgumentsCanOmitParentheses() {
        assertEquals("√3/2", f("sin60"))
        assertEquals("√3/2", f("sin(60"))
        assertEquals("10.8660254", f("sin 60+10"))
        assertEquals("10.8660254", f("sin(60+10"))
        assertEquals("0.9396926208", f("sin(60+10)"))
    }
    @Test fun degreeMinuteSecondAngles() {
        assertEquals("1/2", f("sin(30°)"))
        assertEquals("1/2", f("sin(30°0′0″)"))
        assertEquals("0.5045276238", ResultFormatter.formatDecimal(Evaluator.evaluate("sin(30°18′)")))
        assertEquals("1/2", f("sin(30°0'0\" )"))
    }
    @Test fun radicalsAndPowers() {
        assertEquals("2√2", f("sqrt(8)")); assertEquals("2", f("sqrt(2)*sqrt(2)")); assertEquals("√2", f("2*sqrt(2)/2"))
        assertEquals("8", f("2^3")); assertEquals("120", f("5!")); assertEquals("0.3", f("0.1+0.2"))
        assertEquals("1/2", f("50%"))
    }
    @Test fun errors() {
        assertFailsWith<CalcError> { Evaluator.evaluate("1/0") }; assertFailsWith<CalcError> { Evaluator.evaluate("tan(90)") }
        assertFailsWith<CalcError> { Evaluator.evaluate("asin(2)") }; assertFailsWith<CalcError> { Evaluator.evaluate("sqrt(-1)") }
        assertFailsWith<CalcError> { Evaluator.evaluate("1001!") }
        assertFailsWith<CalcError> { Evaluator.evaluate("(-1)!") }; assertFailsWith<CalcError> { Evaluator.evaluate("1+") }
    }
    @Test fun formatters() {
        assertEquals("100", ResultFormatter.format(Exact.Rational(BigInteger.valueOf(100), BigInteger.ONE)))
        assertEquals("1/2", ResultFormatter.format(Exact.Rational(BigInteger.ONE, BigInteger.TWO)))
        assertEquals("√2/2", ResultFormatter.format(Exact.Surd(Exact.Rational(BigInteger.ONE, BigInteger.TWO), 2)))
        assertEquals("50√3", ResultFormatter.format(Evaluator.evaluate("100*sin(60)")))
        assertEquals("86.60254038", ResultFormatter.formatDecimal(Evaluator.evaluate("100*sin(60)")))
    }
}
