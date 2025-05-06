package net.echonolix.caelum

internal fun Short.toBoolean(): Boolean = this != 0.toShort()
internal fun Int.toBoolean(): Boolean = this != 0
internal fun Long.toBoolean(): Boolean = this != 0L

internal fun Boolean.toShort(): Short = if (this) 1.toShort() else 0.toShort()
internal fun Boolean.toInt(): Int = if (this) 1 else 0
internal fun Boolean.toLong(): Long = if (this) 1L else 0L