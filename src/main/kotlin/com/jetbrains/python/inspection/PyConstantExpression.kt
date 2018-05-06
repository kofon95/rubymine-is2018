package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PyTokenTypes.*
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*


class PyConstantExpression : PyInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor {
        return Visitor(holder, session)
    }

    class Visitor(holder: ProblemsHolder?, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

        override fun visitPyIfStatement(node: PyIfStatement) {
            super.visitPyIfStatement(node)
            processIfPart(node.ifPart)
            for (part in node.elifParts) {
                processIfPart(part)
            }
        }

        private fun processIfPart(pyIfPart: PyIfPart) {
            val condition = pyIfPart.condition
            val result = ensureResult(condition) ?: return
            registerProblem(condition, "The condition is always $result")
        }

        // return null - unknown
        private fun ensureResult(expr: PyExpression?): Boolean? {
            if (expr is PyBoolLiteralExpression) {
                return expr.value
            }

            // Drop parenthesis
            if (expr is PyParenthesizedExpression)
                return ensureResult(expr.containedExpression)

            // Invert expression under "not" operator
            if (expr is PyPrefixExpression) {
                // assuming operator is always "not"
                val res = ensureResult(expr.operand) ?: return null
                return !res
            }

            if (expr is PyBinaryExpression) {
                val op = expr.operator
                if (op == GT || op == GE ||
                        op == LT || op == LE ||
                        op == EQ) {
                    if (expr.leftExpression is PyNumericLiteralExpression &&
                            expr.rightExpression is PyNumericLiteralExpression) {
                        val left = expr.leftExpression as PyNumericLiteralExpression
                        val right = expr.rightExpression as PyNumericLiteralExpression
                        val cmp = left.bigIntegerValue!!.compareTo(right.bigIntegerValue)
                        return when (op) {
                            GT -> cmp > 0
                            GE -> cmp >= 0
                            LT -> cmp < 0
                            LE -> cmp <= 0
                            EQ -> cmp == 0
                            else -> null
                        }
                    }
                } else {
                    val left = ensureResult(expr.leftExpression) ?: return null
                    val right = ensureResult(expr.rightExpression) ?: return null
                    return when (op) {
                        AND_KEYWORD -> left and right
                        OR_KEYWORD -> left or right
                        else -> null
                    }
                }
            }
            return null
        }

        // dfs
        private fun printPyExpression(expr: PyExpression?, offset: Int) {
            print("".padEnd(offset, '.'))
            println("Start a new expression [$expr]")

            if (expr is PyBinaryExpression){
                val left = expr.leftExpression
                val right = expr.rightExpression
                val op = expr.operator

                printPyExpression(left, offset+2)
                print("".padEnd(offset, '.'))
                println(op)
                printPyExpression(right, offset+2)
            } else if (expr is PyPrefixExpression) {
                print("".padEnd(offset, '.'))
                println(expr.operator)
                printPyExpression(expr.operand, offset+2)
            } else if (expr is PyParenthesizedExpression) {
                print("".padEnd(offset, '.'))
                println("Parenthesize")
                printPyExpression(expr.containedExpression, offset+2)
            } else {
                print("".padEnd(offset, '.'))
                println(expr)
            }
        }
    }
}
