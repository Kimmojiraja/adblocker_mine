package com.adshield.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.adshield.app.R

/**
 * Functional Decoy Calculator Dialog.
 * Disguises the browser behind a real math calculator.
 * Entering the secret PIN (e.g. 1337) followed by "=" immediately unlocks the browser.
 */
class DecoyCalculatorDialog(
    context: Context,
    private val unlockPin: String = "1337",
    private val onUnlockSuccess: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private var currentInput: String = "0"
    private var previousValue: Double = 0.0
    private var pendingOp: String? = null
    private var isNewInput: Boolean = true
    private var enteredKeySequence: StringBuilder = StringBuilder()

    private lateinit var textDisplay: TextView
    private lateinit var textExpression: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_decoy_calculator)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        textDisplay = findViewById(R.id.textCalcDisplay)
        textExpression = findViewById(R.id.textCalcExpression)

        setupButtons()
    }

    private fun setupButtons() {
        val digitButtons = mapOf(
            R.id.btnCalc0 to "0", R.id.btnCalc1 to "1", R.id.btnCalc2 to "2",
            R.id.btnCalc3 to "3", R.id.btnCalc4 to "4", R.id.btnCalc5 to "5",
            R.id.btnCalc6 to "6", R.id.btnCalc7 to "7", R.id.btnCalc8 to "8",
            R.id.btnCalc9 to "9"
        )

        for ((id, digit) in digitButtons) {
            findViewById<Button>(id)?.setOnClickListener {
                enteredKeySequence.append(digit)
                if (isNewInput || currentInput == "0") {
                    currentInput = digit
                    isNewInput = false
                } else {
                    currentInput += digit
                }
                textDisplay.text = currentInput
            }
        }

        findViewById<Button>(R.id.btnCalcDot)?.setOnClickListener {
            enteredKeySequence.append(".")
            if (!currentInput.contains(".")) {
                currentInput += "."
                textDisplay.text = currentInput
                isNewInput = false
            }
        }

        findViewById<Button>(R.id.btnCalcAC)?.setOnClickListener {
            currentInput = "0"
            previousValue = 0.0
            pendingOp = null
            isNewInput = true
            enteredKeySequence.clear()
            textDisplay.text = "0"
            textExpression.text = ""
        }

        findViewById<Button>(R.id.btnCalcSign)?.setOnClickListener {
            val d = currentInput.toDoubleOrNull() ?: 0.0
            currentInput = formatResult(-d)
            textDisplay.text = currentInput
        }

        findViewById<Button>(R.id.btnCalcPercent)?.setOnClickListener {
            val d = (currentInput.toDoubleOrNull() ?: 0.0) / 100.0
            currentInput = formatResult(d)
            textDisplay.text = currentInput
        }

        val opButtons = mapOf(
            R.id.btnCalcAdd to "+",
            R.id.btnCalcSub to "-",
            R.id.btnCalcMul to "×",
            R.id.btnCalcDiv to "÷"
        )

        for ((id, op) in opButtons) {
            findViewById<Button>(id)?.setOnClickListener {
                enteredKeySequence.append(op)
                val d = currentInput.toDoubleOrNull() ?: 0.0
                if (pendingOp != null && !isNewInput) {
                    previousValue = compute(previousValue, d, pendingOp!!)
                } else {
                    previousValue = d
                }
                pendingOp = op
                isNewInput = true
                textExpression.text = "${formatResult(previousValue)} $op"
                textDisplay.text = formatResult(previousValue)
            }
        }

        findViewById<Button>(R.id.btnCalcEquals)?.setOnClickListener {
            val rawSequence = enteredKeySequence.toString()
            // Check secret decoy PIN trigger
            if (rawSequence == unlockPin || currentInput == unlockPin) {
                dismiss()
                onUnlockSuccess()
                return@setOnClickListener
            }

            val d = currentInput.toDoubleOrNull() ?: 0.0
            if (pendingOp != null) {
                val res = compute(previousValue, d, pendingOp!!)
                textExpression.text = "${formatResult(previousValue)} $pendingOp ${formatResult(d)} ="
                currentInput = formatResult(res)
                textDisplay.text = currentInput
                pendingOp = null
                isNewInput = true
            }
            enteredKeySequence.clear()
        }
    }

    private fun compute(a: Double, b: Double, op: String): Double {
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else 0.0
            else -> b
        }
    }

    private fun formatResult(v: Double): String {
        return if (v == v.toLong().toDouble()) {
            v.toLong().toString()
        } else {
            v.toString()
        }
    }
}
