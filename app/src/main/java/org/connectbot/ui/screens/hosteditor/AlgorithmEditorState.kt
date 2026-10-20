/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.screens.hosteditor

import org.connectbot.sshlib.SshCryptoPolicy

internal enum class AlgorithmEditMode { DEFAULT, DELTA, EXACT }

/** Text and picker share this representation; syntax validation remains owned by cbssh. */
internal data class AlgorithmRule(val operation: Char, val name: String)

internal fun algorithmRules(expression: String): List<AlgorithmRule> {
    SshCryptoPolicy.validateExpression(expression)
    return expression.takeIf { it.isNotBlank() }?.split(',')?.map {
        val token = it.trim()
        val operation = token.first().takeIf { char -> char in "^+-" } ?: '='
        AlgorithmRule(operation, if (operation == '=') token else token.drop(1))
    }.orEmpty()
}

internal fun List<AlgorithmRule>.expression(): String = joinToString(",") { (operation, name) ->
    if (operation == '=') name else "$operation$name"
}

internal fun setAlgorithmRule(expression: String, name: String, operation: Char?): String = (algorithmRules(expression).filterNot { it.name == name } + listOfNotNull(operation?.let { AlgorithmRule(it, name) })).expression()

internal fun moveAlgorithmRule(expression: String, name: String, target: String): String {
    val rules = algorithmRules(expression).toMutableList()
    val from = rules.indexOfFirst { it.name == name }
    val to = rules.indexOfFirst { it.name == target }
    if (from < 0 || to < 0 || rules[from].operation != rules[to].operation) return expression
    rules.add(to, rules.removeAt(from))
    return rules.expression()
}

/** Preserve unavailable positive entries when converting a delta to an exact list. */
internal fun intendedAlgorithmOrder(defaults: List<String>, rules: List<AlgorithmRule>): List<String> = when {
    rules.isEmpty() -> defaults

    rules.first().operation == '=' -> rules.map { it.name }

    else -> rules.filter { it.operation == '^' }.map { it.name } +
        defaults.filter { name -> rules.none { it.name == name } } +
        rules.filter { it.operation == '+' }.map { it.name }
}

/** The picker groups preferred, inherited, appended, and excluded entries in that order. */
internal fun algorithmDisplayOrder(catalog: List<String>, rules: List<AlgorithmRule>): List<String> = (
    rules.filter { it.operation == '^' || it.operation == '=' }.map { it.name } +
        catalog.filter { name -> rules.none { it.name == name } } +
        rules.filter { it.operation == '+' }.map { it.name } +
        rules.filter { it.operation == '-' }.map { it.name }
    ).distinct()
