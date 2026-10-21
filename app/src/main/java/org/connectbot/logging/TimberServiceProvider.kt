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

package org.connectbot.logging

import android.util.Log
import org.connectbot.BuildConfig
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** Routes library logs (including cbssh) through the same trees as application logs. */
class TimberServiceProvider : SLF4JServiceProvider {
    private val loggers = ConcurrentHashMap<String, TimberLogger>()
    private val factory = ILoggerFactory { name -> loggers.computeIfAbsent(name, ::TimberLogger) }
    private val markers = BasicMarkerFactory()
    private val mdc = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = factory
    override fun getMarkerFactory(): IMarkerFactory = markers
    override fun getMDCAdapter(): MDCAdapter = mdc
    override fun getRequestedApiVersion(): String = "2.0.99"
    override fun initialize() = Unit
}

private class TimberLogger(loggerName: String) : LegacyAbstractLogger() {
    init {
        name = loggerName
    }

    override fun isTraceEnabled(): Boolean = BuildConfig.DEBUG
    override fun isDebugEnabled(): Boolean = BuildConfig.DEBUG
    override fun isInfoEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true
    override fun getFullyQualifiedCallerName(): String = TimberLogger::class.java.name

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?,
    ) {
        val priority = when (level) {
            Level.TRACE -> Log.VERBOSE
            Level.DEBUG -> Log.DEBUG
            Level.INFO -> Log.INFO
            Level.WARN -> Log.WARN
            Level.ERROR -> Log.ERROR
        }
        val message = MessageFormatter.arrayFormat(messagePattern, arguments, throwable).message
        Timber.tag(name).log(priority, throwable, message)
    }
}
