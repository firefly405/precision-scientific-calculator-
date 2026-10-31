package com.example.precisioncalculator

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.content.Context
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.calculator.*
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 18, 19)
        window.navigationBarColor = Color.rgb(12, 18, 19)
        val calculator = CalculatorView(this)
        calculator.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                calculator.setSystemInsets(bars.top, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                calculator.setSystemInsets(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(calculator)
        calculator.requestApplyInsets()
    }
}

private class CalculatorView(context: Context) : View(context) {
    private var bg = Color.rgb(12, 18, 19)
    private var panel = Color.rgb(24, 33, 34)
    private var panelBorder = Color.rgb(49, 68, 69)
    private var key = Color.rgb(32, 46, 47)
    private var keyPressed = Color.rgb(53, 74, 75)
    private var operator = Color.rgb(41, 62, 64)
    private var accent = Color.rgb(116, 214, 202)
    private var accentPressed = Color.rgb(145, 232, 219)
    private val text = Color.rgb(239, 247, 245)
    private var muted = Color.rgb(148, 169, 168)
    private val errorText = Color.rgb(255, 145, 145)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private var expression = ""
    private var result = "0"
    private var error = ""
    private var mode = AngleMode.DEG
    private var inverse = false
    private var justEvaluated = false
    private var radicalDisplay = true
    private var exactResult: Exact? = null
    private var insetTop = 0f
    private var insetBottom = 0f
    private var pressedRow = -1
    private var pressedCol = -1
    private var settingsOpen = false
    private val preferences = context.getSharedPreferences("calculator_settings", Context.MODE_PRIVATE)
    private val swatches = intArrayOf(
        Color.rgb(116, 214, 202), Color.rgb(117, 169, 255), Color.rgb(244, 170, 94),
        Color.rgb(224, 128, 184), Color.rgb(158, 198, 112), Color.rgb(177, 153, 255)
    )
    private val keys = arrayOf(
        arrayOf("清空", "⌫", "角度", "弧度"),
        arrayOf("度", "分", "秒", "角度"),
        arrayOf("sin", "cos", "tan", "√"),
        arrayOf("2nd", "x²", "xʸ", "π"),
        arrayOf("ln", "log", "1/x", "n!"),
        arrayOf("(", ")", "%", "÷"),
        arrayOf("7", "8", "9", "×"),
        arrayOf("4", "5", "6", "−"),
        arrayOf("1", "2", "3", "+"),
        arrayOf("±", "0", ".", "=")
    )
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        background = ColorDrawable(bg)
        paint.strokeWidth = dp(1f)
        setAccentColor(preferences.getInt("accent", accent), persist = false)
    }

    fun setSystemInsets(top: Int, bottom: Int) {
        insetTop = top.toFloat()
        insetBottom = bottom.toFloat()
        invalidate()
    }

    private fun dp(value: Float) = value * density
    private fun sp(value: Float) = value * scaledDensity
    private fun cardBottom() = insetTop + dp(174f)
    private fun gridTop() = insetTop + dp(214f)
    private fun radicalToggleRect() = RectF(width - dp(122f), insetTop + dp(20f), width - dp(32f), insetTop + dp(46f))
    private fun settingsButtonRect() = RectF(width / 2f - dp(40f), cardBottom() + dp(5f), width / 2f + dp(40f), cardBottom() + dp(35f))
    private fun customColorRect() = RectF(dp(32f), insetTop + dp(290f), width - dp(32f), insetTop + dp(344f))
    private fun swatchRect(index: Int): RectF {
        val size = dp(56f); val gap = dp(18f)
        val col = index % 3; val row = index / 3
        val x = dp(44f) + col * (size + gap)
        val y = insetTop + dp(128f) + row * (size + dp(28f))
        return RectF(x, y, x + size, y + size)
    }
    private fun gridMetrics(): Triple<Float, Float, Float> {
        val gap = dp(8f)
        val gridTop = gridTop()
        val cellWidth = (width - gap * 5f) / 4f
        val available = height - insetBottom - gridTop - dp(16f)
        val rows = keys.size
        val cellHeight = minOf(dp(58f), (available - gap * (rows - 1)) / rows)
        return Triple(gap, cellWidth, cellHeight)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.drawColor(bg)
        if (settingsOpen) {
            drawSettings(c)
            return
        }
        val w = width.toFloat()
        val left = dp(16f)
        val right = w - dp(16f)
        val top = insetTop + dp(14f)
        val displayBottom = top + dp(160f)

        paint.style = Paint.Style.FILL
        paint.color = panel
        c.drawRoundRect(left, top, right, displayBottom, dp(20f), dp(20f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = panelBorder
        c.drawRoundRect(left, top, right, displayBottom, dp(20f), dp(20f), paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create("sans", Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = sp(13f)
        paint.color = accent
        c.drawText("精确科学计算器", left + dp(18f), top + dp(25f), paint)
        paint.typeface = Typeface.create("sans", Typeface.NORMAL)

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = sp(16f)
        paint.color = muted
        val shownExpression = if (expression.isEmpty()) "请输入表达式" else displayExpression(expression)
        c.drawText(fitText(shownExpression, right - dp(18f), sp(16f)), right - dp(18f), top + dp(58f), paint)
        paint.textSize = if (error.isEmpty()) sp(42f) else sp(21f)
        paint.color = if (error.isEmpty()) text else errorText
        c.drawText(fitText(if (error.isEmpty()) result else error, right - left - dp(36f), paint.textSize), right - dp(18f), top + dp(111f), paint)

        val toggle = radicalToggleRect()
        paint.color = if (radicalDisplay) Color.rgb(57, 92, 91) else key
        c.drawRoundRect(toggle, dp(13f), dp(13f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(12f)
        paint.color = text
        c.drawText(if (radicalDisplay) "根式" else "小数", toggle.centerX(), toggle.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)

        val stripTop = displayBottom + dp(12f)
        paint.color = key
        c.drawRoundRect(settingsButtonRect(), dp(13f), dp(13f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(12f)
        paint.color = text
        val settings = settingsButtonRect()
        c.drawText("设置", settings.centerX(), settings.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = sp(12f)
        paint.color = muted
        c.drawText(if (mode == AngleMode.DEG) "角度制" else "弧度制", left + dp(4f), stripTop + dp(15f), paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = accent
        c.drawText(if (inverse) "反函数已启用" else "标准函数", right - dp(4f), stripTop + dp(15f), paint)

        val metrics = gridMetrics()
        val gap = metrics.first
        val cellW = metrics.second
        val cellH = metrics.third
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(19f)
        keys.forEachIndexed { row, values -> values.forEachIndexed { col, label ->
            val x = gap + col * (cellW + gap)
            val y = gridTop() + row * (cellH + gap)
            val selected = row == pressedRow && col == pressedCol
            paint.color = when {
                label == "=" -> if (selected) accentPressed else accent
                label in listOf("+", "−", "×", "÷") -> if (selected) Color.rgb(61, 86, 88) else operator
                label == "角度" && mode == AngleMode.DEG || label == "弧度" && mode == AngleMode.RAD -> if (selected) accentPressed else Color.rgb(57, 92, 91)
                selected -> keyPressed
                else -> key
            }
            c.drawRoundRect(x, y, x + cellW, y + cellH, dp(15f), dp(15f), paint)
            paint.color = if (label == "=") Color.rgb(8, 38, 37) else text
            c.drawText(displayLabel(label), x + cellW / 2f, y + cellH / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        }}
    }

    private fun drawSettings(c: Canvas) {
        val left = dp(32f); val right = width - dp(32f)
        val top = insetTop + dp(18f)
        paint.style = Paint.Style.FILL
        paint.color = panel
        c.drawRoundRect(left, top, right, height - insetBottom - dp(24f), dp(20f), dp(20f), paint)
        paint.style = Paint.Style.STROKE
        paint.color = panelBorder
        c.drawRoundRect(left, top, right, height - insetBottom - dp(24f), dp(20f), dp(20f), paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans", Typeface.BOLD)
        paint.textSize = sp(22f)
        paint.color = text
        c.drawText("设置", left + dp(20f), top + dp(38f), paint)
        paint.typeface = Typeface.create("sans", Typeface.NORMAL)

        val back = RectF(right - dp(92f), top + dp(14f), right - dp(14f), top + dp(48f))
        paint.color = key
        c.drawRoundRect(back, dp(13f), dp(13f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(13f)
        paint.color = text
        c.drawText("返回", back.centerX(), back.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = sp(15f)
        paint.color = muted
        c.drawText("主题颜色", left + dp(20f), insetTop + dp(94f), paint)
        swatches.forEachIndexed { index, color ->
            val rect = swatchRect(index)
            paint.color = color
            c.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, paint)
            if (color == accent) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3f)
                paint.color = text
                c.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f + dp(5f), paint)
                paint.style = Paint.Style.FILL
            }
        }

        val custom = customColorRect()
        paint.color = key
        c.drawRoundRect(custom, dp(15f), dp(15f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(16f)
        paint.color = text
        c.drawText("自定义颜色（#RRGGBB）", custom.centerX(), custom.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun mixColor(first: Int, second: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(first) * (1f - t) + Color.red(second) * t).roundToInt(),
            (Color.green(first) * (1f - t) + Color.green(second) * t).roundToInt(),
            (Color.blue(first) * (1f - t) + Color.blue(second) * t).roundToInt()
        )
    }

    private fun setAccentColor(color: Int, persist: Boolean = true) {
        accent = color
        accentPressed = mixColor(color, Color.WHITE, 0.18f)
        bg = mixColor(color, Color.BLACK, 0.93f)
        panel = mixColor(color, Color.BLACK, 0.80f)
        panelBorder = mixColor(color, Color.BLACK, 0.52f)
        key = mixColor(color, Color.BLACK, 0.72f)
        keyPressed = mixColor(color, Color.BLACK, 0.57f)
        operator = mixColor(color, Color.BLACK, 0.62f)
        muted = mixColor(color, Color.WHITE, 0.48f)
        background = ColorDrawable(bg)
        (getContext() as? Activity)?.window?.statusBarColor = bg
        (getContext() as? Activity)?.window?.navigationBarColor = bg
        if (persist) preferences.edit().putInt("accent", color).apply()
        invalidate()
    }

    private fun showColorDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f).toInt(), dp(4f).toInt(), dp(22f).toInt(), 0)
        }
        val preview = View(context).apply { background = ColorDrawable(accent) }
        layout.addView(preview, LinearLayout.LayoutParams(-1, dp(44f).toInt()))
        val valueLabel = TextView(context).apply {
            textSize = 13f
            setTextColor(this@CalculatorView.text)
            gravity = Gravity.CENTER
        }
        layout.addView(valueLabel, LinearLayout.LayoutParams(-1, dp(32f).toInt()))
        val channels = intArrayOf(Color.red(accent), Color.green(accent), Color.blue(accent))
        val bars = arrayOf("红", "绿", "蓝").mapIndexed { index, label ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val name = TextView(context).apply { text = label; textSize = 14f; setTextColor(this@CalculatorView.text) }
            row.addView(name, LinearLayout.LayoutParams(dp(28f).toInt(), dp(42f).toInt()))
            val bar = SeekBar(context).apply { max = 255; progress = channels[index] }
            row.addView(bar, LinearLayout.LayoutParams(0, dp(42f).toInt(), 1f))
            layout.addView(row, LinearLayout.LayoutParams(-1, dp(46f).toInt()))
            bar
        }.toTypedArray()
        fun updatePreview() {
            val color = Color.rgb(bars[0].progress, bars[1].progress, bars[2].progress)
            preview.background = ColorDrawable(color)
            valueLabel.text = String.format(Locale.US, "#%06X", color and 0xFFFFFF)
        }
        bars.forEach { it.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }) }
        updatePreview()
        AlertDialog.Builder(context)
            .setTitle("自定义主题颜色")
            .setView(layout)
            .setNegativeButton("取消", null)
            .setPositiveButton("应用") { _, _ -> setAccentColor(Color.rgb(bars[0].progress, bars[1].progress, bars[2].progress)) }
            .show()
    }

    private fun fitText(value: String, maxWidth: Float, baseSize: Float): String {
        if (value.isEmpty()) return value
        paint.textSize = baseSize
        val minSize = sp(20f)
        while (paint.textSize > minSize && paint.measureText(value) > maxWidth) {
            paint.textSize -= dp(1f)
        }
        if (paint.measureText(value) <= maxWidth) return value
        var start = 0
        var candidate = value
        while (candidate.length > 1 && paint.measureText(candidate) > maxWidth) {
            start++
            candidate = "…" + value.substring(start)
        }
        return candidate
    }
    private fun displayLabel(label: String) = if (inverse) when (label) { "sin" -> "sin⁻¹"; "cos" -> "cos⁻¹"; "tan" -> "tan⁻¹"; else -> label } else label
    private fun displayExpression(value: String) = value.replace("*", "×").replace("/", "÷")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (settingsOpen) {
            if (event.action == MotionEvent.ACTION_UP) {
                val left = dp(32f); val right = width - dp(32f)
                val top = insetTop + dp(18f)
                val back = RectF(right - dp(92f), top + dp(14f), right - dp(14f), top + dp(48f))
                when {
                    back.contains(event.x, event.y) -> settingsOpen = false
                    customColorRect().contains(event.x, event.y) -> showColorDialog()
                    else -> swatches.forEachIndexed { index, color -> if (swatchRect(index).contains(event.x, event.y)) setAccentColor(color) }
                }
                invalidate()
            }
            return true
        }
        val metrics = gridMetrics()
        val gap = metrics.first
        val cellW = metrics.second
        val cellH = metrics.third
        val col = ((event.x - gap) / (cellW + gap)).toInt()
        val row = ((event.y - gridTop()) / (cellH + gap)).toInt()
        val valid = col in 0..3 && row in keys.indices && event.x >= gap && event.y >= gridTop()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (valid) { pressedRow = row; pressedCol = col; invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (settingsButtonRect().contains(event.x, event.y)) {
                    settingsOpen = true
                    invalidate()
                    return true
                }
                if (radicalToggleRect().contains(event.x, event.y)) {
                    radicalDisplay = !radicalDisplay
                    exactResult?.let { result = formatResult(it) }
                    invalidate()
                    return true
                }
                val shouldHandle = valid && row == pressedRow && col == pressedCol
                pressedRow = -1; pressedCol = -1
                if (shouldHandle) handle(keys[row][col])
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressedRow = -1; pressedCol = -1; invalidate(); return true }
        }
        return true
    }
    private fun handle(label: String) {
        if (error.isNotEmpty()) { error = ""; expression = ""; result = "0" }
        val startsNewExpression = label in listOf("sin", "cos", "tan", "√", "π", "ln", "log", "1/x", "n!", "(") || label.firstOrNull()?.isDigit() == true || label == "."
        if (justEvaluated && startsNewExpression) { expression = ""; result = "0"; exactResult = null }
        if (label != "角度" && label != "弧度" && label != "2nd") justEvaluated = false
        when (label) {
            "清空", "C", "AC" -> { expression = ""; result = "0"; error = ""; exactResult = null; justEvaluated = false }
            "⌫" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
            "角度" -> mode = AngleMode.DEG
            "弧度" -> mode = AngleMode.RAD
            "度" -> expression += "°"
            "分" -> expression += "′"
            "秒" -> expression += "″"
            "2nd" -> inverse = !inverse
            "=" -> evaluate()
            "±" -> expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"
            "x²" -> expression += "^2"
            "xʸ" -> expression += "^"
            "1/x" -> expression = "1/($expression)"
            "n!" -> expression += "!"
            "√" -> expression += "sqrt("
            "π" -> { mode = AngleMode.RAD; expression += "π" }
            "sin", "cos", "tan" -> expression += "${if (inverse) "a" else ""}$label("
            "ln", "log" -> expression += "$label("
            "×" -> expression += "*"
            "÷" -> expression += "/"
            "−" -> expression += "-"
            else -> expression += label
        }
        preview(); invalidate()
    }
    private fun preview() {
        if (expression.isBlank()) { result = "0"; exactResult = null; return }
        try { exactResult = Evaluator.evaluate(expression, mode); result = formatResult(exactResult!!) } catch (_: Exception) { }
    }
    private fun formatResult(value: Exact) = if (radicalDisplay) ResultFormatter.format(value) else ResultFormatter.formatDecimal(value)
    private fun evaluate() {
        if (expression.isBlank()) return
        try { exactResult = Evaluator.evaluate(expression, mode); result = formatResult(exactResult!!); expression = result; justEvaluated = true }
        catch (e: CalcError) { error = e.message ?: "表达式有误" }
        catch (_: Exception) { error = "表达式有误" }
        invalidate()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = when (keyCode) { KeyEvent.KEYCODE_ENTER -> "="; KeyEvent.KEYCODE_DEL -> "⌫"; KeyEvent.KEYCODE_ESCAPE -> "C"; KeyEvent.KEYCODE_NUMPAD_ADD -> "+"; KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "−"; KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "×"; KeyEvent.KEYCODE_NUMPAD_DIVIDE -> "÷"; else -> event.unicodeChar.toChar().toString() }
        if (s.isNotEmpty() && (s[0].isDigit() || s in listOf("+", "-", "*", "/", "(", ")", ".", "=", "⌫", "C"))) { handle(s); return true }
        return super.onKeyDown(keyCode, event)
    }
}
